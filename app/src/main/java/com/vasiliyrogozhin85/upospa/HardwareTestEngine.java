package com.vasiliyrogozhin85.upospa;
import android.content.*;import android.content.pm.*;import android.hardware.*;import android.os.*;import org.json.*;
final class HardwareTestEngine{
 static JSONObject quick(Context c){JSONObject j=new JSONObject();try{PackageManager p=c.getPackageManager();j.put("camera",p.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY));j.put("gps",p.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS));j.put("bluetooth",p.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH));j.put("wifi",p.hasSystemFeature(PackageManager.FEATURE_WIFI));j.put("nfc",p.hasSystemFeature(PackageManager.FEATURE_NFC));SensorManager sm=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);j.put("sensor_count",sm==null?0:sm.getSensorList(Sensor.TYPE_ALL).size());BatteryManager bm=(BatteryManager)c.getSystemService(Context.BATTERY_SERVICE);j.put("battery_percent",bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));j.put("charge_counter_uah",bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));j.put("current_now_ua",bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));}catch(Exception ignored){}return j;}
 private HardwareTestEngine(){}
}
