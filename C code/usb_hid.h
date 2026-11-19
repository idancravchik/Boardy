#ifndef __USB_HID_H__
#define __USB_HID_H__

#include <stdint.h>
#include "ch552.h"

// הגדרות לוח
#define HID_REPORT_SIZE 64
#define VENDOR_ID 0x1209
#define PRODUCT_ID 0xC55D

// קובץ לניהול התקשורת בין הלוח לטלפון

void USB_Init(void); // מפעיל את תקשות הHID

void USB_Process(void); // תהליכון USB

uint8_t USB_Available(void); // מחזיר טרו אם יש חבילה מוכנה לקריאה שהגיעה מהטלפון

uint8_t *USB_GetRxBuffer(void); // מצביע לחבילה שהגיעה

void USB_ReleaseRx(void); // מפנה את המחסנית של הקבלה כדי לקבל את החבילה הבאה

void USB_SendReport(const uint8_t *buf, uint8_t len); // מחזיר תשובה ללוח

void USB_Shutdown(void); // מנתק את התקשורת USB לפני שמתחילים את התכניית המרכזית

#endif // __USB_HID_H__
