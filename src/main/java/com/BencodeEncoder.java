package com;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BencodeEncoder {

    // Encodes a Java value into bencoded bytes.
    public byte[] encode(Object o){
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            encodeObject(o, buffer);
        } catch (IOException e) {
            throw new RuntimeException("Encoding failed", e);
        }
        return buffer.toByteArray();
    }

    private void encodeObject(Object o, ByteArrayOutputStream buffer) throws IOException {
        if (o instanceof Integer || o instanceof Long) {
            encodeInteger((Number) o, buffer);
        } else if (o instanceof byte[]) {
            encodeBytes((byte[]) o, buffer);
        } else if (o instanceof String) {
            encodeBytes(((String) o).getBytes(StandardCharsets.UTF_8), buffer);
        } else if (o instanceof List) {
            encodeList((List<?>) o, buffer);
        } else if (o instanceof Map) {
            encodeDictionary((Map<?, ?>) o, buffer);
        } else {
            throw new IllegalArgumentException("Unsupported type: " + o.getClass()); 
        }
    }

    private void encodeInteger(Number val, ByteArrayOutputStream buffer) throws IOException {
        buffer.write('i');
        buffer.write(String.valueOf(val).getBytes(StandardCharsets.UTF_8));
        buffer.write('e');
    }

    private void encodeBytes(byte[] val, ByteArrayOutputStream buffer) throws IOException {
        buffer.write(String.valueOf(val.length).getBytes(StandardCharsets.UTF_8));
        buffer.write(':');
        buffer.write(val);
    }

    private void encodeList(List<?> list, ByteArrayOutputStream buffer) throws IOException {
        buffer.write('l');
        for (Object item : list) {
            encodeObject(item, buffer);
        }
        buffer.write('e');
    }

    // Dictionary keys must stay sorted for valid bencoding.
    private void encodeDictionary(Map<?, ?> map, ByteArrayOutputStream buffer) throws IOException {
        buffer.write('d');
        Map<String, Object> sortedMap = new TreeMap<>((Map<String, Object>) map);
        
        for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
            encodeObject(entry.getKey(), buffer);
            encodeObject(entry.getValue(), buffer);
        }
        buffer.write('e');
    }
    
}
