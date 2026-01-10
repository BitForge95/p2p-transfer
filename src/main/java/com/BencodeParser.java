package com;

public class BencodeParser {
    // Placeholder for now

    private final byte[] data;
    private int index;

    public BencodeParser(byte[] data) {
        this.data = data;
        this.index = 0;
    }

    // Main Function
    public Object decode() {
        if (index >= data.length) {
            return null;
        }

        char c = (char) data[index];

        if (Character.isDigit(c)) {
            return decodeString(); 
        } else if (c == 'i') {
            return decodeInteger();
        }

        throw new RuntimeException("Invalid Bencode format at index " + index);
    }

    private Long decodeInteger() {
        index++;

        int start = index;
        while (data[index] != 'e') {
            index++;
            if (index >= data.length) {
                throw new RuntimeException("Invalid Integer: ends without 'e'");
            }
        }

        String numberStr = new String(data, start, index - start);
        index++;

        return Long.parseLong(numberStr);
    }

    private byte[] decodeString() {
        throw new UnsupportedOperationException("Strings not supported yet!");
    }
}