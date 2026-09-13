package com.vasiliyrogozhin85.posp.common;

import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final String VERSION = "1";
    public static final String HELLO = "POSP_HELLO";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    public static final String COLLECT_REPORT = "COLLECT_REPORT";
    public static final String REPORT_BEGIN = "REPORT_BEGIN";
    public static final String REPORT_END = "REPORT_END";
    public static final String STATUS = "STATUS";

    private Protocol() {}

    public static byte[] line(String message) {
        return (message + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
