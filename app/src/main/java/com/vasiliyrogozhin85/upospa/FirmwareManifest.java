package com.vasiliyrogozhin85.upospa;
import org.json.*;import java.io.*;import java.util.zip.*;
final class FirmwareManifest{
 static JSONObject fromZip(File f){JSONObject out=new JSONObject();try(ZipFile z=new ZipFile(f)){ZipEntry e=z.getEntry("upospa-manifest.json");if(e==null)return out;try(InputStream in=z.getInputStream(e);ByteArrayOutputStream b=new ByteArrayOutputStream()){byte[]x=new byte[8192];int n;while((n=in.read(x))>0)b.write(x,0,n);return new JSONObject(b.toString("UTF-8"));}}catch(Exception ignored){}return out;}
 static String validate(JSONObject m,JSONObject report){if(m==null||m.length()==0)return "В пакете нет upospa-manifest.json — используется эвристическая проверка.";JSONObject d=report.optJSONObject("device");String dev=d==null?"":d.optString("device");JSONArray supported=m.optJSONArray("devices");if(supported!=null&&supported.length()>0){boolean ok=false;for(int i=0;i<supported.length();i++)if(dev.equalsIgnoreCase(supported.optString(i)))ok=true;if(!ok)return "Манифест не содержит codename этого устройства: "+dev;}return "Манифест uPOSPa соответствует базовым требованиям.";}
 private FirmwareManifest(){}
}
