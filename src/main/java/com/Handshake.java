package com;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Handshake {
    private static final String PROTOCOL_STRING = "BitTorrent protocol";
    private static final byte PROTOCOL_LEN = 19;

    // Builds the 68-byte handshake packet
    public static byte[] buildHandshake(byte[] infoHash, byte[] peerId) {
        byte[] handshake = new byte[68];
        int index = 0;

        // 1. Length
        handshake[index++] = PROTOCOL_LEN;

        // 2. Protocol String
        byte[] protoBytes = PROTOCOL_STRING.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(protoBytes, 0, handshake, index, protoBytes.length);
        index += protoBytes.length;

        // 3. Reserved bytes (8 zeros)
        // (Array is initialized to 0 by default, so we just skip 8 bytes)
        index += 8;

        // 4. Info Hash
        System.arraycopy(infoHash, 0, handshake, index, 20);
        index += 20;

        // 5. Peer ID
        System.arraycopy(peerId, 0, handshake, index, 20);

        return handshake;
    }

    // Verifies the response from the peer
    public static boolean verify(byte[] response, byte[] expectedInfoHash) {
        if (response.length < 68) return false;

        // Check 1: Protocol Length
        if (response[0] != PROTOCOL_LEN) return false;

        // Check 2: Info Hash (Bytes 28 to 47)
        // It must match the hash we sent!
        byte[] receivedHash = Arrays.copyOfRange(response, 28, 48);
        return Arrays.equals(receivedHash, expectedInfoHash);
    }
}