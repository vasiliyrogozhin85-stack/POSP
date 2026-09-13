package com.vasiliyrogozhin85.posphost;

import android.content.Context;
import android.util.Base64;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;

final class AdbKeyStore {
    private static final String PRIV = "adb_private.pk8";
    private static final String PUB = "adb_public.der";
    private static final byte[] SHA1_DIGEST_INFO_PREFIX = hex("3021300906052b0e03021a05000414");

    private final KeyPair pair;

    private AdbKeyStore(KeyPair pair) { this.pair = pair; }

    static AdbKeyStore loadOrCreate(Context context) throws Exception {
        File priv = new File(context.getFilesDir(), PRIV);
        File pub = new File(context.getFilesDir(), PUB);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (priv.isFile() && pub.isFile()) {
            PrivateKey pr = kf.generatePrivate(new PKCS8EncodedKeySpec(readAll(priv)));
            PublicKey pu = kf.generatePublic(new X509EncodedKeySpec(readAll(pub)));
            return new AdbKeyStore(new KeyPair(pu, pr));
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        writeAll(priv, pair.getPrivate().getEncoded());
        writeAll(pub, pair.getPublic().getEncoded());
        return new AdbKeyStore(pair);
    }

    byte[] signAdbToken(byte[] token) throws Exception {
        RSAPrivateCrtKey key = (RSAPrivateCrtKey) pair.getPrivate();
        int keyLen = (key.getModulus().bitLength() + 7) / 8;
        byte[] digestInfo = concat(SHA1_DIGEST_INFO_PREFIX, token);
        int psLen = keyLen - digestInfo.length - 3;
        if (psLen < 8) throw new GeneralSecurityException("RSA key too short");
        byte[] em = new byte[keyLen];
        em[0] = 0x00; em[1] = 0x01;
        for (int i = 0; i < psLen; i++) em[2 + i] = (byte)0xFF;
        em[2 + psLen] = 0x00;
        System.arraycopy(digestInfo, 0, em, 3 + psLen, digestInfo.length);
        BigInteger m = new BigInteger(1, em);
        BigInteger s = m.modPow(key.getPrivateExponent(), key.getModulus());
        return toFixed(s, keyLen);
    }

    byte[] adbPublicKeyPayload() {
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        BigInteger n = key.getModulus();
        int words = 64;
        byte[] struct = new byte[4 + 4 + words * 4 + words * 4 + 4];
        int off = 0;
        putLe32(struct, off, words); off += 4;
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        long n0 = n.and(two32.subtract(BigInteger.ONE)).longValue() & 0xffffffffL;
        long inv = BigInteger.valueOf(n0).modInverse(two32).longValue() & 0xffffffffL;
        long n0inv = (-inv) & 0xffffffffL;
        putLe32(struct, off, n0inv); off += 4;
        BigInteger mask = two32.subtract(BigInteger.ONE);
        for (int i = 0; i < words; i++) {
            putLe32(struct, off, n.shiftRight(i * 32).and(mask).longValue()); off += 4;
        }
        BigInteger rr = BigInteger.ONE.shiftLeft(words * 32 * 2).mod(n);
        for (int i = 0; i < words; i++) {
            putLe32(struct, off, rr.shiftRight(i * 32).and(mask).longValue()); off += 4;
        }
        putLe32(struct, off, key.getPublicExponent().longValue());
        String line = Base64.encodeToString(struct, Base64.NO_WRAP) + " posphost@android\0";
        return line.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n; while ((n = in.read(b)) > 0) out.write(b,0,n); return out.toByteArray();
        }
    }
    private static void writeAll(File f, byte[] data) throws IOException { try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); } }
    private static byte[] concat(byte[] a, byte[] b) { byte[] r = new byte[a.length+b.length]; System.arraycopy(a,0,r,0,a.length); System.arraycopy(b,0,r,a.length,b.length); return r; }
    private static byte[] hex(String s) { byte[] r = new byte[s.length()/2]; for(int i=0;i<r.length;i++) r[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16); return r; }
    private static byte[] toFixed(BigInteger v, int len) { byte[] src=v.toByteArray(), dst=new byte[len]; int copy=Math.min(src.length,len); System.arraycopy(src,src.length-copy,dst,len-copy,copy); return dst; }
    private static void putLe32(byte[] a, int o, long v) { a[o]=(byte)v; a[o+1]=(byte)(v>>>8); a[o+2]=(byte)(v>>>16); a[o+3]=(byte)(v>>>24); }
}
