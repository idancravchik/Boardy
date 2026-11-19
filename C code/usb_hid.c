#include "usb_hid.h"
#include "ch552.h"
#include "ch552_usb.h"
#include <string.h>

// USB RAM buffers (located in XRAM)
__xdata __at(0x0000) uint8_t Ep0Buffer[64];  // מחסנית תקשורת טלפון ללוח
__xdata __at(0x0040) uint8_t Ep1Buffer[128]; // מחסנית תקשורת לוח לטלפון

static volatile uint8_t rx_ready = 0;
static uint8_t usb_dev_addr = 0;
static uint8_t usb_config = 0;

// מבנה מתאר לוח לטלפון
static const __code uint8_t DevDesc[] = {
    0x12,       // bLength
    0x01,       // bDescriptorType (Device)
    0x10, 0x01, // bcdUSB 1.10
    0x00,       // bDeviceClass
    0x00,       // bDeviceSubClass
    0x00,       // bDeviceProtocol
    0x40,       // bMaxPacketSize0 (64 bytes)
    0x09, 0x12, // idVendor (0x1209)
    0x5D, 0xC5, // idProduct (0xC55D)
    0x00, 0x01, // bcdDevice 1.00
    0x01,       // iManufacturer
    0x02,       // iProduct
    0x03,       // iSerialNumber
    0x01        // bNumConfigurations
};
// מבנה עדכון HID
static const __code uint8_t ReportDesc[] = {
    0x06, 0x00, 0xFF, // USAGE_PAGE (Vendor Defined 0xFF00)
    0x09, 0x01,       // USAGE (Vendor Usage 1)
    0xA1, 0x01,       // COLLECTION (Application)
    0x09, 0x02,       //   USAGE (Vendor Usage 2 - OUT)
    0x15, 0x00,       //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00, //   LOGICAL_MAXIMUM (255)
    0x75, 0x08,       //   REPORT_SIZE (8)
    0x95, 0x40,       //   REPORT_COUNT (64 bytes)
    0x91, 0x02,       //   OUTPUT (Data, Var, Abs)
    0x09, 0x03,       //   USAGE (Vendor Usage 3 - IN)
    0x15, 0x00,       //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00, //   LOGICAL_MAXIMUM (255)
    0x75, 0x08,       //   REPORT_SIZE (8)
    0x95, 0x40,       //   REPORT_COUNT (64 bytes)
    0x81, 0x02,       //   INPUT (Data, Var, Abs)
    0xC0              // END_COLLECTION
};

// פירוט הגדרות תקשורת
static const __code uint8_t CfgDesc[] = {
    // Configuration Descriptor
    0x09,       // bLength
    0x02,       // bDescriptorType (Configuration)
    0x29, 0x00, // wTotalLength (41 bytes)
    0x01,       // bNumInterfaces
    0x01,       // bConfigurationValue
    0x00,       // iConfiguration
    0x80,       // bmAttributes (Bus Powered)
    0x32,       // bMaxPower (100 mA)

    0x09, // bLength
    0x04, // bDescriptorType (Interface)
    0x00, // bInterfaceNumber
    0x00, // bAlternateSetting
    0x02, // bNumEndpoints (EP1 IN, EP1 OUT)
    0x03, // bInterfaceClass (HID)
    0x00, // bInterfaceSubClass (None)
    0x00, // bInterfaceProtocol (None)
    0x00, // iInterface

    0x09,                     // bLength
    0x21,                     // bDescriptorType (HID)
    0x11, 0x01,               // bcdHID 1.11
    0x00,                     // bCountryCode
    0x01,                     // bNumDescriptors
    0x22,                     // bDescriptorType (Report)
    sizeof(ReportDesc), 0x00, // wDescriptorLength

    0x07,       // bLength
    0x05,       // bDescriptorType (Endpoint)
    0x01,       // bEndpointAddress (EP1 OUT)
    0x03,       // bmAttributes (Interrupt)
    0x40, 0x00, // wMaxPacketSize (64)
    0x01,       // bInterval (1 ms)

    0x07,       // bLength
    0x05,       // bDescriptorType (Endpoint)
    0x81,       // bEndpointAddress (EP1 IN)
    0x03,       // bmAttributes (Interrupt)
    0x40, 0x00, // wMaxPacketSize (64)
    0x01        // bInterval (1 ms)
};

// שמות וכינויים
static const __code uint8_t Str0Desc[] = {0x04, 0x03, 0x09, 0x04}; // Language ID: 0x0409 (English)
static const __code uint8_t Str1Desc[] = {                         // "DevBox"
                                          0x0E, 0x03,
                                          'D', 0, 'e', 0, 'v', 0, 'B', 0, 'o', 0, 'x', 0};
