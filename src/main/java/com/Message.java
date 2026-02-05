package com;

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

}
