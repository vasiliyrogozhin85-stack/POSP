package com.vasiliyrogozhin85.upospa;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;

final class UsbModeDetector {
    static boolean hasAdb(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getInterfaceClass() == 255 &&
                x.getInterfaceSubclass() == 66 &&
                x.getInterfaceProtocol() == 1) return true;
        }
        return false;
    }

    static boolean hasFastboot(UsbDevice d) {
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface x = d.getInterface(i);
            if (x.getInterfaceClass() == 255 &&
                x.getInterfaceSubclass() == 66 &&
                x.getInterfaceProtocol() == 3) return true;
        }
        return false;
    }

    static boolean isAccessory(UsbDevice d) {
        int v = d.getVendorId(), p = d.getProductId();
        return v == 0x18D1 && p >= 0x2D00 && p <= 0x2D05;
    }

    static String describe(UsbDevice d) {
        StringBuilder s = new StringBuilder();
        if (hasAdb(d)) s.append("ADB ");
        if (hasFastboot(d)) s.append("FASTBOOT ");
        if (isAccessory(d)) s.append("ACCESSORY ");
        if (s.length() == 0) s.append("USB");
        return s.toString().trim();
    }

    private UsbModeDetector() {}
}
