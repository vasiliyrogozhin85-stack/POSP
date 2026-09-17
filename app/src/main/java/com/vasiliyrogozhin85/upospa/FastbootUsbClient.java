package com.vasiliyrogozhin85.upospa;

import android.hardware.usb.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class FastbootUsbClient implements Closeable {
    interface ProgressListener { void onProgress(long done,long total,String phase); }

    static final class PartitionEntry {
        final String name,size,type;
        PartitionEntry(String n,String s,String t){name=n;size=s;type=t;}
    }

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in,out;

    private FastbootUsbClient(UsbDeviceConnection c, UsbInterface i, UsbEndpoint in, UsbEndpoint out) {
        conn=c; intf=i; this.in=in; this.out=out;
    }

    static FastbootUsbClient open(UsbManager m, UsbDevice d) throws Exception {
        UsbInterface fi=null; UsbEndpoint ei=null,eo=null;
        for(int i=0;i<d.getInterfaceCount();i++){
            UsbInterface x=d.getInterface(i);
            if(x.getInterfaceClass()!=255 || x.getInterfaceSubclass()!=66 || x.getInterfaceProtocol()!=3) continue;
            for(int e=0;e<x.getEndpointCount();e++){
                UsbEndpoint ep=x.getEndpoint(e);
                if(ep.getType()!=UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if(ep.getDirection()==UsbConstants.USB_DIR_IN) ei=ep; else eo=ep;
            }
            if(ei!=null&&eo!=null){fi=x;break;}
        }
        if(fi==null) throw new IOException("Fastboot interface not found");
        UsbDeviceConnection c=m.openDevice(d);
        if(c==null) throw new IOException("openDevice=null");
        if(!c.claimInterface(fi,true)){c.close();throw new IOException("Cannot claim Fastboot interface");}
        return new FastbootUsbClient(c,fi,ei,eo);
    }

    String command(String cmd, int timeout) throws Exception {
        writeAscii(cmd,3000);
        return readTerminal(timeout,false,null);
    }

    String getVar(String name) {
        try { return command("getvar:"+name,3500).trim(); }
        catch(Exception e){ return "—"; }
    }

    Map<String,String> querySummary() {
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        String[] vars={"product","unlocked","secure","current-slot","slot-count","is-userspace",
                "version-baseband","version-bootloader","max-download-size","serialno"};
        for(String v:vars)m.put(v,getVar(v));
        return m;
    }

    Map<String,String> querySlotState(){
        LinkedHashMap<String,String> m=new LinkedHashMap<>();
        m.put("current-slot",getVar("current-slot"));
        m.put("slot-count",getVar("slot-count"));
        for(String slot:new String[]{"a","b"}){
            m.put("slot-successful:"+slot,getVar("slot-successful:"+slot));
            m.put("slot-unbootable:"+slot,getVar("slot-unbootable:"+slot));
            m.put("slot-retry-count:"+slot,getVar("slot-retry-count:"+slot));
        }
        return m;
    }

    List<PartitionEntry> queryPartitionMap(){
        ArrayList<PartitionEntry> out=new ArrayList<>();
        String[] names={"boot","boot_a","boot_b","init_boot","init_boot_a","init_boot_b",
                "vendor_boot","vendor_boot_a","vendor_boot_b","dtbo","dtbo_a","dtbo_b",
                "vbmeta","vbmeta_a","vbmeta_b","vbmeta_system","vbmeta_vendor","recovery",
                "super","system","system_a","system_b","system_ext","vendor","vendor_a","vendor_b",
                "product","odm","vendor_dlkm","odm_dlkm","system_dlkm","metadata","userdata"};
        for(String n:names){
            String size=getVar("partition-size:"+n);
            if(size==null||size.isEmpty()||"—".equals(size))continue;
            String type=getVar("partition-type:"+n);
            out.add(new PartitionEntry(n,size,type));
        }
        return out;
    }

    void setActive(String slot) throws Exception {
        if(!"a".equals(slot)&&!"b".equals(slot))throw new IllegalArgumentException("slot must be a or b");
        command("set_active:"+slot,10000);
    }

    void reboot() throws Exception { command("reboot",10000); }

    void rebootFastbootd() throws Exception {
        writeAscii("reboot-fastboot",3000);
        try { readTerminal(10000,false,null); }
        catch(IOException e) {
            String m=e.getMessage()==null?"":e.getMessage();
            // Many bootloaders disconnect immediately after accepting reboot-fastboot.
            // Preserve explicit FAIL replies, tolerate a transport timeout/disconnect.
            if(m.startsWith("Fastboot:")) throw e;
        }
    }

    void flash(InputStream source,long size,String partition,ProgressListener listener) throws Exception {
        if(source==null)throw new IOException("source=null");
        if(partition==null||partition.trim().isEmpty())throw new IOException("partition is empty");
        if(size<=0)throw new IOException("unknown image size");
        if(size>0xffffffffL)throw new IOException("image > 4 GiB is not supported by uPOSPa v0.8 beta direct flasher");

        if(listener!=null)listener.onProgress(0,size,"download handshake");
        writeAscii(String.format(Locale.US,"download:%08x",size),3000);
        String data=readDataReply(10000);
        long accepted=parseHex(data);
        if(accepted>0 && accepted<size)throw new IOException("Fastboot accepted only "+accepted+" bytes of "+size);

        byte[] b=new byte[16*1024];
        long done=0;
        while(done<size){
            int want=(int)Math.min(b.length,size-done);
            int n=readFullySome(source,b,want);
            if(n<=0)throw new EOFException("image ended at "+done+" of "+size);
            writeBytes(b,n,15000);
            done+=n;
            if(listener!=null)listener.onProgress(done,size,"sending image");
        }
        readTerminal(30000,false,listener);
        if(listener!=null)listener.onProgress(size,size,"flashing "+partition);
        command("flash:"+partition,180000);
        if(listener!=null)listener.onProgress(size,size,"done");
    }

    private String readDataReply(int timeout) throws Exception {
        long end=System.currentTimeMillis()+timeout;
        StringBuilder info=new StringBuilder();
        while(System.currentTimeMillis()<end){
            String s=readPacket((int)Math.max(1,end-System.currentTimeMillis()));
            if(s==null)continue;
            if(s.startsWith("INFO")){if(info.length()>0)info.append('\n');info.append(s.substring(4));continue;}
            if(s.startsWith("DATA"))return s.substring(4).trim();
            if(s.startsWith("FAIL"))throw new IOException("Fastboot: "+s.substring(4));
            if(s.startsWith("OKAY"))throw new IOException("Fastboot returned OKAY instead of DATA");
        }
        throw new IOException("Fastboot download handshake timeout");
    }

    private String readTerminal(int timeout,boolean allowData,ProgressListener listener) throws Exception {
        StringBuilder info=new StringBuilder();
        long end=System.currentTimeMillis()+timeout;
        while(System.currentTimeMillis()<end){
            String s=readPacket((int)Math.max(1,end-System.currentTimeMillis()));
            if(s==null)continue;
            if(s.startsWith("INFO")){
                if(info.length()>0) info.append('\n'); info.append(s.substring(4));
                continue;
            }
            if(s.startsWith("OKAY")){
                String tail=s.substring(4);
                if(!tail.isEmpty()){if(info.length()>0)info.append('\n');info.append(tail);}
                return info.toString();
            }
            if(s.startsWith("FAIL"))throw new IOException("Fastboot: "+s.substring(4));
            if(s.startsWith("DATA")&&allowData)return s.substring(4);
            if(info.length()>0)info.append('\n');
            info.append(s);
        }
        throw new IOException("Fastboot timeout");
    }

    private String readPacket(int timeout){
        byte[] b=new byte[4096];
        int r=conn.bulkTransfer(in,b,b.length,timeout);
        if(r<=0)return null;
        return new String(b,0,r,StandardCharsets.UTF_8);
    }

    private void writeAscii(String s,int timeout)throws IOException{
        byte[] q=s.getBytes(StandardCharsets.US_ASCII);
        writeBytes(q,q.length,timeout);
    }

    private void writeBytes(byte[] b,int len,int timeout)throws IOException{
        int n=conn.bulkTransfer(out,b,len,timeout);
        if(n!=len)throw new IOException("Fastboot USB write "+n+"/"+len);
    }

    private static int readFullySome(InputStream in,byte[] b,int want)throws IOException{
        int off=0;
        while(off<want){
            int n=in.read(b,off,want-off);
            if(n<0)break;
            if(n==0)continue;
            off+=n;
        }
        return off;
    }

    private static long parseHex(String s){
        try{
            s=s.trim().toLowerCase(Locale.US);
            if(s.startsWith("0x"))s=s.substring(2);
            return Long.parseLong(s,16);
        }catch(Exception e){return 0;}
    }

    public void close(){
        try{conn.releaseInterface(intf);}catch(Exception ignored){}
        try{conn.close();}catch(Exception ignored){}
    }
}
