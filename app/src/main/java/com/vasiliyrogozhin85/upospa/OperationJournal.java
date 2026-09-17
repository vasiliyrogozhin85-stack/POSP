package com.vasiliyrogozhin85.upospa;
import android.content.Context;import org.json.*;import java.io.*;import java.text.*;import java.util.*;
final class OperationJournal{
 private final File f; OperationJournal(Context c){File d=new File(c.getFilesDir(),"operations");d.mkdirs();f=new File(d,"operations.jsonl");}
 synchronized void add(String type,String status,String detail){try{JSONObject j=new JSONObject();j.put("time",new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX",Locale.US).format(new Date()));j.put("type",type);j.put("status",status);j.put("detail",detail);try(FileWriter w=new FileWriter(f,true)){w.write(j.toString()+"\n");}}catch(Exception ignored){}}
 File file(){return f;}
}
