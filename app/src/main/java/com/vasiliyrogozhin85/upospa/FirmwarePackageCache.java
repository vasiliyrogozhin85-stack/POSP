package com.vasiliyrogozhin85.upospa;
import android.content.Context;import android.net.Uri;import java.io.*;
final class FirmwarePackageCache{
 static File copyUriToCache(Context c,Uri u,String name)throws Exception{File d=new File(c.getCacheDir(),"firmware");d.mkdirs();String safe=name==null?"package.bin":name.replaceAll("[^A-Za-z0-9._-]","_");File f=new File(d,safe);try(InputStream in=c.getContentResolver().openInputStream(u);OutputStream out=new FileOutputStream(f)){if(in==null)throw new IOException("openInputStream=null");byte[]b=new byte[1024*1024];int n;long total=0;while((n=in.read(b))>0){out.write(b,0,n);total+=n;if(total>12L*1024*1024*1024)throw new IOException("package too large");}}return f;}
 private FirmwarePackageCache(){}
}
