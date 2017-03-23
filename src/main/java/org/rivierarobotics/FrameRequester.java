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
import java.util.Set;
import java.util.function.Consumer;

import org.rivierarobotics.protos.Packet.Frame;
import org.rivierarobotics.protos.Packet.SetFrameType;

import com.google.common.collect.Sets;

public class FrameRequester {

    private final NetworkManager network;
    private final Set<Consumer<Frame>> frameCallback =
            Sets.newConcurrentHashSet();
    private volatile Source source = Source.PLAIN;
    private volatile boolean sendSource = false;

    {
        new ThreadLoop("FrameRequester", this::frameRequestLoop, 10).start();
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

    public void addFrameCallback(Consumer<Frame> frameCallback) {
        this.frameCallback.add(frameCallback);
    }

    public void frameRequestLoop() {
        if (sendSource) {
            network.sendMessage(SetFrameType.newBuilder()
                    .setType(SetFrameType.Type.valueOf(source.name())).build());
            sendSource = false;
        }
        Optional<Frame> frame = network.nextMessageOfType(Frame.class);
        if (frame.isPresent()) {
            frameCallback.forEach(c -> c.accept(frame.get()));
        }
    }

}
