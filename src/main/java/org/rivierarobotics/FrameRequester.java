package org.rivierarobotics;

import java.util.Optional;
import java.util.function.Consumer;

import org.rivierarobotics.protos.Packet.Frame;
import org.rivierarobotics.protos.Packet.SetFrameType;

public class FrameRequester {

    private final NetworkManager network;
    private volatile Consumer<Frame> frameCallback;
    private volatile Source source = Source.PLAIN;
    private volatile boolean sendSource = false;

    {
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    frameRequestLoop();
                    Thread.sleep(10);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setName("FrameRequest Loop");
        thread.setDaemon(true);
        thread.start();
    }

    public FrameRequester(NetworkManager network) {
        this.network = network;
    }

    public void setSource(Source source) {
        this.source = source;
        sendSource = true;
    }

    public Source getSource() {
        return this.source;
    }

    public void setFrameCallback(Consumer<Frame> frameCallback) {
        this.frameCallback = frameCallback;
    }

    public void frameRequestLoop() {
        if (sendSource) {
            network.sendMessage(SetFrameType.newBuilder()
                    .setType(SetFrameType.Type.valueOf(source.name())).build());
        }
        if (frameCallback == null) {
            // do nothing
            return;
        }
        Optional<Frame> frame = network.nextMessageOfType(Frame.class);
        if (frame.isPresent()) {
            frameCallback.accept(frame.get());
        }
    }

}
