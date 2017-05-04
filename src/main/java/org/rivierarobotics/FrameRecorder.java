/*
 * This file is part of vision-client-2017, licensed under the MIT License (MIT).
 *
 * Copyright (c) Team5818 <https://github.com/Team5818>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.rivierarobotics;

import static com.google.common.base.Preconditions.checkState;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.rivierarobotics.protos.Packet.Frame;
import org.slf4j.Logger;

import com.google.common.io.ByteStreams;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerPropertyBase;

public class FrameRecorder {

    private static final Logger LOGGER = LoggerUtil.callingClassLogger();

    private static final File REC_PATH = new File("vc2017-recorded");
    static {
        try {
            Files.createDirectories(REC_PATH.toPath());
        } catch (IOException e) {
            LOGGER.warn("Error creating REC_PATH", e);
        }
    }
    private static final DateTimeFormatter FILE_NAME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd+HH.mm.ss");

    private final FrameRequester requester;
    private final Lock changeLock = new ReentrantLock();
    private final Deque<Frame> pendingFrames = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private final AtomicReference<OutputStream> encodeStream =
            new AtomicReference<>(null);
    private final AtomicReference<Path> encodeFile =
            new AtomicReference<>(null);
    private final AtomicInteger frameCounter = new AtomicInteger(0);
    private final BFCProperty behindFramesCountProperty =
            new BFCProperty(this);

    private static final class BFCProperty extends ReadOnlyIntegerPropertyBase {

        private final FrameRecorder $this;

        private BFCProperty(FrameRecorder $this) {
            this.$this = $this;
        }

        @Override
        public int get() {
            return $this.pendingFrames.size();
        }

        @Override
        public String getName() {
            return "behindFramesCount";
        }

        @Override
        public Object getBean() {
            return $this;
        }

        @Override
        public void fireValueChangedEvent() {
            super.fireValueChangedEvent();
        }
    }

    {
        new ThreadLoop("FrameRecorder", this::encodingLoop, 10).start();
    }

    public FrameRecorder(FrameRequester requester) {
        this.requester = requester;
        this.requester.addFrameCallback(this::addFrame);
    }

    public void startRecording() {
        changeLock.lock();
        try {
            if (encodeFile.get() != null || closeRequested.get()) {
                return;
            }
            try {
                Path path = REC_PATH.toPath()
                        .resolve(LocalDateTime.now().format(FILE_NAME_FORMAT));
                encodeStream.set(Files.newOutputStream(path));
                encodeFile.set(path);
                frameCounter.set(0);
            } catch (Exception e) {
                LOGGER.warn("Error creating target recording directory", e);
                return;
            }
            LOGGER.info("Recorder started (file: " + encodeFile.get() + ")!");
        } finally {
            changeLock.unlock();
        }
    }

    public void stopRecording() {
        changeLock.lock();
        try {
            closeRequested.set(true);
            LOGGER.info("Stop requested");
        } finally {
            changeLock.unlock();
        }
    }

    public boolean isRecording() {
        // not recording if close requested
        return !closeRequested.get() && encodeFile.get() != null;
    }

    public ReadOnlyIntegerProperty behindFramesCountProperty() {
        return behindFramesCountProperty;
    }

    private void addFrame(Frame frame) {
        if (!isRecording()) {
            return;
        }
        pendingFrames.addLast(frame);
        behindFramesCountProperty.fireValueChangedEvent();
    }

    private void encodingLoop() throws Exception {
        changeLock.lock();
        try {
            if (encodeFile.get() != null && !pendingFrames.isEmpty()) {
                doEncode();
            }
            if (closeRequested.get()) {
                LOGGER.info("Stopping recording of " + encodeFile.get());
                LOGGER.info("Flushing frames (count: " + pendingFrames.size()
                        + ")");
                while (!pendingFrames.isEmpty()) {
                    doEncode();
                }
                LOGGER.info("Flushed all frames, zipping");
                // set stream so it closes in finally
                try (OutputStream stream = encodeStream.get()) {
                    // zipEncode();
                } catch (Exception e) {
                    LOGGER.warn("Error zip-encoding", e);
                } finally {
                    LOGGER.info("Finalized recording of " + encodeFile.get());
                    encodeStream.set(null);
                    encodeFile.set(null);
                    closeRequested.set(false);
                }
            }
        } finally {
            changeLock.unlock();
        }
    }

    private void zipEncode() throws IOException {
        Path e = encodeFile.get();
        Path zip = e.resolveSibling(e.getFileName() + ".zip");
        try (ZipOutputStream out =
                new ZipOutputStream(Files.newOutputStream(zip));
                Stream<Path> paths = Files.list(e)) {
            for (Iterator<Path> iter = paths.iterator(); iter.hasNext();) {
                Path next = iter.next();
                out.putNextEntry(new ZipEntry(next.getFileName().toString()));
                out.write(Files.readAllBytes(next));
                out.closeEntry();
            }
        }
        // if totally successful, clear dir
        Files.walkFileTree(e, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.deleteIfExists(dir);
                return super.postVisitDirectory(dir, exc);
            }
        });
    }

    private void doEncode() throws IOException {
        checkState(!pendingFrames.isEmpty(), "no frame to encode");
        try (InputStream stream = FrameDecoder
                .getJpegStreamFromFrame(pendingFrames.removeFirst())) {
            OutputStream out = encodeStream.get();
            ByteStreams.copy(stream, out);
            out.flush();
        } finally {
            behindFramesCountProperty.fireValueChangedEvent();
        }
    }

    private BufferedImage decodeFrame(Frame frame) {
        try (InputStream is = FrameDecoder.getJpegStreamFromFrame(frame)) {
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
