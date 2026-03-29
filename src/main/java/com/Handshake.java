package com;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Handshake {
    private static final String PROTOCOL_STRING = "BitTorrent protocol";
    private static final byte PROTOCOL_LEN = 19;

    // Builds a standard 68-byte BitTorrent handshake.
    public static byte[] buildHandshake(byte[] infoHash, byte[] peerId) {
        byte[] handshake = new byte[68];
        int index = 0;

        handshake[index++] = PROTOCOL_LEN;

        byte[] protoBytes = PROTOCOL_STRING.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(protoBytes, 0, handshake, index, protoBytes.length);
        index += protoBytes.length;

        index += 8;

        System.arraycopy(infoHash, 0, handshake, index, 20);
        index += 20;

        System.arraycopy(peerId, 0, handshake, index, 20);

        return handshake;
    }

    // Verifies handshake shape and info-hash match.
    public static boolean verify(byte[] response, byte[] expectedInfoHash) {
        if (response.length < 68) return false;

        if (response[0] != PROTOCOL_LEN) return false;

        byte[] receivedHash = Arrays.copyOfRange(response, 28, 48);
        return Arrays.equals(receivedHash, expectedInfoHash);
    }
}