#include "iap_flash.h"

uint8_t IAP_WriteWord(uint16_t addr, uint16_t data_word) {
    uint8_t status;
    uint8_t ea_saved;

    // Safety bounds check: never overwrite the bootloader partition
    if (addr >= BOOTLOADER_START) {
        return 0x03; // Out of bounds error
    }

    // Must be even address
    addr &= ~0x0001;

    // Disable all interrupts during flash programming
    ea_saved = EA;
    EA = 0;

    // Enter safe mode and enable code write
    SAFE_MOD = 0x55;
    SAFE_MOD = 0xAA;
    GLOBAL_CFG |= bCODE_WE;

    // Load address and 16-bit word
    ROM_ADDR_L = (uint8_t)(addr & 0xFF);
    ROM_ADDR_H = (uint8_t)((addr >> 8) & 0xFF);
    ROM_DATA_L = (uint8_t)(data_word & 0xFF);
    ROM_DATA_H = (uint8_t)((data_word >> 8) & 0xFF);

    // Trigger flash write
    ROM_CTRL = ROM_CMD_WRITE;

    // Read result status (hardware pauses CPU during write)
    status = ROM_STATUS;

    // Re-lock safe mode and disable code write
    SAFE_MOD = 0x55;
    SAFE_MOD = 0xAA;
    GLOBAL_CFG &= ~bCODE_WE;
    SAFE_MOD = 0x00;

    // Restore interrupt state
    EA = ea_saved;

    // Success if address valid (bit 6 set) and no command error (bit 1 clear)
    if ((status & bROM_CMD_ERR) || !(status & bROM_ADDR_OK)) {
        return 0x04; // Write error
    }
    return 0x00;
}

uint16_t IAP_ReadWord(uint16_t addr) {
    uint8_t low, high;
    __code const uint8_t *ptr = (__code const uint8_t *)addr;
    low = *ptr;
    high = *(ptr + 1);
    return ((uint16_t)high << 8) | low;
}

uint16_t IAP_CalculateCRC16(uint16_t start_addr, uint16_t length) {
    uint16_t crc = 0xFFFF;
    __code const uint8_t *ptr = (__code const uint8_t *)start_addr;

    for (uint16_t i = 0; i < length; i++) {
        uint8_t byte = *ptr++;
        crc ^= ((uint16_t)byte << 8);
        for (uint8_t b = 0; b < 8; b++) {
            if (crc & 0x8000) {
                crc = (crc << 1) ^ 0x1021;
            } else {
                crc <<= 1;
            }
        }
    }
    return crc;
}

uint8_t IAP_ErasePage(uint16_t addr) {
    uint16_t i;
    uint8_t status = 0;
    uint8_t ea_saved;

    // Safety bounds check: never erase the bootloader partition
    if (addr >= BOOTLOADER_START) {
        return 0x03; // Out of bounds error
    }

    // Align address to 1KB block boundary
    addr &= ~0x03FF;

    ea_saved = EA;
    EA = 0;

    // Enter safe mode and enable code write
    SAFE_MOD = 0x55;
    SAFE_MOD = 0xAA;
    GLOBAL_CFG |= bCODE_WE;
    SAFE_MOD = 0x00;

    // Erase 1KB by programming all words to 0xFFFF
    ROM_DATA_L = 0xFF;
    ROM_DATA_H = 0xFF;

    for (i = 0; i < 1024; i += 2) {
        uint16_t target = addr + i;
        ROM_ADDR_L = (uint8_t)(target & 0xFF);
        ROM_ADDR_H = (uint8_t)((target >> 8) & 0xFF);
        ROM_CTRL = ROM_CMD_WRITE;
        status = ROM_STATUS;
        if ((status & bROM_CMD_ERR) || !(status & bROM_ADDR_OK)) {
            break;
        }
    }

    // Re-lock safe mode and disable code write
    SAFE_MOD = 0x55;
    SAFE_MOD = 0xAA;
    GLOBAL_CFG &= ~bCODE_WE;
    SAFE_MOD = 0x00;

    EA = ea_saved;

    // Success if address valid (bit 6 set) and no command error (bit 1 clear)
    if ((status & bROM_CMD_ERR) || !(status & bROM_ADDR_OK)) {
        return 0x04; // Erase error
    }
    return 0x00;
}


