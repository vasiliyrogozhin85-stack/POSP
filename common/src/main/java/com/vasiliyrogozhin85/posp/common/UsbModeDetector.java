package com.vasiliyrogozhin85.posp.common;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;

public final class UsbModeDetector {
    public enum Mode {
        ADB, FASTBOOT, ACCESSORY, UNKNOWN
    }

    private UsbModeDetector() {}

    public static Mode detect(UsbDevice device) {
        int vendor = device.getVendorId();
        int product = device.getProductId();
        if (vendor == 0x18D1 && product >= 0x2D00 && product <= 0x2D05) {
            return Mode.ACCESSORY;
        }

        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 0xFF &&
                intf.getInterfaceSubclass() == 0x42 &&
                intf.getInterfaceProtocol() == 0x01) {
                return Mode.ADB;
            }
            if (intf.getInterfaceClass() == 0xFF &&
                intf.getInterfaceSubclass() == 0x42 &&
                intf.getInterfaceProtocol() == 0x03) {
                return Mode.FASTBOOT;
            }
        }
        return Mode.UNKNOWN;
    }
}
