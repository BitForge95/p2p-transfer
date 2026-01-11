package com;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import com.BencodeParser;

public class BencodeParserTest {
    @Test
    void testDictionary() {
        // d3:bar4:spame -> {"bar": "spam"}
        BencodeParser parser = new BencodeParser("d3:bar4:spame".getBytes());
        Map<String, Object> map = (Map<String, Object>) parser.decode();
        
        assertEquals("spam", new String((byte[]) map.get("bar")));
    }
}
