package com.vasiliyrogozhin85.upospa;
import android.content.Context;import org.json.*;import java.io.*;
final class SessionStore{
 private final File file; SessionStore(Context c){file=new File(c.getFilesDir(),"active_session.json");}
 synchronized JSONObject load(){try{if(!file.isFile())return new JSONObject();byte[]b=new byte[(int)file.length()];try(FileInputStream i=new FileInputStream(file)){i.read(b);}return new JSONObject(new String(b,"UTF-8"));}catch(Exception e){return new JSONObject();}}
 synchronized void put(String k,Object v){try{JSONObject j=load();j.put(k,v);try(FileOutputStream o=new FileOutputStream(file)){o.write(j.toString(2).getBytes("UTF-8"));}}catch(Exception ignored){}}
 synchronized void clear(){if(file.exists())file.delete();}
}
