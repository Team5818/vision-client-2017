package org.rivierarobotics;

import java.io.InputStream;

import org.rivierarobotics.protos.Packet.Frame;

public class FrameDecoder {

    /**
     * Converts a {@link Frame} to an InputStream with JPEG data.
     * 
     * @return the JPEG data stream
     */
    public static InputStream getJpegStreamFromFrame(Frame frame) {
        return frame.getJpeg().newInput();
    }
}
