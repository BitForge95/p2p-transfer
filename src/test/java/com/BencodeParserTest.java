package com;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.BencodeParser;

class BencodeParserTest {

    @Test
    void testInteger() {
        BencodeParser parser = new BencodeParser("i42e".getBytes());
        assertEquals(42L, parser.decode());

        parser = new BencodeParser("i-500e".getBytes());
        assertEquals(-500L, parser.decode());
    }
}
