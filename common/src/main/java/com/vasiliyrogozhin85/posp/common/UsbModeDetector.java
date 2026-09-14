package com.vasiliyrogozhin85.posp.common;
import android.hardware.usb.*;
public final class UsbModeDetector {
    public static boolean hasAdb(UsbDevice d){
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface x=d.getInterface(i);
            if(x.getInterfaceClass()==255 && x.getInterfaceSubclass()==66 && x.getInterfaceProtocol()==1) return true;
        }
        return false;
    }
    public static boolean hasFastboot(UsbDevice d){
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface x=d.getInterface(i);
            if(x.getInterfaceClass()==255 && x.getInterfaceSubclass()==66 && x.getInterfaceProtocol()==3) return true;
        }
        return false;
    }
    public static boolean isAccessory(UsbDevice d){
        int v=d.getVendorId(), p=d.getProductId();
        return v==0x18D1 && p>=0x2D00 && p<=0x2D05;
    }
    public static String describe(UsbDevice d){
        StringBuilder s=new StringBuilder();
        if(hasAdb(d)) s.append("ADB ");
        if(hasFastboot(d)) s.append("FASTBOOT ");
        if(isAccessory(d)) s.append("ACCESSORY ");
        return s.length()==0 ? "UNKNOWN" : s.toString().trim();
    }
    private UsbModeDetector(){}
}
