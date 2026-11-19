#ifndef __IAP_FLASH_H__
#define __IAP_FLASH_H__

#include <stdint.h>
#include "ch552.h"

#define USER_FLASH_START    0x0000
#define USER_FLASH_END      0x2BFF   // 11 KB User Flash partition
#define BOOTLOADER_START    0x2C00   // Bootloader begins at 0x2C00 (3 KB reserved)

#ifndef ROM_CMD_WRITE
#define ROM_CMD_WRITE       0x9A     // Flash-ROM word write command
#endif

// Writes a 16-bit word to Code Flash at even address
uint8_t IAP_WriteWord(uint16_t addr, uint16_t data_word);

// Reads a 16-bit word from Code Flash
uint16_t IAP_ReadWord(uint16_t addr);

// Computes 16-bit CRC over user flash region
uint16_t IAP_CalculateCRC16(uint16_t start_addr, uint16_t length);

// Erases a 1KB page of Code Flash by writing 0xFFFF (addr must be below BOOTLOADER_START)
uint8_t IAP_ErasePage(uint16_t addr);

#endif // __IAP_FLASH_H__
