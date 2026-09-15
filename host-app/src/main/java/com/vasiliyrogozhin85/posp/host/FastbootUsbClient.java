package com.vasiliyrogozhin85.posp.host;
import android.hardware.usb.*;
import java.io.*;import java.nio.charset.StandardCharsets;
public final class FastbootUsbClient implements Closeable{
 private final UsbDeviceConnection c; private final UsbInterface intf; private final UsbEndpoint in,out;
 private FastbootUsbClient(UsbDeviceConnection c,UsbInterface i,UsbEndpoint in,UsbEndpoint out){this.c=c;this.intf=i;this.in=in;this.out=out;}
 public static FastbootUsbClient open(UsbManager m,UsbDevice d)throws IOException{
  UsbInterface fi=null;UsbEndpoint in=null,out=null;
  for(int i=0;i<d.getInterfaceCount();i++){UsbInterface x=d.getInterface(i);if(x.getInterfaceClass()==255&&x.getInterfaceSubclass()==66&&x.getInterfaceProtocol()==3){fi=x;break;}}
  if(fi==null)throw new IOException("Fastboot interface not found");
  for(int i=0;i<fi.getEndpointCount();i++){UsbEndpoint e=fi.getEndpoint(i);if(e.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK){if(e.getDirection()==UsbConstants.USB_DIR_IN)in=e;else out=e;}}
  UsbDeviceConnection c=m.openDevice(d);if(c==null)throw new IOException("openDevice=null");if(!c.claimInterface(fi,true)){c.close();throw new IOException("claimInterface failed");}
  if(in==null||out==null){c.close();throw new IOException("Fastboot endpoints missing");}return new FastbootUsbClient(c,fi,in,out);
 }
 public synchronized String command(String cmd)throws IOException{
  byte[] q=cmd.getBytes(StandardCharsets.US_ASCII);int w=c.bulkTransfer(out,q,q.length,4000);if(w!=q.length)throw new IOException("fastboot write "+w+"/"+q.length);
  StringBuilder all=new StringBuilder();byte[] b=new byte[4096];long until=System.currentTimeMillis()+7000;
  while(System.currentTimeMillis()<until){int n=c.bulkTransfer(in,b,b.length,1200);if(n<=0)continue;String s=new String(b,0,n,StandardCharsets.UTF_8);if(s.startsWith("INFO")){all.append(s.substring(4)).append('\n');continue;}if(s.startsWith("OKAY")){all.append(s.substring(4));return all.toString();}if(s.startsWith("FAIL"))throw new IOException("fastboot FAIL: "+s.substring(4));if(s.startsWith("DATA"))throw new IOException("Unexpected DATA response");all.append(s);}
  throw new IOException("Fastboot timeout: "+cmd);
 }
 public String getvar(String name)throws IOException{return command("getvar:"+name);}
 public void close(){try{c.releaseInterface(intf);}catch(Exception ignored){}c.close();}
}