static const __code uint8_t Str2Desc[] = {// "Idan's DevBoard"
                                          0x20, 0x03,
                                          'I', 0, 'd', 0, 'a', 0, 'n', 0, '\'', 0, 's', 0, ' ', 0,
                                          'D', 0, 'e', 0, 'v', 0, 'B', 0, 'o', 0, 'a', 0, 'r', 0, 'd', 0};
static const __code uint8_t Str3Desc[] = {// "v1.0"
                                          0x0A, 0x03,
                                          'v', 0, '1', 0, '.', 0, '0', 0};

static __code const uint8_t *setup_tx_ptr = 0;
static uint16_t setup_tx_len = 0;

void USB_Init(void)
{
    // Configure 24MHz internal clock
    SAFE_MOD = 0x55;
    SAFE_MOD = 0xAA;
    CLOCK_CFG = 0x86; // 24MHz internal clock
    SAFE_MOD = 0x00;

    // Reset USB SIE
    USB_CTRL = bUC_RESET_SIE | bUC_CLR_ALL;
    USB_CTRL = 0x00;

    // Setup Endpoints DMA pointers in XRAM
    UEP0_DMA_L = (uint8_t)((uint16_t)Ep0Buffer & 0xFF);
    UEP0_DMA_H = (uint8_t)(((uint16_t)Ep0Buffer >> 8) & 0xFF);

    UEP1_DMA_L = (uint8_t)((uint16_t)Ep1Buffer & 0xFF);
    UEP1_DMA_H = (uint8_t)(((uint16_t)Ep1Buffer >> 8) & 0xFF);

    // Endpoint 1: TX & RX dual-buffer enable
    UEP4_1_MOD = 0xC0;
    UEP1_CTRL = bUEP_AUTO_TOG | UEP_T_RES_NAK | UEP_R_RES_ACK;
    UEP1_T_LEN = 0;

    // Endpoint 0: OUT ACK, IN NAK
    UEP0_CTRL = UEP_R_RES_ACK | UEP_T_RES_NAK;

    // USB Controller: Device Mode, Internal Pullup, DMA enable
    USB_CTRL = bUC_DEV_PU_EN | bUC_INT_BUSY | bUC_DMA_EN;
    UDEV_CTRL = bUD_PD_DIS | bUD_PORT_EN; // Full speed 12Mbps
    USB_DEV_AD = 0x00;

    rx_ready = 0;
    usb_dev_addr = 0;
    usb_config = 0;
}

static void Handle_EP0_Setup(void)
{
    uint8_t req_type = Ep0Buffer[0];
    uint8_t req = Ep0Buffer[1];
    uint16_t value = Ep0Buffer[2] | ((uint16_t)Ep0Buffer[3] << 8);
    uint16_t length = Ep0Buffer[6] | ((uint16_t)Ep0Buffer[7] << 8);

    setup_tx_ptr = 0;
    setup_tx_len = 0;

    if ((req_type & USB_REQ_TYP_MASK) == USB_REQ_TYP_STANDARD)
    {
        switch (req)
        {
        case USB_GET_DESCRIPTOR:
            switch (Ep0Buffer[3])
            {
            case 0x01: // Device Descriptor
                setup_tx_ptr = DevDesc;
                setup_tx_len = sizeof(DevDesc);
                break;
            case 0x02: // Configuration Descriptor
                setup_tx_ptr = CfgDesc;
                setup_tx_len = sizeof(CfgDesc);
                break;
            case 0x03: // String Descriptor
                switch (Ep0Buffer[2])
                {
                case 0:
                    setup_tx_ptr = Str0Desc;
                    setup_tx_len = sizeof(Str0Desc);
                    break;
                case 1:
                    setup_tx_ptr = Str1Desc;
                    setup_tx_len = sizeof(Str1Desc);
                    break;
                case 2:
                    setup_tx_ptr = Str2Desc;
                    setup_tx_len = sizeof(Str2Desc);
                    break;
                case 3:
                    setup_tx_ptr = Str3Desc;
                    setup_tx_len = sizeof(Str3Desc);
                    break;
                }
                break;
            case 0x22: // HID Report Descriptor
                setup_tx_ptr = ReportDesc;
                setup_tx_len = sizeof(ReportDesc);
                break;
            }
            break;

        case USB_SET_ADDRESS:
            usb_dev_addr = (uint8_t)value;
            break;

        case USB_SET_CONFIGURATION:
            usb_config = (uint8_t)value;
            break;

        case USB_GET_STATUS:
            Ep0Buffer[0] = 0x00;
            Ep0Buffer[1] = 0x00;
            setup_tx_len = 2;
            break;
        }
    }
    else if ((req_type & USB_REQ_TYP_MASK) == USB_REQ_TYP_CLASS)
    {
        switch (req)
        {
        case HID_SET_IDLE:
            break;
        case HID_GET_REPORT:
            break;
        }
    }

    if (setup_tx_len > length)
    {
        setup_tx_len = length;
    }

    uint8_t send_len = (setup_tx_len > 64) ? 64 : (uint8_t)setup_tx_len;
    if (setup_tx_ptr && send_len > 0)
    {
        memcpy(Ep0Buffer, setup_tx_ptr, send_len);
        setup_tx_ptr += send_len;
        setup_tx_len -= send_len;
    }

    UEP0_T_LEN = send_len;
    UEP0_CTRL = bUEP_R_TOG | bUEP_T_TOG | UEP_T_RES_ACK | UEP_R_RES_ACK;
}

