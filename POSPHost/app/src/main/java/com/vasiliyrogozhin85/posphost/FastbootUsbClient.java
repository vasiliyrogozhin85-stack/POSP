package com.vasiliyrogozhin85.posphost;

import android.hardware.usb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class FastbootUsbClient implements Closeable {
    private final UsbDeviceConnection conn; private final UsbInterface intf; private final UsbEndpoint in,out;
    private FastbootUsbClient(UsbDeviceConnection c,UsbInterface i,UsbEndpoint in,UsbEndpoint out){this.conn=c;this.intf=i;this.in=in;this.out=out;}
    static boolean isFastbootInterface(UsbInterface i){ return i.getInterfaceClass()==255 && i.getInterfaceSubclass()==66 && i.getInterfaceProtocol()==3; }
    static FastbootUsbClient open(UsbManager mgr,UsbDevice dev)throws Exception{
        UsbInterface f=null;UsbEndpoint ei=null,eo=null;
        for(int x=0;x<dev.getInterfaceCount();x++){UsbInterface i=dev.getInterface(x); if(!isFastbootInterface(i))continue; for(int e=0;e<i.getEndpointCount();e++){UsbEndpoint ep=i.getEndpoint(e);if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(ep.getDirection()==UsbConstants.USB_DIR_IN)ei=ep;else eo=ep;}} if(ei!=null&&eo!=null){f=i;break;}}
        if(f==null)throw new IOException("Fastboot USB interface not found"); UsbDeviceConnection c=mgr.openDevice(dev);if(c==null)throw new IOException("USB permission missing");if(!c.claimInterface(f,true)){c.close();throw new IOException("Cannot claim fastboot interface");}return new FastbootUsbClient(c,f,ei,eo);
    }
    String command(String cmd,int timeoutMs)throws IOException{
        byte[] q=cmd.getBytes(StandardCharsets.US_ASCII); if(q.length>64)throw new IOException("Fastboot command too long");
        int n=conn.bulkTransfer(out,q,q.length,10000); if(n!=q.length)throw new IOException("Fastboot write failed");
        StringBuilder info=new StringBuilder(); long end=System.currentTimeMillis()+timeoutMs;
        while(System.currentTimeMillis()<end){byte[] b=new byte[4096];int t=(int)Math.max(1,end-System.currentTimeMillis());int r=conn.bulkTransfer(in,b,b.length,t);if(r<=0)continue;String s=new String(b,0,r,StandardCharsets.UTF_8);if(s.startsWith("INFO")){info.append(s.substring(4)).append('\n');continue;}if(s.startsWith("OKAY"))return info+"OKAY "+s.substring(4);if(s.startsWith("FAIL"))return info+"FAIL "+s.substring(4);if(s.startsWith("DATA"))return info+"DATA "+s.substring(4);info.append(s).append('\n');}
        throw new IOException("Fastboot timeout: "+cmd);
    }
    String getVar(String name)throws IOException{return command("getvar:"+name,15000);}
    @Override public void close(){try{conn.releaseInterface(intf);}catch(Exception ignored){}conn.close();}
}
