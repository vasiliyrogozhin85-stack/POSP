package com.vasiliyrogozhin85.posphost;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

final class FirmwarePackageValidator {
    static final class Result {
        boolean valid;
        String message;
        String sha256;
        JSONObject manifest;
        int checkedFiles;
    }

    static Result validate(File zipFile, String expectedProduct, String expectedHardware, String expectedBoard) {
        Result r = new Result();
        try {
            r.sha256 = sha256(zipFile);
            try (ZipFile zf = new ZipFile(zipFile)) {
                ZipEntry entry = zf.getEntry("posp_manifest.json");
                if (entry == null) { r.valid=false; r.message="В пакете отсутствует posp_manifest.json"; return r; }
                String text = readText(zf, entry);
                JSONObject m = new JSONObject(text); r.manifest=m;
                if (!matchesAny(m, "product", expectedProduct) || !matchesAny(m, "hardware", expectedHardware) || !matchesAny(m, "board", expectedBoard)) {
                    r.valid=false;
                    r.message="Пакет не соответствует подключённому устройству: product="+m.opt("product")+", hardware="+m.opt("hardware")+", board="+m.opt("board");
                    return r;
                }
                JSONArray files = m.optJSONArray("files");
                if (files != null) {
                    for (int i=0;i<files.length();i++) {
                        JSONObject f=files.optJSONObject(i); if(f==null) continue;
                        String path=f.optString("path"); String expected=f.optString("sha256");
                        if(path.isEmpty()||expected.isEmpty()) { r.valid=false; r.message="Некорректная запись files["+i+"] в manifest"; return r; }
                        ZipEntry ze=zf.getEntry(path); if(ze==null) { r.valid=false; r.message="В пакете отсутствует файл "+path; return r; }
                        String actual=sha256(zf.getInputStream(ze)); r.checkedFiles++;
                        if(!expected.equalsIgnoreCase(actual)) { r.valid=false; r.message="SHA-256 не совпадает для "+path; return r; }
                    }
                }
                r.valid=true;
                r.message="Пакет совместим. Проверено файлов по manifest: "+r.checkedFiles+". Автоматическая прошивка критических разделов отключена.";
            }
        } catch (Exception e) { r.valid=false; r.message="Ошибка проверки: "+e.getMessage(); }
        return r;
    }

    private static boolean matchesAny(JSONObject m,String key,String actual){
        if(actual==null||actual.isEmpty()) return false;
        Object v=m.opt(key);
        if(v instanceof JSONArray){JSONArray a=(JSONArray)v;for(int i=0;i<a.length();i++)if(actual.equalsIgnoreCase(a.optString(i)))return true;return false;}
        return actual.equalsIgnoreCase(m.optString(key));
    }
    private static String readText(ZipFile zf,ZipEntry e)throws Exception{try(InputStream in=zf.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[]b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());}}
    static String sha256(File f)throws Exception{try(InputStream in=new FileInputStream(f)){return sha256(in);}}
    private static String sha256(InputStream in)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[]b=new byte[8192];int n;while((n=in.read(b))>0)md.update(b,0,n);StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.US,"%02x",x));return s.toString();}
}