void USB_Process(void)
{
    if (UIF_BUS_RST)
    {
        USB_DEV_AD = 0x00;
        UEP0_CTRL = UEP_R_RES_ACK | UEP_T_RES_NAK;
        UEP1_CTRL = bUEP_AUTO_TOG | UEP_T_RES_NAK | UEP_R_RES_ACK;
        UIF_BUS_RST = 0; // Clear reset flag
        rx_ready = 0;
        return;
    }

    if (UIF_TRANSFER)
    {
        uint8_t token = USB_INT_ST & MASK_UIS_TOKEN;
        uint8_t endp = USB_INT_ST & MASK_UIS_ENDP;

        if (endp == 0)
        { // Endpoint 0
            if (token == UIS_TOKEN_SETUP)
            {
                Handle_EP0_Setup();
            }
            else if (token == UIS_TOKEN_IN)
            {
                if (usb_dev_addr != 0 && USB_DEV_AD == 0)
                {
                    USB_DEV_AD = usb_dev_addr;
                }
                if (setup_tx_len > 0)
                {
                    uint8_t send_len = (setup_tx_len > 64) ? 64 : (uint8_t)setup_tx_len;
                    memcpy(Ep0Buffer, setup_tx_ptr, send_len);
                    setup_tx_ptr += send_len;
                    setup_tx_len -= send_len;
                    UEP0_T_LEN = send_len;
                    UEP0_CTRL ^= bUEP_T_TOG;
                    UEP0_CTRL = (UEP0_CTRL & ~MASK_UEP_T_RES) | UEP_T_RES_ACK;
                }
                else
                {
                    UEP0_T_LEN = 0;
                    UEP0_CTRL = UEP_R_RES_ACK | UEP_T_RES_NAK;
                }
            }
            else if (token == UIS_TOKEN_OUT)
            {
                UEP0_CTRL = UEP_R_RES_ACK | UEP_T_RES_NAK;
            }
        }
        else if (endp == 1)
        { // Endpoint 1 (HID Data)
            if (token == UIS_TOKEN_OUT)
            {
                rx_ready = 1;
                UEP1_CTRL = (UEP1_CTRL & ~MASK_UEP_R_RES) | UEP_R_RES_NAK;
            }
            else if (token == UIS_TOKEN_IN)
            {
                UEP1_T_LEN = 0;
                UEP1_CTRL = (UEP1_CTRL & ~MASK_UEP_T_RES) | UEP_T_RES_NAK;
            }
        }

        UIF_TRANSFER = 0; // Clear transfer flag
    }
}

uint8_t USB_Available(void)
{
    return rx_ready;
}

uint8_t *USB_GetRxBuffer(void)
{
    return &Ep1Buffer[0];
}

void USB_ReleaseRx(void)
{
    rx_ready = 0;
    UEP1_CTRL = (UEP1_CTRL & ~MASK_UEP_R_RES) | UEP_R_RES_ACK;
}

void USB_SendReport(const uint8_t *buf, uint8_t len)
{
    memcpy(&Ep1Buffer[64], buf, len);
    UEP1_T_LEN = len;
    UEP1_CTRL = (UEP1_CTRL & ~MASK_UEP_T_RES) | UEP_T_RES_ACK;
}

void USB_Shutdown(void)
{
    USB_CTRL = 0x00;
    UDEV_CTRL = 0x00;
}
