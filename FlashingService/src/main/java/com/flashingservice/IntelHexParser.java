package com.flashingservice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

/**
 * Parses Intel HEX files into raw binary firmware buffers for CH552 microcontrollers.
 */
public class IntelHexParser {

    public static final int DEFAULT_MAX_FLASH = 11264; // 11 KB user flash (0x0000 - 0x2BFF)

    public static class ParsedHex {
        private final byte[] data;
        private final int startAddress;
        private final int endAddress;
        private final int crc16;

        public ParsedHex(byte[] data, int startAddress, int endAddress, int crc16) {
            this.data = data;
            this.startAddress = startAddress;
            this.endAddress = endAddress;
            this.crc16 = crc16;
        }

        public byte[] getData() {
            return data;
        }

        public int getStartAddress() {
            return startAddress;
        }

        public int getEndAddress() {
            return endAddress;
        }

        public int getCrc16() {
            return crc16;
        }
    }

    public static ParsedHex parse(String hexText) throws IOException {
        return parse(hexText, DEFAULT_MAX_FLASH);
    }

    public static ParsedHex parse(String hexText, int maxFlashSize) throws IOException {
        byte[] memory = new byte[maxFlashSize];
        Arrays.fill(memory, (byte) 0xFF);

        int minAddress = maxFlashSize;
        int maxAddress = 0;
        int extendedAddress = 0;

        BufferedReader reader = new BufferedReader(new StringReader(hexText));
        String line;

        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(":") || trimmed.length() < 11) {
                continue;
            }

            // Verify Intel HEX line checksum
            int sum = 0;
            for (int i = 1; i < trimmed.length() - 1; i += 2) {
                sum += Integer.parseInt(trimmed.substring(i, i + 2), 16);
            }
            if ((sum & 0xFF) != 0) {
                throw new IOException("Corrupted Intel HEX line checksum mismatch: " + trimmed);
            }

            int byteCount = Integer.parseInt(trimmed.substring(1, 3), 16);
            int address = Integer.parseInt(trimmed.substring(3, 7), 16) + extendedAddress;
            int recordType = Integer.parseInt(trimmed.substring(7, 9), 16);

            if (recordType == 0x01) {
                // End of File Record
                break;
            } else if (recordType == 0x02) {
                // Extended Segment Address
                extendedAddress = Integer.parseInt(trimmed.substring(9, 13), 16) << 4;
            } else if (recordType == 0x04) {
                // Extended Linear Address
                extendedAddress = Integer.parseInt(trimmed.substring(9, 13), 16) << 16;
            } else if (recordType == 0x00) {
                // Data Record
                for (int i = 0; i < byteCount; i++) {
                    int targetAddr = address + i;
                    if (targetAddr >= maxFlashSize) {
                        throw new IllegalArgumentException("Firmware address 0x" + Integer.toHexString(targetAddr) +
                                " exceeds user flash limit (max 0x" + Integer.toHexString(maxFlashSize) + ")");
                    }
                    byte byteVal = (byte) Integer.parseInt(trimmed.substring(9 + i * 2, 11 + i * 2), 16);
                    memory[targetAddr] = byteVal;

                    if (targetAddr < minAddress) minAddress = targetAddr;
                    if (targetAddr > maxAddress) maxAddress = targetAddr;
                }
            }
        }

        if (maxAddress < minAddress) {
            minAddress = 0;
            maxAddress = 0;
        }

        int length = maxAddress + 1;
        if (length % 2 != 0) {
            length++; // 16-bit word alignment
        }

        // Calculate CRC-16-CCITT over [0 .. length]
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            int byteVal = memory[i] & 0xFF;
            crc ^= (byteVal << 8);
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) ^ 0x1021) & 0xFFFF;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }

        byte[] result = new byte[length];
        System.arraycopy(memory, 0, result, 0, length);

        return new ParsedHex(result, minAddress, length, crc);
    }
}
