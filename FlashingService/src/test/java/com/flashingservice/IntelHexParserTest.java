package com.flashingservice;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

public class IntelHexParserTest {

    @Test
    public void testParseValidHex() throws IOException {
        String hex = ":04000000021234565E\n:00000001FF\n";
        IntelHexParser.ParsedHex parsed = IntelHexParser.parse(hex, 11264);

        assertNotNull(parsed);
        assertEquals(4, parsed.getEndAddress());
        byte[] data = parsed.getData();
        assertEquals((byte) 0x02, data[0]);
        assertEquals((byte) 0x12, data[1]);
        assertEquals((byte) 0x34, data[2]);
        assertEquals((byte) 0x56, data[3]);
    }

    @Test(expected = IOException.class)
    public void testCorruptChecksumThrows() throws IOException {
        String corruptHex = ":040000000212345699\n:00000001FF\n";
        IntelHexParser.parse(corruptHex, 11264);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOutOfBoundsThrows() throws IOException {
        String oobHex = ":02300000123488\n:00000001FF\n";
        IntelHexParser.parse(oobHex, 11264);
    }

    @Test
    public void testCrcCalculation() throws IOException {
        String hex = ":02000000AABB99\n:00000001FF\n";
        IntelHexParser.ParsedHex parsed = IntelHexParser.parse(hex, 11264);
        assertEquals(2, parsed.getEndAddress());
        assertTrue(parsed.getCrc16() != 0);
    }
}
