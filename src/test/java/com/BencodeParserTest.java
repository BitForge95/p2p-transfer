package com;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.BencodeParser;

public class BencodeParserTest {
    @Test
    void testString() {
        BencodeParser parser = new BencodeParser("4:spam".getBytes());
        byte[] result = (byte[]) parser.decode();
        assertEquals("spam", new String(result));
            
        parser = new BencodeParser("0:".getBytes());
        result = (byte[]) parser.decode();
        assertEquals("", new String(result));
    }
}
