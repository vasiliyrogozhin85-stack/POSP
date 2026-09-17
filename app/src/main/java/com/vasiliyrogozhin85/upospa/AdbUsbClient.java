package com.vasiliyrogozhin85.upospa;

import android.content.Context;
import android.hardware.usb.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

final class AdbUsbClient implements Closeable {
    static final int A_CNXN = 0x4e584e43, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
            A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_AUTH = 0x48545541;
    static final int AUTH_TOKEN = 1, AUTH_SIGNATURE = 2, AUTH_RSAPUBLICKEY = 3;

    private final UsbDeviceConnection conn;
    private final UsbInterface intf;
    private final UsbEndpoint in, out;
    private final AdbKeyStore keys;
    private int nextLocal = 1;

    private AdbUsbClient(Context c, UsbDeviceConnection co, UsbInterface i,
                         UsbEndpoint in, UsbEndpoint out) throws Exception {
        conn = co; intf = i; this.in = in; this.out = out;
        keys = AdbKeyStore.loadOrCreate(c);
    }

    static boolean isAdbInterface(UsbInterface i) {
        return i.getInterfaceClass() == 255 &&
                i.getInterfaceSubclass() == 66 &&
                i.getInterfaceProtocol() == 1;
    }

    static AdbUsbClient open(Context ctx, UsbManager mgr, UsbDevice dev) throws Exception {
        UsbInterface found = null;
        UsbEndpoint ei = null, eo = null;
        for (int x = 0; x < dev.getInterfaceCount(); x++) {
            UsbInterface i = dev.getInterface(x);
            if (!isAdbInterface(i)) continue;
            for (int e = 0; e < i.getEndpointCount(); e++) {
                UsbEndpoint ep = i.getEndpoint(e);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    if (ep.getDirection() == UsbConstants.USB_DIR_IN) ei = ep; else eo = ep;
                }
            }
            if (ei != null && eo != null) { found = i; break; }
        }
        if (found == null) throw new IOException("ADB interface not found");
        UsbDeviceConnection c = mgr.openDevice(dev);
        if (c == null) throw new IOException("USB open failed / permission missing");
        if (!c.claimInterface(found, true)) {
            c.close();
            throw new IOException("Cannot claim ADB interface");
        }
        return new AdbUsbClient(ctx, c, found, ei, eo);
    }

    void connect() throws Exception {
        send(A_CNXN, 0x01000000, 4096,
                "host::uPOSPaHost\0".getBytes(StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + 90000;
        boolean sentSig = false;
        while (System.currentTimeMillis() < deadline) {
            Packet p = readPacket(10000);
            if (p == null) continue;
            if (p.cmd == A_CNXN) return;
            if (p.cmd == A_AUTH && p.arg0 == AUTH_TOKEN) {
                if (!sentSig) {
                    send(A_AUTH, AUTH_SIGNATURE, 0, keys.signAdbToken(p.data));
                    sentSig = true;
                } else {
                    send(A_AUTH, AUTH_RSAPUBLICKEY, 0, keys.adbPublicKeyPayload());
                }
            }
        }
        throw new IOException("ADB authorization timeout. Подтвердите RSA-ключ на подключённом телефоне.");
    }

    String shell(String cmd, int timeout) throws Exception {
        int local = nextLocal++;
        send(A_OPEN, local, 0, ("shell:" + cmd + "\0").getBytes(StandardCharsets.UTF_8));
        int remote = 0;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        long end = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() < end) {
            Packet p = readPacket(Math.min(5000,
                    Math.max(500, (int) (end - System.currentTimeMillis()))));
            if (p == null) continue;
            if (p.cmd == A_OKAY && p.arg1 == local) { remote = p.arg0; continue; }
            if (p.cmd == A_WRTE && p.arg1 == local) {
                remote = p.arg0;
                data.write(p.data);
                send(A_OKAY, local, remote, new byte[0]);
                continue;
            }
            if (p.cmd == A_CLSE && p.arg1 == local) {
                if (remote == 0) remote = p.arg0;
                send(A_CLSE, local, remote, new byte[0]);
                return data.toString("UTF-8");
            }
        }
        if (remote != 0) send(A_CLSE, local, remote, new byte[0]);
        throw new IOException("ADB shell timeout: " + cmd);
    }

    private void send(int cmd, int a0, int a1, byte[] d) throws IOException {
        if (d == null) d = new byte[0];
        int sum = 0;
        for (byte b : d) sum += b & 255;
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd).putInt(a0).putInt(a1).putInt(d.length).putInt(sum)
                .putInt(cmd ^ 0xffffffff);
        write(h.array(), 10000);
        if (d.length > 0) write(d, 10000);
    }

    private Packet readPacket(int timeout) throws IOException {
        byte[] h = read(24, timeout);
        if (h == null) return null;
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        int c = b.getInt(), a0 = b.getInt(), a1 = b.getInt(),
                len = b.getInt(), chk = b.getInt(), magic = b.getInt();
        if ((c ^ 0xffffffff) != magic) throw new IOException("Bad ADB magic");
        if (len < 0 || len > 1024 * 1024) throw new IOException("Bad ADB payload " + len);
        byte[] d = len == 0 ? new byte[0] : read(len, timeout);
        if (d == null) throw new IOException("ADB payload timeout");
        int sum = 0;
        for (byte x : d) sum += x & 255;
        if (sum != chk) throw new IOException("Bad ADB checksum");
        return new Packet(c, a0, a1, d);
    }

    private void write(byte[] b, int t) throws IOException {
        int n = conn.bulkTransfer(out, b, b.length, t);
        if (n != b.length) throw new IOException("USB write " + n + "/" + b.length);
    }

    private byte[] read(int len, int timeout) throws IOException {
        byte[] all = new byte[len];
        int off = 0;
        long end = System.currentTimeMillis() + timeout;
        while (off < len) {
            int rem = len - off;
            byte[] tmp = new byte[rem];
            int n = conn.bulkTransfer(in, tmp, rem,
                    (int) Math.max(1, end - System.currentTimeMillis()));
            if (n <= 0) {
                if (off == 0) return null;
                throw new IOException("USB read timeout");
            }
            System.arraycopy(tmp, 0, all, off, n);
            off += n;
        }
        return all;
    }

    public void close() {
        try { conn.releaseInterface(intf); } catch (Exception ignored) {}
        try { conn.close(); } catch (Exception ignored) {}
    }

    private static final class Packet {
        final int cmd, arg0, arg1;
        final byte[] data;
        Packet(int c, int a0, int a1, byte[] d) {
            cmd = c; arg0 = a0; arg1 = a1; data = d;
        }
    }
}
