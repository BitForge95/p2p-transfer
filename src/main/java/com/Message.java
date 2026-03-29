package com;

import java.nio.ByteBuffer;

public class Message {
    public final int id;
    public final byte[] payload;

    public Message(int id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    @Override
    public String toString() {
        String name;
        switch (id) {
            case 0: name = "CHOKE"; break;
            case 1: name = "UNCHOKE"; break;
            case 2: name = "INTERESTED"; break;
            case 3: name = "NOT_INTERESTED"; break;
            case 4: name = "HAVE"; break;
            case 5: name = "BITFIELD"; break;
            case 6: name = "REQUEST"; break;
            case 7: name = "PIECE"; break;
            default: name = "UNKNOWN (" + id + ")"; break;
        }
        return "Message: " + name + " [" + (payload.length + 1) + " bytes]";
    }

    public static byte[] build(int id) {
        byte[] msg = new byte[5];

        msg[3] = 1;

        msg[4] = (byte) id;

        return msg;
    }

    // Builds a REQUEST message for one block of a piece.
    public static byte[] buildRequest(int index, int begin, int length) {
        ByteBuffer buffer = ByteBuffer.allocate(17);

        buffer.putInt(13);
        buffer.put((byte) 6);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);

        return buffer.array();
    }
}
