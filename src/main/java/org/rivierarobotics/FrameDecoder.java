package org.rivierarobotics;

import java.io.InputStream;
import java.util.zip.InflaterInputStream;

import org.rivierarobotics.protos.Packet.Frame;

public class FrameDecoder {

    /**
     * Converts a {@link Frame} to an InputStream with JPEG data.
     * 
     * @return the JPEG data stream
     */
    public static InputStream getJpegStreamFromFrame(Frame frame) {
        return new InflaterInputStream(frame.getJpeg().newInput());
    }
}
