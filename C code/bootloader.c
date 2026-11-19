#include "ch552.h"
#include "ch552_usb.h"
#include "iap_flash.h"
#include "usb_hid.h"

// Include module implementations for single translation unit compilation
// This maximizes SDCC register overlay optimization and prevents duplicate SFR symbols
#include "iap_flash.c"
#include "usb_hid.c"

#define CMD_HELLO        0x01
#define CMD_UNLOCK       0x02
#define CMD_ERASE        0x03
#define CMD_WRITE_BLOCK  0x04
#define CMD_VERIFY_CRC   0x05
#define CMD_REBOOT       0x06

#define STATUS_OK        0x00
#define STATUS_BAD_CRC   0x01
#define STATUS_LOCKED    0x02
#define STATUS_OOB       0x03
#define STATUS_WRITE_ERR 0x04
#define STATUS_UNKNOWN   0xFF

// Boot button pin: Pin 32 (P3.2 / INT0) - active LOW
#define BOOT_PIN_BIT     2

static uint8_t is_unlocked = 0;
static uint16_t erased_pages_mask = 0;
static __xdata uint8_t reply_buf[64];

static void DelayMs(uint16_t ms) {
    while (ms--) {
        for (uint16_t i = 0; i < 2400; i++) {
            __asm nop __endasm;
        }
    }
}

static void JumpToApp(void) {
    USB_Shutdown();
    DelayMs(10);
    // Jump to user application partition entry point at 0x0000
    __asm
        ljmp 0x0000
    __endasm;
}

static void ProcessPacket(void) {
    uint8_t *rx = USB_GetRxBuffer();
    uint8_t cmd = rx[0];
    uint8_t seq = rx[1];
    uint16_t addr = rx[2] | ((uint16_t)rx[3] << 8);
    uint8_t len = rx[4];

    memset(reply_buf, 0, 64);
    reply_buf[0] = cmd;
    reply_buf[1] = seq;
    reply_buf[2] = rx[2];
    reply_buf[3] = rx[3];
    reply_buf[59] = STATUS_OK;

    switch (cmd) {
        case CMD_HELLO:
            reply_buf[4] = 0x52; // Chip ID: CH552
            reply_buf[5] = 0x02; // Bootloader v2.0 (with partition erase & re-flash support)
            reply_buf[6] = (uint8_t)(USER_FLASH_END & 0xFF);
            reply_buf[7] = (uint8_t)((USER_FLASH_END >> 8) & 0xFF); // 11 KB usable
            reply_buf[59] = STATUS_OK;
            break;

        case CMD_UNLOCK:
            if (rx[5] == 0x5A && rx[6] == 0xC3 && rx[7] == 0x3C && rx[8] == 0xA5) {
                is_unlocked = 1;
                erased_pages_mask = 0; // Reset erased page mask for new session
                reply_buf[59] = STATUS_OK;
            } else {
                is_unlocked = 0;
                reply_buf[59] = STATUS_LOCKED;
            }
            break;

        case CMD_ERASE:
            if (!is_unlocked) {
                reply_buf[59] = STATUS_LOCKED;
                break;
            }
            {
                uint16_t erase_len = rx[2] | ((uint16_t)rx[3] << 8);
                uint16_t p;
                uint8_t err = 0;

                // 0 or >= BOOTLOADER_START means erase entire user partition (0x0000 - 0x2BFF)
                if (erase_len == 0 || erase_len > BOOTLOADER_START) {
                    erase_len = BOOTLOADER_START;
                } else {
                    erase_len = (erase_len + 0x03FF) & ~0x03FF;
                    if (erase_len > BOOTLOADER_START) {
                        erase_len = BOOTLOADER_START;
                    }
                }

                for (p = 0; p < erase_len; p += 0x0400) {
                    if (IAP_ErasePage(p) != 0) {
                        err = 1;
                        break;
                    }
                    erased_pages_mask |= ((uint16_t)1 << (p >> 10));
                }
                reply_buf[59] = err ? STATUS_WRITE_ERR : STATUS_OK;
            }
            break;

        case CMD_WRITE_BLOCK:
            if (!is_unlocked) {
                reply_buf[59] = STATUS_LOCKED;
                break;
            }

            if (addr + len > BOOTLOADER_START) {
                reply_buf[59] = STATUS_OOB;
                break;
            }

            // Ensure every 1KB page covering [addr, addr + len - 1] is erased before writing
            // (safety fallback if CMD_ERASE was not explicitly called)
            {
                uint8_t start_p = (uint8_t)(addr >> 10);
                uint8_t end_p   = (uint8_t)((addr + len - 1) >> 10);
                uint8_t erase_err = 0;
                uint8_t p;
                for (p = start_p; p <= end_p; p++) {
                    if (!(erased_pages_mask & ((uint16_t)1 << p))) {
                        if (IAP_ErasePage((uint16_t)p << 10) != 0) {
                            erase_err = 1;
                            break;
                        }
                        erased_pages_mask |= ((uint16_t)1 << p);
                    }
                }
                if (erase_err) {
                    reply_buf[59] = STATUS_WRITE_ERR;
                    break;
                }
            }

            // Write 16-bit words
            {
                uint8_t err = 0;
                uint8_t i;
                for (i = 0; i < len; i += 2) {
                    uint16_t word = rx[5 + i] | ((uint16_t)rx[6 + i] << 8);
                    if (IAP_WriteWord(addr + i, word) != 0) {
                        err = 1;
                        break;
                    }
                }
                reply_buf[59] = err ? STATUS_WRITE_ERR : STATUS_OK;
            }
            break;

        case CMD_VERIFY_CRC:
            {
                uint16_t expected_crc = rx[5] | ((uint16_t)rx[6] << 8);
                uint16_t calc_crc = IAP_CalculateCRC16(0x0000, addr);
                reply_buf[4] = (uint8_t)(calc_crc & 0xFF);
                reply_buf[5] = (uint8_t)((calc_crc >> 8) & 0xFF);
                reply_buf[59] = (calc_crc == expected_crc) ? STATUS_OK : STATUS_BAD_CRC;
            }
            break;

        case CMD_REBOOT:
            reply_buf[59] = STATUS_OK;
            USB_SendReport(reply_buf, 64);
            DelayMs(50);
            JumpToApp();
            return;

        default:
            reply_buf[59] = STATUS_UNKNOWN;
            break;
    }

    USB_SendReport(reply_buf, 64);
    USB_ReleaseRx();
}

