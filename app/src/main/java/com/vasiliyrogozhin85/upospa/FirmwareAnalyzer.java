package com.vasiliyrogozhin85.upospa;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class FirmwareAnalyzer {
    static final class Result {
        String name="";
        long size;
        String sha256="";
        final List<String> interesting = new ArrayList<>();
        final List<String> expectedDevices = new ArrayList<>();
        String type="Файл";
        String summary="";
        String otaType="";
        String postBuild="";
        String securityPatch="";
        String inferredPartition="";
        boolean directImage;
        boolean sparseImage;
        String imageMagic="";
    }

    static Result analyze(Context c, Uri uri) throws Exception {
        Result r=new Result();
        r.name=displayName(c,uri);
        r.size=size(c,uri);
        r.sha256=sha256(c,uri);
        String n=r.name.toLowerCase(Locale.US);
        if(n.endsWith(".zip")) {
            r.type="ZIP/OTA";
            scanZip(c,uri,r);
        } else if(n.endsWith(".img")) {
            r.type="Образ раздела";
            r.directImage=true;
            r.inferredPartition=inferPartition(r.name);
            inspectImage(c,uri,r);
            r.interesting.add(r.name);
        } else if(n.endsWith(".bin")) {
            r.type="BIN/Payload";
            if(n.endsWith("payload.bin"))r.interesting.add("payload.bin");
        }
        if(r.directImage) {
            r.summary="Прямой образ"+(r.inferredPartition.isEmpty()?"":" раздела "+r.inferredPartition)+
                    (r.sparseImage?" • Android sparse":"")+
                    (r.imageMagic.isEmpty()?"":" • "+r.imageMagic);
        } else if(!r.interesting.isEmpty()) {
            r.summary="Найдены компоненты: "+join(r.interesting, ", ");
        } else {
            r.summary="Контрольная сумма рассчитана. Содержимое пакета требует ручной проверки.";
        }
        return r;
    }

    private static void scanZip(Context c,Uri u,Result r) throws Exception {
        try(InputStream raw=c.getContentResolver().openInputStream(u)){
            if(raw==null)throw new IOException("openInputStream=null");
            try(ZipInputStream z=new ZipInputStream(new BufferedInputStream(raw))){
                ZipEntry e; int count=0;
                String[] keys={"boot.img","vendor_boot.img","init_boot.img","vbmeta.img","dtbo.img",
                        "recovery.img","super.img","payload.bin","META-INF/com/android/metadata"};
                while((e=z.getNextEntry())!=null && count<10000){
                    String name=e.getName(); count++;
                    for(String k:keys) if(name.endsWith(k) && !r.interesting.contains(k)) r.interesting.add(k);
                    if(name.endsWith("META-INF/com/android/metadata")) parseMetadata(z,r);
                }
            }
        }
    }

    private static void parseMetadata(InputStream in, Result r) throws IOException {
        ByteArrayOutputStream o=new ByteArrayOutputStream();
        byte[] b=new byte[4096]; int n,total=0;
        while((n=in.read(b))>0 && total<256*1024){o.write(b,0,n);total+=n;}
        String s=o.toString("UTF-8");
        for(String line:s.split("\\r?\\n")){
            int p=line.indexOf('='); if(p<=0)continue;
            String k=line.substring(0,p).trim(),v=line.substring(p+1).trim();
            if("pre-device".equals(k)){
                for(String x:v.split("\\|"))if(!x.trim().isEmpty()&&!r.expectedDevices.contains(x.trim()))r.expectedDevices.add(x.trim());
            } else if("ota-type".equals(k)) r.otaType=v;
            else if("post-build".equals(k)) r.postBuild=v;
            else if("post-security-patch-level".equals(k)) r.securityPatch=v;
        }
    }

    private static void inspectImage(Context c,Uri u,Result r){
        try(InputStream in=c.getContentResolver().openInputStream(u)){
            if(in==null)return;
            byte[] h=new byte[64]; int n=in.read(h);
            if(n>=4){
                long le=(h[0]&255L)|((h[1]&255L)<<8)|((h[2]&255L)<<16)|((h[3]&255L)<<24);
                r.sparseImage=le==0xed26ff3aL;
            }
            if(n>=8){
                String a=new String(h,0,8,"US-ASCII");
                if(a.startsWith("ANDROID!"))r.imageMagic="Android boot image";
                else if(a.startsWith("VNDRBOOT"))r.imageMagic="Vendor boot image";
                else if(a.startsWith("AVB0"))r.imageMagic="AVB image";
            }
        }catch(Exception ignored){}
    }

    static String inferPartition(String name){
        String n=name.toLowerCase(Locale.US);
        if(n.endsWith(".img"))n=n.substring(0,n.length()-4);
        int slash=Math.max(n.lastIndexOf('/'),n.lastIndexOf('\\'));
        if(slash>=0)n=n.substring(slash+1);
        String[] known={"boot","vendor_boot","init_boot","vbmeta","vbmeta_system","vbmeta_vendor","dtbo","recovery","super","system","system_ext","vendor","product","odm","vendor_dlkm","odm_dlkm","system_dlkm","userdata"};
        for(String k:known)if(n.equals(k)||n.endsWith("_"+k))return k;
        return "";
    }

    private static String sha256(Context c, Uri u) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=c.getContentResolver().openInputStream(u)){
            if(in==null) throw new IOException("openInputStream=null");
            byte[] b=new byte[1024*1024]; int n;
            while((n=in.read(b))>0) md.update(b,0,n);
        }
        StringBuilder s=new StringBuilder();
        for(byte x:md.digest()) s.append(String.format(Locale.US,"%02x",x&255));
        return s.toString();
    }

    private static String displayName(Context c,Uri u){
        try(Cursor cur=c.getContentResolver().query(u,null,null,null,null)){
            if(cur!=null&&cur.moveToFirst()){
                int i=cur.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(i>=0)return cur.getString(i);
            }
        }catch(Exception ignored){}
        return u.getLastPathSegment()==null?"package":u.getLastPathSegment();
    }
    private static long size(Context c,Uri u){
        try(Cursor cur=c.getContentResolver().query(u,null,null,null,null)){
            if(cur!=null&&cur.moveToFirst()){
                int i=cur.getColumnIndex(OpenableColumns.SIZE);
                if(i>=0&&!cur.isNull(i))return cur.getLong(i);
            }
        }catch(Exception ignored){}
        return 0;
    }
    private static String join(List<String> xs,String sep){
        StringBuilder s=new StringBuilder();
        for(String x:xs){if(s.length()>0)s.append(sep);s.append(x);}
        return s.toString();
    }
    private FirmwareAnalyzer(){}
}
