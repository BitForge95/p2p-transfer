package com;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.BencodeParser;

public class BencodeParserTest {
    @Test
    void testList() {
        // l4:spami42ee -> ["spam", 42]
        BencodeParser parser = new BencodeParser("l4:spami42ee".getBytes());
        List<Object> list = (List<Object>) parser.decode();
        
        assertEquals(2, list.size());
        assertEquals("spam", new String((byte[]) list.get(0)));
        assertEquals(42L, list.get(1));
    }
}
