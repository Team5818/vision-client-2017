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
