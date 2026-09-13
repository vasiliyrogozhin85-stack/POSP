package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.hardware.usb.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

final class AdbUsbClient implements Closeable {
    static final int A_SYNC=0x434e5953, A_CNXN=0x4e584e43, A_OPEN=0x4e45504f, A_OKAY=0x59414b4f,
            A_CLSE=0x45534c43, A_WRTE=0x45545257, A_AUTH=0x48545541;
    static final int AUTH_TOKEN=1, AUTH_SIGNATURE=2, AUTH_RSAPUBLICKEY=3;

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in, out;
    private final AdbKeyStore keys;
    private int remoteMax = 4096;
    private int nextLocal = 1;

    private AdbUsbClient(Context ctx, UsbDeviceConnection conn, UsbInterface intf, UsbEndpoint in, UsbEndpoint out) throws Exception {
        this.conn=conn; this.intf=intf; this.in=in; this.out=out; this.keys=AdbKeyStore.loadOrCreate(ctx);
    }

    static boolean isAdbInterface(UsbInterface i) { return i.getInterfaceClass()==255 && i.getInterfaceSubclass()==66 && i.getInterfaceProtocol()==1; }

    static AdbUsbClient open(Context ctx, UsbManager mgr, UsbDevice dev) throws Exception {
        UsbInterface found=null; UsbEndpoint epIn=null, epOut=null;
        for(int x=0;x<dev.getInterfaceCount();x++) {
            UsbInterface i=dev.getInterface(x); if(!isAdbInterface(i)) continue;
            for(int e=0;e<i.getEndpointCount();e++) {
                UsbEndpoint ep=i.getEndpoint(e);
                if(ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if(ep.getDirection()==UsbConstants.USB_DIR_IN) epIn=ep; else epOut=ep;
                }
            }
            if(epIn!=null && epOut!=null){ found=i; break; }
        }
        if(found==null) throw new IOException("ADB USB interface not found");
        UsbDeviceConnection c=mgr.openDevice(dev); if(c==null) throw new IOException("USB open failed / permission missing");
        if(!c.claimInterface(found,true)){ c.close(); throw new IOException("Cannot claim ADB USB interface"); }
        return new AdbUsbClient(ctx,c,found,epIn,epOut);
    }

    void connect() throws Exception {
        send(A_CNXN, 0x01000000, 4096, "host::POSPHost\0".getBytes(StandardCharsets.UTF_8));
        long deadline=System.currentTimeMillis()+90000;
        boolean sentPublic=false;
        while(System.currentTimeMillis()<deadline) {
            Packet p=readPacket(10000);
            if(p==null) continue;
            if(p.cmd==A_CNXN){ remoteMax=p.arg1>0?p.arg1:4096; return; }
            if(p.cmd==A_AUTH && p.arg0==AUTH_TOKEN) {
                if(!sentPublic) {
                    send(A_AUTH,AUTH_SIGNATURE,0,keys.signAdbToken(p.data));
                    // If key is unknown, daemon will ask again; then send public key to trigger RSA dialog.
                    sentPublic=true;
                } else send(A_AUTH,AUTH_RSAPUBLICKEY,0,keys.adbPublicKeyPayload());
            }
        }
        throw new IOException("ADB authorization timeout. Approve the RSA dialog on target phone.");
    }

    String shell(String command, int timeoutMs) throws Exception {
        int local=nextLocal++;
        byte[] service=("shell:"+command+"\0").getBytes(StandardCharsets.UTF_8);
        send(A_OPEN,local,0,service);
        int remote=0; ByteArrayOutputStream data=new ByteArrayOutputStream();
        long deadline=System.currentTimeMillis()+timeoutMs;
        while(System.currentTimeMillis()<deadline) {
            Packet p=readPacket(Math.min(5000, Math.max(500,(int)(deadline-System.currentTimeMillis()))));
            if(p==null) continue;
            if(p.cmd==A_OKAY && p.arg1==local) { remote=p.arg0; continue; }
            if(p.cmd==A_WRTE && p.arg1==local) {
                remote=p.arg0; data.write(p.data); send(A_OKAY,local,remote,new byte[0]); continue;
            }
            if(p.cmd==A_CLSE && p.arg1==local) {
                if(remote==0) remote=p.arg0; send(A_CLSE,local,remote,new byte[0]);
                return data.toString("UTF-8");
            }
        }
        if(remote!=0) send(A_CLSE,local,remote,new byte[0]);
        throw new IOException("ADB shell timeout: "+command);
    }

    private void send(int cmd,int arg0,int arg1,byte[] data) throws IOException {
        if(data==null)data=new byte[0];
        int sum=0; for(byte b:data) sum+=(b&0xff);
        ByteBuffer h=ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.length).putInt(sum).putInt(cmd^0xffffffff);
        writeFully(h.array(),10000); if(data.length>0) writeFully(data,10000);
    }

    private Packet readPacket(int timeout) throws IOException {
        byte[] h=readFully(24,timeout); if(h==null)return null;
        ByteBuffer b=ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int cmd=b.getInt(), a0=b.getInt(), a1=b.getInt(), len=b.getInt(), chk=b.getInt(), magic=b.getInt();
        if((cmd^0xffffffff)!=magic) throw new IOException("Bad ADB magic");
        if(len<0 || len>1024*1024) throw new IOException("Bad ADB payload size "+len);
        byte[] d=len==0?new byte[0]:readFully(len,timeout); if(d==null) throw new IOException("ADB payload timeout");
        int sum=0; for(byte x:d)sum+=(x&0xff); if(sum!=chk) throw new IOException("Bad ADB checksum");
        return new Packet(cmd,a0,a1,d);
    }

    private void writeFully(byte[] b,int timeout)throws IOException { int n=conn.bulkTransfer(out,b,b.length,timeout); if(n!=b.length) throw new IOException("USB write failed: "+n+"/"+b.length); }
    private byte[] readFully(int len,int timeout)throws IOException {
        byte[] all=new byte[len]; int off=0; long end=System.currentTimeMillis()+timeout;
        while(off<len) {
            int remain=len-off; byte[] tmp=new byte[remain]; int t=(int)Math.max(1,end-System.currentTimeMillis());
            int n=conn.bulkTransfer(in,tmp,remain,t); if(n<=0){ if(off==0)return null; throw new IOException("USB read timeout"); }
            System.arraycopy(tmp,0,all,off,n); off+=n;
        }
        return all;
    }

    @Override public void close(){ try{conn.releaseInterface(intf);}catch(Exception ignored){} conn.close(); }
    private static final class Packet { final int cmd,arg0,arg1; final byte[] data; Packet(int c,int a,int b,byte[]d){cmd=c;arg0=a;arg1=b;data=d;} }
}