void main(void) {
    // Configure Boot button pins (P3.2 and P1.5) as quasi-bidirectional inputs with pullup
    P3_MOD_OC |= (1 << BOOT_PIN_BIT); // Quasi-bidirectional / pull-up mode (1 1)
    P3_DIR_PU |= (1 << BOOT_PIN_BIT); // Enable pullup
    P3_2 = 1; // High impedance input read

    // Configure P1.5 with pullup
    P1_MOD_OC |= (1 << 5);             // Quasi-bidirectional / pull-up mode (1 1)
    P1_DIR_PU |= (1 << 5);
    P1_5 = 1;

    // Button pressed if either P3.2 or P1.5 is held to GND
    uint8_t button_pressed = (P3_2 == 0) || (P1_5 == 0);

    // If reset vector at 0x0000 is 0xFFFF, the chip is blank
    uint16_t first_word = IAP_ReadWord(0x0000);
    uint8_t is_blank = (first_word == 0xFFFF);

    // Initialize USB HID
    USB_Init();

    // If button is held or chip has no sketch, stay in bootloader permanently
    uint32_t timeout = 0;
    uint8_t stay_in_bootloader = button_pressed || is_blank;

    while (1) {
        USB_Process();

        if (USB_Available()) {
            stay_in_bootloader = 1; // Handshake received, cancel timeout
            ProcessPacket();
        }

        if (!stay_in_bootloader) {
            timeout++;
            if (timeout > 2000000UL) { // ~4.0s timeout at 24MHz
                JumpToApp();
            }
        }
    }
}
