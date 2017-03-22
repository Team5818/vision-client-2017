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
package org.rivierarobotics.packet;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.rivierarobotics.protos.Packet.SimplePacket;

import com.google.common.io.ByteStreams;
import com.google.protobuf.Any;
import com.google.protobuf.Message;

public final class Packets {

    public static Any readPacket(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        int len = in.readInt();
        return SimplePacket.parseFrom(ByteStreams.limit(stream, len))
                .getMessage();
    }

    public static void writePacket(OutputStream stream, Message packet)
            throws IOException {
        DataOutputStream out = new DataOutputStream(stream);
        SimplePacket pack =
                SimplePacket.newBuilder().setMessage(Any.pack(packet)).build();

        int len = pack.getSerializedSize();
        out.writeInt(len);
        pack.writeTo(stream);
    }

    private Packets() {
    }

}
