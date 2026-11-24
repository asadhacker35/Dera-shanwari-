package com.derashanwari.admin.util;

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

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * USB Host OTG manager for the Black Copper BC-87AC 80mm ESC/POS thermal receipt printer.
 * Runs all USB I/O off the main thread and never throws uncaught exceptions when hardware is absent.
 */
public final class UsbThermalPrinterManager {
    private static final String ACTION_USB_PERMISSION = "com.derashanwari.admin.USB_PRINTER_PERMISSION";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final int WRITE_TIMEOUT_MS = 5000;

    public interface PrintCallback {
        void onSuccess(String deviceLabel);
        void onError(String message);
    }

    public static class PrinterTarget {
        public final UsbDevice device;
        public final UsbInterface usbInterface;
        public final UsbEndpoint outEndpoint;

        public PrinterTarget(UsbDevice device, UsbInterface usbInterface, UsbEndpoint outEndpoint) {
            this.device = device;
            this.usbInterface = usbInterface;
            this.outEndpoint = outEndpoint;
        }

        public String getLabel() {
            String prod = device.getProductName();
            if (prod != null && !prod.trim().isEmpty()) {
                return prod.trim() + " (VID:" + device.getVendorId() + ")";
            }
            return "USB Thermal Printer (VID:" + device.getVendorId() + " PID:" + device.getProductId() + ")";
        }
    }

    private UsbThermalPrinterManager() {}

    public static String describeConnectedPrinter(Context context) {
        if (context == null) return "USB service unavailable";
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return "USB Host not supported on this device";
        PrinterTarget target = findBestPrinterTarget(usbManager);
        if (target == null) {
            return "No USB OTG thermal printer detected. Connect Black Copper BC-87AC via OTG cable.";
        }
        boolean perm = usbManager.hasPermission(target.device);
        return "Connected: " + target.getLabel() + (perm ? " [Permission Granted]" : " [Tap Test Print to grant USB permission]");
    }

    public static void printBytes(Context context, byte[] payload, PrintCallback callback) {
        if (context == null || payload == null || payload.length == 0) {
            postError(callback, "Empty receipt payload.");
            return;
        }
        Context appCtx = context.getApplicationContext();
        UsbManager usbManager = (UsbManager) appCtx.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            postError(callback, "USB Host API not available on this device.");
            return;
        }

        PrinterTarget target = findBestPrinterTarget(usbManager);
        if (target == null) {
            postError(callback, "Black Copper BC-87AC printer not detected over USB OTG.");
            return;
        }

        if (usbManager.hasPermission(target.device)) {
            sendAsync(usbManager, target, payload, callback);
            return;
        }

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
                try {
                    appCtx.unregisterReceiver(this);
                } catch (Exception ignored) {
                }
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && usbManager.hasPermission(target.device)) {
                    sendAsync(usbManager, target, payload, callback);
                } else {
                    postError(callback, "USB printer permission was denied.");
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appCtx.registerReceiver(receiver, filter);
        }

        Intent permIntent = new Intent(ACTION_USB_PERMISSION);
        permIntent.setPackage(appCtx.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getBroadcast(appCtx, 4801, permIntent, flags);
        usbManager.requestPermission(target.device, pi);
    }

    public static PrinterTarget findBestPrinterTarget(UsbManager usbManager) {
        if (usbManager == null) return null;
        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices == null || devices.isEmpty()) return null;

        PrinterTarget fallbackBulkTarget = null;
        for (UsbDevice dev : devices.values()) {
            if (dev == null) continue;
            for (int i = 0; i < dev.getInterfaceCount(); i++) {
                UsbInterface intf = dev.getInterface(i);
                if (intf == null) continue;
                UsbEndpoint outEp = findBulkOutEndpoint(intf);
                if (outEp == null) continue;
                if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_PRINTER) {
                    return new PrinterTarget(dev, intf, outEp);
                }
                if (fallbackBulkTarget == null) {
                    fallbackBulkTarget = new PrinterTarget(dev, intf, outEp);
                }
            }
        }
        return fallbackBulkTarget;
    }

    private static UsbEndpoint findBulkOutEndpoint(UsbInterface intf) {
        for (int e = 0; e < intf.getEndpointCount(); e++) {
            UsbEndpoint ep = intf.getEndpoint(e);
            if (ep != null
                    && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                return ep;
            }
        }
        return null;
    }

    private static void sendAsync(UsbManager usbManager, PrinterTarget target, byte[] payload, PrintCallback callback) {
        EXECUTOR.execute(() -> {
            UsbDeviceConnection conn = null;
            try {
                conn = usbManager.openDevice(target.device);
                if (conn == null) {
                    postError(callback, "Unable to open USB connection to " + target.getLabel());
                    return;
                }
                if (!conn.claimInterface(target.usbInterface, true)) {
                    postError(callback, "Failed to claim USB printer interface.");
                    return;
                }
                int chunk = Math.max(64, Math.min(4096, target.outEndpoint.getMaxPacketSize() * 16));
                int offset = 0;
                while (offset < payload.length) {
                    int len = Math.min(chunk, payload.length - offset);
                    byte[] slice = new byte[len];
                    System.arraycopy(payload, offset, slice, 0, len);
                    int written = conn.bulkTransfer(target.outEndpoint, slice, len, WRITE_TIMEOUT_MS);
                    if (written < 0) {
                        postError(callback, "USB write timeout/failure at byte " + offset);
                        return;
                    }
                    offset += written;
                }
                MAIN.post(() -> {
                    if (callback != null) callback.onSuccess(target.getLabel());
                });
            } catch (Exception e) {
                postError(callback, e.getMessage() != null ? e.getMessage() : "USB print failed");
            } finally {
                if (conn != null) {
                    try {
                        conn.releaseInterface(target.usbInterface);
                    } catch (Exception ignored) {
                    }
                    try {
                        conn.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }

    private static void postError(PrintCallback callback, String msg) {
        MAIN.post(() -> {
            if (callback != null) callback.onError(msg);
        });
    }
}
