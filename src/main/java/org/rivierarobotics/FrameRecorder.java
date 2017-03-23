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
