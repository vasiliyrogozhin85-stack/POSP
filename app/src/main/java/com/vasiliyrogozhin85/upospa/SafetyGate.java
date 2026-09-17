package com.vasiliyrogozhin85.upospa;
import org.json.*;
final class SafetyGate{
 static JSONObject evaluate(JSONObject report,JSONObject firmware){JSONObject r=new JSONObject();try{int battery=report.optJSONObject("battery")==null?-1:report.optJSONObject("battery").optInt("capacity_percent",-1);JSONObject p=report.optJSONObject("system_properties");String locked=p==null?"":p.optString("ro.boot.flash.locked");boolean unlocked="0".equals(locked)||"unlocked".equalsIgnoreCase(p==null?"":p.optString("ro.boot.vbmeta.device_state"));r.put("battery_ok",battery<0||battery>=40);r.put("bootloader_unlocked",unlocked);r.put("firmware_selected",firmware!=null&&firmware.length()>0);r.put("allow_destructive",(battery<0||battery>=40)&&unlocked&&firmware!=null&&firmware.length()>0);r.put("reason",r.optBoolean("allow_destructive")?"Критические базовые проверки пройдены.":"Запись заблокирована до выполнения всех обязательных проверок.");}catch(Exception ignored){}return r;}
 private SafetyGate(){}
}
