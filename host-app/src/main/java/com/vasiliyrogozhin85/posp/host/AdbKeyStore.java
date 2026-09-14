package com.vasiliyrogozhin85.posp.host;
import android.content.Context;
import android.util.Base64;
import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

final class AdbKeyStore {
    private static final String PRIV="adb_private.pk8", PUB="adb_public.der";
    private static final byte[] SHA1_PREFIX=hex("3021300906052b0e03021a05000414");
    private final KeyPair pair;
    private AdbKeyStore(KeyPair p){pair=p;}
    static AdbKeyStore loadOrCreate(Context c)throws Exception{
        File prf=new File(c.getFilesDir(),PRIV), puf=new File(c.getFilesDir(),PUB);
        KeyFactory kf=KeyFactory.getInstance("RSA");
        if(prf.isFile()&&puf.isFile()){
            PrivateKey pr=kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(prf)));
            PublicKey pu=kf.generatePublic(new X509EncodedKeySpec(readAll(puf)));
            return new AdbKeyStore(new KeyPair(pu,pr));
        }
        KeyPairGenerator g=KeyPairGenerator.getInstance("RSA"); g.initialize(2048);
        KeyPair p=g.generateKeyPair(); writeAll(prf,p.getPrivate().getEncoded()); writeAll(puf,p.getPublic().getEncoded());
        return new AdbKeyStore(p);
    }
    byte[] signAdbToken(byte[] token)throws Exception{
        RSAPrivateCrtKey k=(RSAPrivateCrtKey)pair.getPrivate();
        int len=(k.getModulus().bitLength()+7)/8;
        byte[] di=concat(SHA1_PREFIX,token); int ps=len-di.length-3;
        byte[] em=new byte[len]; em[0]=0; em[1]=1;
        for(int i=0;i<ps;i++) em[2+i]=(byte)0xff;
        em[2+ps]=0; System.arraycopy(di,0,em,3+ps,di.length);
        return toFixed(new BigInteger(1,em).modPow(k.getPrivateExponent(),k.getModulus()),len);
    }
    byte[] adbPublicKeyPayload(){
        RSAPublicKey k=(RSAPublicKey)pair.getPublic(); BigInteger n=k.getModulus();
        int words=64; byte[] st=new byte[4+4+words*4+words*4+4]; int off=0;
        putLe32(st,off,words); off+=4;
        BigInteger two32=BigInteger.ONE.shiftLeft(32);
        long n0=n.and(two32.subtract(BigInteger.ONE)).longValue()&0xffffffffL;
        long inv=BigInteger.valueOf(n0).modInverse(two32).longValue()&0xffffffffL;
        putLe32(st,off,(-inv)&0xffffffffL); off+=4;
        BigInteger mask=two32.subtract(BigInteger.ONE);
        for(int i=0;i<words;i++){putLe32(st,off,n.shiftRight(i*32).and(mask).longValue());off+=4;}
        BigInteger rr=BigInteger.ONE.shiftLeft(words*32*2).mod(n);
        for(int i=0;i<words;i++){putLe32(st,off,rr.shiftRight(i*32).and(mask).longValue());off+=4;}
        putLe32(st,off,k.getPublicExponent().longValue());
        return (Base64.encodeToString(st,Base64.NO_WRAP)+" posphost@android\0").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    private static byte[] readAll(File f)throws IOException{try(FileInputStream i=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[]b=new byte[4096];int n;while((n=i.read(b))>0)o.write(b,0,n);return o.toByteArray();}}
    private static void writeAll(File f,byte[]d)throws IOException{try(FileOutputStream o=new FileOutputStream(f)){o.write(d);}}
    private static byte[] concat(byte[]a,byte[]b){byte[]r=new byte[a.length+b.length];System.arraycopy(a,0,r,0,a.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
    private static byte[] hex(String s){byte[]r=new byte[s.length()/2];for(int i=0;i<r.length;i++)r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return r;}
    private static byte[] toFixed(BigInteger v,int len){byte[]s=v.toByteArray(),d=new byte[len];int c=Math.min(s.length,len);System.arraycopy(s,s.length-c,d,len-c,c);return d;}
    private static void putLe32(byte[]a,int o,long v){a[o]=(byte)v;a[o+1]=(byte)(v>>>8);a[o+2]=(byte)(v>>>16);a[o+3]=(byte)(v>>>24);}
}
