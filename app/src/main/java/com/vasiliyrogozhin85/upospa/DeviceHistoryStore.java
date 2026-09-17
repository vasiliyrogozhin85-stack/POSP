package com.vasiliyrogozhin85.upospa;
import android.content.Context;import org.json.*;import java.io.*;import java.text.*;import java.util.*;
final class DeviceHistoryStore{
 private final File file; DeviceHistoryStore(Context c){file=new File(c.getFilesDir(),"device_history.json");}
 synchronized JSONArray load(){try{if(!file.isFile())return new JSONArray();byte[]b=new byte[(int)file.length()];try(FileInputStream i=new FileInputStream(file)){i.read(b);}return new JSONArray(new String(b,"UTF-8"));}catch(Exception e){return new JSONArray();}}
 synchronized void add(JSONObject report,String source){try{JSONArray a=load();JSONObject d=report.optJSONObject("device");JSONObject x=new JSONObject();x.put("time",new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date()));x.put("source",source);x.put("manufacturer",d==null?"":d.optString("manufacturer"));x.put("model",d==null?"":d.optString("model"));x.put("device",d==null?"":d.optString("device"));x.put("android",report.optJSONObject("android")==null?"":report.optJSONObject("android").optString("release"));x.put("report",report);JSONArray n=new JSONArray();n.put(x);for(int i=0;i<a.length()&&i<29;i++)n.put(a.get(i));try(FileOutputStream o=new FileOutputStream(file)){o.write(n.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
 synchronized void clear(){try{if(file.exists())file.delete();}catch(Exception ignored){}}
}
