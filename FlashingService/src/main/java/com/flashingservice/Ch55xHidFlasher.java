package com.flashingservice;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android USB Host library for flashing WCH CH552 microcontrollers over USB HID (Java).
 * Includes auto-detection and Auto-Reset from CDC Serial mode (0xC550) to HID Bootloader (0xC55D).
 */
public class Ch55xHidFlasher {

    public static final int VENDOR_ID = 0x1209;
    public static final int PRODUCT_ID = 0xC55D;      // USB HID Bootloader
    public static final int CDC_PRODUCT_ID = 0xC550;  // USB CDC Serial User Application

    public static final byte MAGIC_RESET_BYTE = (byte) 0xFE; // Trigger jump to 0x2C00

    public static final byte CMD_HELLO = 0x01;
    public static final byte CMD_UNLOCK = 0x02;
    public static final byte CMD_ERASE = 0x03;
    public static final byte CMD_WRITE_BLOCK = 0x04;
    public static final byte CMD_VERIFY_CRC = 0x05;
    public static final byte CMD_REBOOT = 0x06;

    public static final int CHUNK_SIZE = 48;
    public static final int USB_TIMEOUT_MS = 2000;
    private static final String ACTION_USB_PERMISSION = "com.flashingservice.USB_PERMISSION";

    public interface PermissionCallback {
        void onPermissionResult(boolean granted);
    }

    /**
     * Listener for automatic CH552 board connection/disconnection events.
     * All callbacks are delivered on Android's Main (UI) thread.
     */
    public interface BoardConnectionListener {
        /**
         * Called when a CH552 board is connected, switched to HID Bootloader (if needed),
         * granted USB permission, and locked in bootloader mode via CMD_HELLO.
         * Ready for manual {@link #flash(UsbDevice, String, FlasherCallback)}.
         */
        void onBoardReady(UsbDevice hidDevice);

        /**
         * Called when the board is physically disconnected.
         */
        void onBoardDisconnected();

        /**
         * Called when the user denies the USB permission dialog.
         */
        void onPermissionDenied();

        /**
         * Optional callback for connection/handshake errors (e.g. CDC reset timeout).
         */
        default void onError(Throwable error) {}
    }

    private final Context context;
    private final UsbManager usbManager;
    private final ExecutorService executor;
    private final Handler mainHandler;

    private BroadcastReceiver usbWatcherReceiver;
    private BoardConnectionListener connectionListener;
    private volatile boolean isWatching = false;
    private volatile boolean isFlashing = false;
    private volatile boolean isResettingCdc = false;
    private volatile boolean isPreparingDevice = false;
    private UsbDevice readyHidDevice = null;
    private Runnable pendingDisconnectRunnable = null;

