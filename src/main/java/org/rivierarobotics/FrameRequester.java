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