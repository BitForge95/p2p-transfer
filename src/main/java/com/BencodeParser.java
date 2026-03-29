package com;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class BencodeParser {
    private final byte[] data;
    private int index;

    public BencodeParser(byte[] data) {
        this.data = data;
        this.index = 0;
    }

    // Decodes the next bencoded value at the current index.
    public Object decode() {
        if (index >= data.length) {
            return null;
        }

        char c = (char) data[index];

        if (Character.isDigit(c)) {
            return decodeString(); 
        } else if (c == 'i') {
            return decodeInteger();
        } else if (c == 'l') {
            return decodeList();
        } else if (c == 'd') {
            return decodeDictionary();
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
        int start = index;
        while (data[index] != ':') {
            index++;
            if (index >= data.length) {
                throw new RuntimeException("Invalid String: ends without ':'");
            }
        }

        String lengthStr = new String(data, start, index - start);
        int length = Integer.parseInt(lengthStr);

        index++;

        if (index + length > data.length) {
            throw new RuntimeException("Invalid String: length exceeds data size");
        }

        byte[] strBytes = new byte[length];
        System.arraycopy(data, index, strBytes, 0, length);

        index += length;

        return strBytes;
    }

    private List<Object> decodeList() {
        index++;

        List<Object> list = new ArrayList<>();

        while (data[index] != 'e') {
            list.add(decode());
        }

        index++;
        return list;
    }

    private Map<String, Object> decodeDictionary() {
        index++;

        Map<String, Object> map = new TreeMap<>();

        while(data[index] != 'e') {
            String key = new String(decodeString());

            Object value = decode();

            map.put(key,value);
        }

        index++; 

        return map;

    }
}