    public Ch55xHidFlasher(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Safely wraps callbacks so they are always delivered on Android's Main (UI) thread.
     */
    private FlasherCallback wrapMainThread(FlasherCallback callback) {
        if (callback == null) return null;
        return new FlasherCallback() {
            @Override
            public void onProgress(int percent, int currentBytes, int totalBytes) {
                mainHandler.post(() -> callback.onProgress(percent, currentBytes, totalBytes));
            }

            @Override
            public void onStatus(String message) {
                mainHandler.post(() -> callback.onStatus(message));
            }

            @Override
            public void onSuccess() {
                mainHandler.post(callback::onSuccess);
            }

            @Override
            public void onError(Throwable error) {
                mainHandler.post(() -> callback.onError(error));
            }
        };
    }

    /**
     * Finds connected CH552 HID Bootloader devices matching VID 0x1209 and PID 0xC55D.
     */
    public UsbDevice findConnectedDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == VENDOR_ID && device.getProductId() == PRODUCT_ID) {
                return device;
            }
        }
        return null;
    }

    /**
     * Finds connected CH552 CDC Serial devices matching VID 0x1209 and PID 0xC550.
     */
    public UsbDevice findConnectedCdcDevice() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (device.getVendorId() == VENDOR_ID && device.getProductId() == CDC_PRODUCT_ID) {
                return device;
            }
        }
        return null;
    }

    /**
     * Checks if permission is granted for the given USB device.
     */
    public boolean hasPermission(UsbDevice device) {
        return usbManager.hasPermission(device);
    }

    /**
     * Requests USB permission from the user for the given device.
     */
    public void requestPermission(UsbDevice device, PermissionCallback callback) {
        if (hasPermission(device)) {
            callback.onPermissionResult(true);
            return;
        }

        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(context.getPackageName());

        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                flags
        );

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    context.unregisterReceiver(this);
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    callback.onPermissionResult(granted);
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }

        usbManager.requestPermission(device, permissionIntent);
    }

    /**
     * Starts watching for a CH552 board (either already connected or plugged in later).
     * Automatically handles CDC (0xC550) -> HID (0xC55D) reset, requests USB permission,
     * and sends CMD_HELLO to lock the board in bootloader mode (cancelling the 4s timeout).
     * Call {@link #stopWatchingBoard()} in Activity.onDestroy() to avoid leaks.
     */
    public void startWatchingBoard(BoardConnectionListener listener) {
        stopWatchingBoard();
        if (listener == null) return;

        this.connectionListener = listener;
        this.isWatching = true;
        this.isResettingCdc = false;
        this.isPreparingDevice = false;
        this.readyHidDevice = null;

        usbWatcherReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (!isWatching || intent == null) return;
                String action = intent.getAction();
                UsbDevice dev = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (dev == null || dev.getVendorId() != VENDOR_ID) return;

                int pid = dev.getProductId();
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    if (pid == PRODUCT_ID) {
                        handleHidDeviceAttached(dev);
                    } else if (pid == CDC_PRODUCT_ID) {
                        handleCdcDeviceAttached(dev);
                    }
                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    if (pid == PRODUCT_ID || pid == CDC_PRODUCT_ID) {
                        handleDeviceDetached(dev);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbWatcherReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbWatcherReceiver, filter);
        }

        // Check if a CH552 board is already plugged in
        checkAlreadyConnectedBoard();
    }

    /**
     * Stops watching for USB board attach/detach events and releases the receiver.
     */
    public void stopWatchingBoard() {
        isWatching = false;
        isResettingCdc = false;
        isPreparingDevice = false;
        readyHidDevice = null;
        connectionListener = null;

        if (pendingDisconnectRunnable != null) {
            mainHandler.removeCallbacks(pendingDisconnectRunnable);
            pendingDisconnectRunnable = null;
        }

        if (usbWatcherReceiver != null) {
            try {
                context.unregisterReceiver(usbWatcherReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered
            }
            usbWatcherReceiver = null;
        }
    }

    private void checkAlreadyConnectedBoard() {
        if (!isWatching || isFlashing) return;

        UsbDevice hidDevice = findConnectedDevice();
        if (hidDevice != null) {
            handleHidDeviceAttached(hidDevice);
            return;
        }

        UsbDevice cdcDevice = findConnectedCdcDevice();
        if (cdcDevice != null) {
            handleCdcDeviceAttached(cdcDevice);
        }
    }

    private void cancelPendingDisconnect() {
        if (pendingDisconnectRunnable != null) {
            mainHandler.removeCallbacks(pendingDisconnectRunnable);
            pendingDisconnectRunnable = null;
        }
    }

    private void handleHidDeviceAttached(UsbDevice hidDevice) {
        if (!isWatching || isFlashing || isPreparingDevice) return;
        if (readyHidDevice != null && readyHidDevice.getDeviceId() == hidDevice.getDeviceId()) return;

        cancelPendingDisconnect();
        isPreparingDevice = true;

        requestPermission(hidDevice, granted -> {
            if (!isWatching) {
                isPreparingDevice = false;
                return;
            }
            if (!granted) {
                isPreparingDevice = false;
                isResettingCdc = false;
                BoardConnectionListener l = connectionListener;
                if (l != null) mainHandler.post(l::onPermissionDenied);
                return;
            }

            // Send CMD_HELLO on background thread to lock the bootloader (cancels 4s timeout)
            executor.execute(() -> {
                try {
                    Thread.sleep(150); // Brief settle time for Android USB stack
                    lockBootloaderWithHello(hidDevice);

                    if (!isWatching) {
                        isPreparingDevice = false;
                        return;
                    }

                    isResettingCdc = false;
                    isPreparingDevice = false;
                    readyHidDevice = hidDevice;
                    BoardConnectionListener l = connectionListener;
                    if (l != null) {
                        mainHandler.post(() -> l.onBoardReady(hidDevice));
                    }
                } catch (Exception e) {
                    isPreparingDevice = false;
                    isResettingCdc = false;
                    if (!isWatching) return;

                    // If bootloader timed out during permission dialog and jumped to CDC, catch it automatically
                    UsbDevice cdcFallback = findConnectedCdcDevice();
                    if (cdcFallback != null) {
                        mainHandler.post(() -> handleCdcDeviceAttached(cdcFallback));
                        return;
                    }

                    BoardConnectionListener l = connectionListener;
                    if (l != null && findConnectedDevice() != null) {
                        mainHandler.post(() -> l.onError(e));
                    }
                }
            });
        });
    }

    private void handleCdcDeviceAttached(UsbDevice cdcDevice) {
        if (!isWatching || isFlashing || isPreparingDevice || isResettingCdc) return;

        cancelPendingDisconnect();
        isPreparingDevice = true;

        requestPermission(cdcDevice, granted -> {
            if (!isWatching) {
                isPreparingDevice = false;
                return;
            }
            if (!granted) {
                isPreparingDevice = false;
                BoardConnectionListener l = connectionListener;
                if (l != null) mainHandler.post(l::onPermissionDenied);
                return;
            }

            isResettingCdc = true;
            executor.execute(() -> {
                try {
                    sendCdcReset(cdcDevice);
                    isPreparingDevice = false;

                    // Wait up to 4s for HID Bootloader (0xC55D) to appear
                    long startTime = System.currentTimeMillis();
                    while (isWatching && isResettingCdc && (System.currentTimeMillis() - startTime < 4000)) {
                        Thread.sleep(150);
                        UsbDevice newHid = findConnectedDevice();
                        if (newHid != null) {
                            mainHandler.post(() -> handleHidDeviceAttached(newHid));
                            return;
                        }
                    }

                    if (isWatching && isResettingCdc && readyHidDevice == null) {
                        isResettingCdc = false;
                        String visibleDevices = describeConnectedUsbDevices();
                        BoardConnectionListener l = connectionListener;
                        if (l != null) {
                            mainHandler.post(() -> l.onError(
                                    new IOException("Timed out waiting for CH552 to reboot into HID Bootloader. Visible USB: " + visibleDevices)
                            ));
                        }
                    }
                } catch (Exception e) {
                    isPreparingDevice = false;
                    isResettingCdc = false;
                    BoardConnectionListener l = connectionListener;
                    if (isWatching && l != null) {
                        mainHandler.post(() -> l.onError(e));
                    }
                }
            });
        });
    }

    private void handleDeviceDetached(UsbDevice detached) {
        if (!isWatching || isFlashing || isResettingCdc) return;

        if (readyHidDevice != null && detached.getDeviceId() == readyHidDevice.getDeviceId()) {
            cancelPendingDisconnect();
            pendingDisconnectRunnable = () -> {
                pendingDisconnectRunnable = null;
                if (!isWatching || isFlashing || isResettingCdc) return;

                if (findConnectedDevice() == null && findConnectedCdcDevice() == null) {
                    readyHidDevice = null;
                    BoardConnectionListener l = connectionListener;
                    if (l != null) l.onBoardDisconnected();
                } else if (findConnectedDevice() == null) {
                    // Board rebooted into CDC; re-trigger reset flow
                    readyHidDevice = null;
                    checkAlreadyConnectedBoard();
                }
            };
            mainHandler.postDelayed(pendingDisconnectRunnable, 800);
        }
    }

    /**
     * Sends CMD_HELLO to the HID bootloader to confirm readiness and cancel its 4-second auto-jump timeout.
     */
    private void lockBootloaderWithHello(UsbDevice device) throws IOException {
        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            throw new IOException("Failed to open USB HID device for handshake.");
        }
        UsbInterface usbInterface = null;
        try {
            usbInterface = device.getInterface(0);
            if (!connection.claimInterface(usbInterface, true)) {
                throw new IOException("Failed to claim USB HID interface 0.");
            }
            UsbEndpoint epOut = null;
            UsbEndpoint epIn = null;
            for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                UsbEndpoint ep = usbInterface.getEndpoint(i);
                if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    epOut = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    epIn = ep;
                }
            }
            if (epOut == null || epIn == null) {
                throw new IOException("Required USB HID endpoints (IN/OUT) not found.");
            }
            transceive(connection, epOut, epIn, CMD_HELLO, (byte) 0, 0, new byte[0]);
        } finally {
            if (usbInterface != null) {
                connection.releaseInterface(usbInterface);
            }
            connection.close();
        }
    }

    /**
     * Sends the magic reset byte to a connected CH552 CDC Serial device,
     * commanding it to disconnect USB and jump to the HID Bootloader at 0x2C00.
     */
    public void sendCdcReset(UsbDevice cdcDevice) throws IOException {
        UsbDeviceConnection conn = usbManager.openDevice(cdcDevice);
        if (conn == null) {
            throw new IOException("Failed to open connection to CDC Serial device. Ensure permissions are granted.");
        }

        try {
            UsbEndpoint epOut = null;
            UsbInterface targetInterface = null;

            // Search for bulk OUT endpoint across all interfaces
            for (int i = 0; i < cdcDevice.getInterfaceCount(); i++) {
                UsbInterface iface = cdcDevice.getInterface(i);
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT &&
                            ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        epOut = ep;
                        targetInterface = iface;
                        break;
                    }
                }
                if (epOut != null) break;
            }

            if (epOut == null || targetInterface == null) {
                throw new IOException("CDC Bulk OUT endpoint not found on device.");
            }

            if (!conn.claimInterface(targetInterface, true)) {
                throw new IOException("Failed to claim CDC interface " + targetInterface.getId());
            }

            // Also claim CDC Control interface (0) if separate, so SET_CONTROL_LINE_STATE succeeds on Android
            UsbInterface controlInterface = (cdcDevice.getInterfaceCount() > 0 && cdcDevice.getInterface(0) != targetInterface)
                    ? cdcDevice.getInterface(0)
                    : null;
            boolean claimedControl = controlInterface != null && conn.claimInterface(controlInterface, true);

            try {
                // Assert DTR/RTS (CDC Set_Control_Line_State request 0x22, value 0x03)
                int ctrlIndex = controlInterface != null ? controlInterface.getId() : targetInterface.getId();
                conn.controlTransfer(0x21, 0x22, 0x03, ctrlIndex, null, 0, 1000);

                // Send the magic reset byte (0xFE)
                byte[] payload = new byte[]{MAGIC_RESET_BYTE};
                int sent = conn.bulkTransfer(epOut, payload, payload.length, 1000);
                if (sent < payload.length) {
                    throw new IOException("Failed to send reset byte to CDC: transferred " + sent + " bytes.");
                }
            } finally {
                if (claimedControl) {
                    conn.releaseInterface(controlInterface);
                }
                conn.releaseInterface(targetInterface);
            }
        } finally {
            conn.close();
        }
    }

    private String describeConnectedUsbDevices() {
        HashMap<String, UsbDevice> list = usbManager.getDeviceList();
        if (list.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (UsbDevice d : list.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(String.format("0x%04X:0x%04X", d.getVendorId(), d.getProductId()));
        }
        return sb.toString();
    }

    /**
     * Reads an Intel HEX file from app assets.
     */
    public String readHexFromAssets(String fileName) throws IOException {
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    /**
     * 1-Step Automatic Flashing:
     * 1. Detects whether CH552 is in HID Bootloader mode (0xC55D) or CDC Serial mode (0xC550).
     * 2. If in CDC Serial mode, triggers Auto-Reset and seamlessly waits for HID Bootloader.
     * 3. Checks and requests USB permissions automatically.
     * 4. Flashes the firmware in a background thread.
     * 5. Delivers all callbacks safely on Android's Main (UI) thread!
     */
    public void flashAuto(String hexContent, FlasherCallback callback) {
        FlasherCallback safeCallback = wrapMainThread(callback);

        // Scenario 1: Device is already in HID Bootloader mode
        UsbDevice hidDevice = findConnectedDevice();
        if (hidDevice != null) {
            flashDirect(hidDevice, hexContent, safeCallback);
            return;
        }

        // Scenario 2: Device is running in CDC Serial mode
        UsbDevice cdcDevice = findConnectedCdcDevice();
        if (cdcDevice != null) {
            if (safeCallback != null) {
                safeCallback.onStatus("Found CH552 in CDC Serial mode. Requesting permission to switch to Bootloader...");
            }

            requestPermission(cdcDevice, granted -> {
                if (!granted) {
                    if (safeCallback != null) {
                        safeCallback.onError(new IOException("CDC USB permission was denied by user."));
                    }
                    return;
                }

                executor.execute(() -> {
                    try {
                        if (safeCallback != null) {
                            safeCallback.onStatus("Switching CH552 from Serial CDC to HID Bootloader...");
                        }
                        sendCdcReset(cdcDevice);

                        // Wait for device to detach and re-enumerate as HID (VID: 0x1209, PID: 0xC55D)
                        long startTime = System.currentTimeMillis();
                        UsbDevice newHidDevice = null;
                        while (System.currentTimeMillis() - startTime < 4000) {
                            try {
                                Thread.sleep(150);
                            } catch (InterruptedException ignored) {}

                            newHidDevice = findConnectedDevice();
                            if (newHidDevice != null) {
                                break;
                            }
                        }

                        if (newHidDevice == null) {
                            throw new IOException("Timed out waiting for CH552 to reboot into HID Bootloader.");
                        }

                        try {
                            Thread.sleep(300); // Allow USB stack and OS to settle before claiming interface
                        } catch (InterruptedException ignored) {}

                        UsbDevice finalDevice = newHidDevice;
                        mainHandler.post(() -> flashDirect(finalDevice, hexContent, safeCallback));

                    } catch (Exception e) {
                        if (safeCallback != null) {
                            safeCallback.onError(e);
                        }
                    }
                });
            });
            return;
        }

        // Scenario 3: Neither device detected
        if (safeCallback != null) {
            safeCallback.onError(new IOException("CH552 device not found. Connect board via USB OTG adapter."));
        }
    }

    private void flashDirect(UsbDevice device, String hexContent, FlasherCallback safeCallback) {
        if (safeCallback != null) {
            safeCallback.onStatus("Requesting USB HID device permission...");
        }

        requestPermission(device, granted -> {
            if (!granted) {
                if (safeCallback != null) {
                    safeCallback.onError(new IOException("USB HID permission was denied by user."));
                }
                return;
            }
            flash(device, hexContent, safeCallback);
        });
    }

    /**
     * 1-Step Automatic Flashing from an Asset file (e.g. "firmware.hex"):
     * Loads the file from assets/ and flashes it to the connected board automatically.
     * All callbacks delivered on Android's Main (UI) thread.
     */
    public void flashAsset(String assetFileName, FlasherCallback callback) {
        FlasherCallback safeCallback = wrapMainThread(callback);
        try {
            String hex = readHexFromAssets(assetFileName);
            flashAuto(hex, safeCallback);
        } catch (Exception e) {
            if (safeCallback != null) {
                safeCallback.onError(new IOException("Failed to load asset file '" + assetFileName + "': " + e.getMessage(), e));
            }
        }
    }

    /**
     * Asynchronously flashes an Intel HEX string to the connected CH552 device.
     * All callbacks are safely delivered on Android's Main (UI) thread.
     */
    public void flash(UsbDevice device, String hexText, FlasherCallback callback) {
        FlasherCallback safeCallback = wrapMainThread(callback);
        isFlashing = true;
        cancelPendingDisconnect();
        executor.execute(() -> {
            UsbDeviceConnection connection = null;
            UsbInterface usbInterface = null;

            try {
                if (safeCallback != null) safeCallback.onStatus("Opening USB device connection...");
                connection = usbManager.openDevice(device);
                if (connection == null) {
                    throw new IOException("Failed to open USB device connection. Ensure permissions are granted.");
                }

                usbInterface = device.getInterface(0);
                if (!connection.claimInterface(usbInterface, true)) {
                    throw new IOException("Failed to claim USB HID interface 0.");
                }

                UsbEndpoint epOut = null;
                UsbEndpoint epIn = null;

                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint ep = usbInterface.getEndpoint(i);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        epOut = ep;
                    } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        epIn = ep;
                    }
                }

                if (epOut == null || epIn == null) {
                    throw new IOException("Required USB HID endpoints (IN/OUT) not found on device interface.");
                }

                // 1. Handshake (CMD_HELLO)
                if (safeCallback != null) safeCallback.onStatus("Connecting to bootloader (CMD_HELLO)...");
                byte seq = 0;
                byte[] helloResp = transceive(connection, epOut, epIn, CMD_HELLO, seq++, 0, new byte[0]);
                int chipId = helloResp[4] & 0xFF;
                int version = helloResp[5] & 0xFF;
                int maxFlash = (helloResp[6] & 0xFF) | ((helloResp[7] & 0xFF) << 8);

                if (safeCallback != null) {
                    safeCallback.onStatus("Detected chip ID 0x" + Integer.toHexString(chipId) +
                            " (v" + version + ".0, limit " + maxFlash + " bytes)");
                }

                // 2. Parse Intel HEX
                if (safeCallback != null) safeCallback.onStatus("Parsing Intel HEX firmware...");
                IntelHexParser.ParsedHex parsed = IntelHexParser.parse(hexText, maxFlash);
                byte[] binData = parsed.getData();
                int totalBytes = parsed.getEndAddress();
                int expectedCrc = parsed.getCrc16();

                // 3. Unlock Flash engine (CMD_UNLOCK)
                if (safeCallback != null) safeCallback.onStatus("Unlocking IAP Flash engine...");
                byte[] unlockToken = new byte[]{(byte) 0x5A, (byte) 0xC3, (byte) 0x3C, (byte) 0xA5};
                byte[] unlockResp = transceive(connection, epOut, epIn, CMD_UNLOCK, seq++, 0, unlockToken);
                if (unlockResp[59] != 0) {
                    throw new IOException("Bootloader rejected IAP unlock key.");
                }

                // 4. Erase previous flash partition (CMD_ERASE)
                if (safeCallback != null) safeCallback.onStatus("Erasing user Flash partition...");
                try {
                    byte[] eraseResp = transceive(connection, epOut, epIn, CMD_ERASE, seq++, 0, new byte[0]);
                    int eraseStatus = eraseResp[59] & 0xFF;
                    if (eraseStatus != 0x00 && eraseStatus != 0xFF) {
                        if (safeCallback != null) {
                            safeCallback.onStatus("Flash erase status: 0x" + Integer.toHexString(eraseStatus) + ", proceeding with write...");
                        }
                    }
                } catch (Exception e) {
                    if (safeCallback != null) {
                        safeCallback.onStatus("Flash erase skipped (" + e.getMessage() + "), proceeding with write...");
                    }
                }

                // 5. Flash blocks (CMD_WRITE_BLOCK)
                if (safeCallback != null) safeCallback.onStatus("Writing firmware (" + totalBytes + " bytes)...");
                int offset = 0;
                while (offset < totalBytes) {
                    int remaining = totalBytes - offset;
                    int chunkSize = Math.min(remaining, CHUNK_SIZE);
                    byte[] chunk = new byte[chunkSize];
                    System.arraycopy(binData, offset, chunk, 0, chunkSize);

                    byte[] writeResp = transceive(connection, epOut, epIn, CMD_WRITE_BLOCK, seq++, offset, chunk);
                    int status = writeResp[59];
                    if (status != 0) {
                        throw new IOException("Flash write failed at offset 0x" + Integer.toHexString(offset) +
                                " with error code 0x" + Integer.toHexString(status));
                    }

                    offset += chunkSize;
                    int percent = (int) (((double) offset / totalBytes) * 100);
                    if (safeCallback != null) safeCallback.onProgress(percent, offset, totalBytes);
                }

                // 5. Verify CRC (CMD_VERIFY_CRC)
                if (safeCallback != null) safeCallback.onStatus("Verifying written firmware integrity (CRC16)...");
                byte[] crcPayload = new byte[]{(byte) (expectedCrc & 0xFF), (byte) ((expectedCrc >> 8) & 0xFF)};
                byte[] verifyResp = transceive(connection, epOut, epIn, CMD_VERIFY_CRC, seq++, totalBytes, crcPayload);
                if (verifyResp[59] != 0) {
                    int chipCrc = (verifyResp[4] & 0xFF) | ((verifyResp[5] & 0xFF) << 8);
                    throw new IOException("CRC check failed! Chip: 0x" + Integer.toHexString(chipCrc) +
                            ", Expected: 0x" + Integer.toHexString(expectedCrc));
                }

                // 6. Reboot device (CMD_REBOOT)
                if (safeCallback != null) safeCallback.onStatus("Rebooting device into user application...");
                try {
                    transceive(connection, epOut, epIn, CMD_REBOOT, seq++, 0, new byte[0], 300);
                } catch (Exception ignored) {
                    // Detaches immediately on reboot
                }

                if (safeCallback != null) {
                    safeCallback.onStatus("Done! Microcontroller successfully programmed.");
                    safeCallback.onSuccess();
                }

            } catch (Exception e) {
                if (safeCallback != null) safeCallback.onError(e);
            } finally {
                readyHidDevice = null;
                isFlashing = false;
                if (usbInterface != null && connection != null) {
                    connection.releaseInterface(usbInterface);
                }
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }

    /**
     * Explicitly erases the user flash partition (up to totalBytes, or entire partition if 0).
     * Protected from erasing the bootloader.
     */
    public void eraseUserPartition(UsbDevice device, int totalBytes, FlasherCallback callback) {
        FlasherCallback safeCallback = wrapMainThread(callback);
        executor.execute(() -> {
            UsbDeviceConnection connection = null;
            UsbInterface usbInterface = null;
            try {
                if (safeCallback != null) safeCallback.onStatus("Opening USB device connection...");
                connection = usbManager.openDevice(device);
                if (connection == null) {
                    throw new IOException("Failed to open USB device connection. Ensure permissions are granted.");
                }

                usbInterface = device.getInterface(0);
                if (!connection.claimInterface(usbInterface, true)) {
                    throw new IOException("Failed to claim USB HID interface 0.");
                }

                UsbEndpoint epOut = null;
                UsbEndpoint epIn = null;
                for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
                    UsbEndpoint ep = usbInterface.getEndpoint(i);
                    if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                        epOut = ep;
                    } else if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        epIn = ep;
                    }
                }
                if (epOut == null || epIn == null) {
                    throw new IOException("Required USB HID endpoints (IN/OUT) not found.");
                }

                byte seq = 0;
                transceive(connection, epOut, epIn, CMD_HELLO, seq++, 0, new byte[0]);

                byte[] unlockToken = new byte[]{(byte) 0x5A, (byte) 0xC3, (byte) 0x3C, (byte) 0xA5};
                byte[] unlockResp = transceive(connection, epOut, epIn, CMD_UNLOCK, seq++, 0, unlockToken);
                if (unlockResp[59] != 0) {
                    throw new IOException("Bootloader rejected IAP unlock key.");
                }

                if (safeCallback != null) safeCallback.onStatus("Erasing user Flash partition...");
                byte[] eraseResp = transceive(connection, epOut, epIn, CMD_ERASE, seq++, totalBytes, new byte[0]);
                int eraseStatus = eraseResp[59] & 0xFF;
                if (eraseStatus != 0x00) {
                    throw new IOException("Flash erase failed with status 0x" + Integer.toHexString(eraseStatus));
                }

                if (safeCallback != null) {
                    safeCallback.onStatus("User flash partition successfully erased.");
                    safeCallback.onSuccess();
                }
            } catch (Exception e) {
                if (safeCallback != null) safeCallback.onError(e);
            } finally {
                if (usbInterface != null && connection != null) {
                    connection.releaseInterface(usbInterface);
                }
                if (connection != null) {
                    connection.close();
                }
            }
        });
    }

    private byte[] transceive(
            UsbDeviceConnection connection,
            UsbEndpoint epOut,
            UsbEndpoint epIn,
            byte cmd,
            byte seq,
            int addr,
            byte[] payload
    ) throws IOException {
        return transceive(connection, epOut, epIn, cmd, seq, addr, payload, USB_TIMEOUT_MS);
    }

    private byte[] transceive(
            UsbDeviceConnection connection,
            UsbEndpoint epOut,
            UsbEndpoint epIn,
            byte cmd,
            byte seq,
            int addr,
            byte[] payload,
            int timeoutMs
    ) throws IOException {
        byte[] txPacket = new byte[64];
        txPacket[0] = cmd;
        txPacket[1] = seq;
        txPacket[2] = (byte) (addr & 0xFF);
        txPacket[3] = (byte) ((addr >> 8) & 0xFF);
        txPacket[4] = (byte) payload.length;

        System.arraycopy(payload, 0, txPacket, 5, payload.length);

        int sent = connection.bulkTransfer(epOut, txPacket, 64, timeoutMs);
        if (sent < 64) {
            throw new IOException("USB write error: sent " + sent + "/64 bytes for command 0x" + Integer.toHexString(cmd));
        }

        byte[] rxPacket = new byte[64];
        int received = connection.bulkTransfer(epIn, rxPacket, 64, timeoutMs);
        if (received < 64) {
            throw new IOException("USB read error: received " + received + "/64 bytes for command 0x" + Integer.toHexString(cmd));
        }

        return rxPacket;
    }
}
