package com.vasiliyrogozhin85.upospa;

import android.hardware.usb.*;

import java.io.*;
import java.nio.charset.StandardCharsets;

final class AoaHostConnection implements Closeable {
    interface Listener {
        void onLine(String s);
        void onReport(File f);
        void onError(String s);
    }

    private final UsbDeviceConnection c;
    private final UsbInterface intf;
    private final UsbEndpoint in, out;
    private volatile boolean running;

    private AoaHostConnection(UsbDeviceConnection c, UsbInterface i,
                              UsbEndpoint in, UsbEndpoint out) {
        this.c = c; intf = i; this.in = in; this.out = out;
    }

    static AoaHostConnection open(UsbManager m, UsbDevice d) throws Exception {
        UsbInterface fi = null;
        UsbEndpoint ei = null, eo = null;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            UsbInterface ui = d.getInterface(i);
            UsbEndpoint ti = null, to = null;
            for (int e = 0; e < ui.getEndpointCount(); e++) {
                UsbEndpoint ep = ui.getEndpoint(e);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) ti = ep; else to = ep;
            }
            if (ti != null && to != null && !AdbUsbClient.isAdbInterface(ui)) {
                fi = ui; ei = ti; eo = to; break;
            }
        }
        if (fi == null) throw new IOException("Accessory bulk interface not found");
        UsbDeviceConnection c = m.openDevice(d);
        if (c == null) throw new IOException("openDevice=null");
        if (!c.claimInterface(fi, true)) {
            c.close();
            throw new IOException("claim accessory interface failed");
        }
        return new AoaHostConnection(c, fi, ei, eo);
    }

    synchronized void sendLine(String s) throws Exception {
        byte[] b = Protocol.line(s);
        int n = c.bulkTransfer(out, b, b.length, 3000);
        if (n != b.length) throw new IOException("AOA write " + n + "/" + b.length);
    }

    void start(File dir, Listener l) {
        if (running) return;
        running = true;
        new Thread(() -> loop(dir, l), "upospa-aoa").start();
    }

    private void loop(File dir, Listener l) {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        try {
            while (running) {
                int x = readByte();
                if (x < 0) continue;
                if (x == '\n') {
                    String s = line.toString(StandardCharsets.UTF_8.name()).trim();
                    line.reset();
                    if (s.startsWith(Protocol.REPORT_BEGIN + " ")) {
                        long size = Long.parseLong(
                                s.substring((Protocol.REPORT_BEGIN + " ").length()).trim());
                        if (size < 0 || size > 64 * 1024 * 1024L)
                            throw new IOException("bad report size " + size);
                        dir.mkdirs();
                        File f = new File(dir,
                                "uPOSPa_Client_Report_" + System.currentTimeMillis() + ".json");
                        try (FileOutputStream o = new FileOutputStream(f)) {
                            copy(o, size);
                        }
                        l.onReport(f);
                    } else if (!s.isEmpty() && !Protocol.REPORT_END.equals(s)) {
                        l.onLine(s);
                    }
                } else {
                    if (line.size() > 8192) throw new IOException("protocol line too long");
                    line.write(x);
                }
            }
        } catch (Exception e) {
            if (running) l.onError(String.valueOf(e));
        }
    }

    private int readByte() {
        byte[] b = new byte[1];
        int n = c.bulkTransfer(in, b, 1, 1000);
        return n == 1 ? (b[0] & 255) : -1;
    }

    private void copy(OutputStream o, long count) throws Exception {
        byte[] b = new byte[16384];
        long r = count;
        while (r > 0) {
            int want = (int) Math.min(b.length, r);
            int n = c.bulkTransfer(in, b, want, 5000);
            if (n <= 0) throw new EOFException("report interrupted");
            o.write(b, 0, n);
            r -= n;
        }
    }

    public void close() {
        running = false;
        try { c.releaseInterface(intf); } catch (Exception ignored) {}
        try { c.close(); } catch (Exception ignored) {}
    }
}
