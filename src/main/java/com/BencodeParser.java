package com;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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
        index++; //I skipped the first letter 'l'

        List<Object> list = new ArrayList<>();

        while(data[index] == 'e') {
            list.add(decode()); //REcurrsivly called decode function to decode either Intergers or Strings for now
        }

        index++; // Skipping the 'e' also

        return list;

    }

    private Map<String, Object> decodeDictionary() {
        //The dictionary in bencoding is in the format d<key><value><key><value>e
        //Here our task is to store them and use for further tracking
        index++; //Skipping the letter 'd'

        Map<String, Object> map = new TreeMap<>(); //Using treemap() cause it sorts the keys
        //Bencode dictionaries are usually lexicographically sorted

        while(data[index] != 'e') {
            String key = new String(decodeString());

            Object value = decode();

            map.put(key,value);
        }

        index++; 

        return map;

    }
}