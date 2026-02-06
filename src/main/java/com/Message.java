package com;

import java.nio.ByteBuffer;

public class Message {
    // To communicate with the peer , after the handshake to recive the packets 
    public final int id;
    public final byte[] payload;

    public Message(int id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }


    // Source : https://wiki.theory.org/BitTorrentSpecification#Messages
    // Author : Charan Sai
    // Date : 31/01/2026
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
        // Length (4 bytes) + ID (1 byte) = 5 bytes total
        // But length field itself is 1 (just the ID byte)
        byte[] msg = new byte[5];
        
        // Length = 1 (0x00 0x00 0x00 0x01)
        msg[3] = 1;
        
        // ID
        msg[4] = (byte) id;
        
        return msg;
    }

    // SOurce : Stackover Flow
    // Author : Online 
    // Date : 06/02/2026
    public static byte[] buildRequest(int index, int begin, int length) {
        // Total message length = 13 bytes
        // 1 byte ID + 4 bytes Index + 4 bytes Begin + 4 bytes Length
        
        ByteBuffer buffer = ByteBuffer.allocate(17); // 4 bytes for Length prefix + 13 bytes payload
        
        buffer.putInt(13);      // Length Prefix
        buffer.put((byte) 6);   // ID for REQUEST ( ID : 6 is to ask for a block )
        buffer.putInt(index);   // Piece Index
        buffer.putInt(begin);   // Offset
        buffer.putInt(length);  // Block Size
        
        return buffer.array();
    }

    

}
