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

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import org.rivierarobotics.protos.Packet.Frame;

public class FrameRecorder {

    private static final File REC_PATH = new File("vc2017-recorded");
    static {
        REC_PATH.mkdir();
    }

    private final FrameRequester requester;
    private final Lock changeLock = new ReentrantLock();
    private final Deque<Frame> pendingFrames = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean closeRequested = new AtomicBoolean();
    private volatile SequenceEncoder enc;

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
            try {
                enc = new SequenceEncoder(
                        new File(REC_PATH, LocalDateTime.now() + ".mp4"));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        } finally {
            changeLock.unlock();
        }
    }

    public void stopRecording() {
        changeLock.lock();
        try {
            closeRequested.set(true);
        } finally {
            changeLock.unlock();
        }
    }

    public boolean isRecording() {
        // not recording if close requested
        return !closeRequested.get() && enc != null;
    }

    private void addFrame(Frame frame) {
        if (!isRecording()) {
            return;
        }
        pendingFrames.addLast(frame);
    }

    private void encodingLoop() throws Exception {
        changeLock.lock();
        try {
            if (enc != null && !pendingFrames.isEmpty()) {
                enc.encodeNativeFrame(decodeFrame(pendingFrames.removeFirst()));
            }
            if (pendingFrames.isEmpty() && closeRequested.get()) {
                try {
                    enc.finish();
                } finally {
                    enc = null;
                    closeRequested.set(false);
                }
            }
        } finally {
            changeLock.unlock();
        }
    }

    private Picture decodeFrame(Frame frame) {
        BufferedImage img;
        try (InputStream is = FrameDecoder.getJpegStreamFromFrame(frame)) {
            img = ImageIO.read(is);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return AWTUtil.fromBufferedImage(img);
    }

}
