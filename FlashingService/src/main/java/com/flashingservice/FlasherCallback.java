package com.flashingservice;

/**
 * Callback listener for CH552 HID flashing progress and lifecycle events.
 */
public interface FlasherCallback {
    /**
     * Called when flashing progress changes.
     * @param percent Progress from 0 to 100.
     * @param currentBytes Number of bytes written so far.
     * @param totalBytes Total number of bytes to write.
     */
    void onProgress(int percent, int currentBytes, int totalBytes);

    /**
     * Informational status messages (e.g. "Connecting...", "Writing firmware...").
     * @param message Status text description.
     */
    void onStatus(String message);

    /**
     * Called when the firmware was verified and successfully flashed.
     */
    void onSuccess();

    /**
     * Called if an error or timeout occurs during the flash process.
     * @param error Throwable error.
     */
    void onError(Throwable error);
}
