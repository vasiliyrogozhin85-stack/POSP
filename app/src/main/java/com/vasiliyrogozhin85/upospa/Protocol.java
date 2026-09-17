package com.vasiliyrogozhin85.upospa;

import java.nio.charset.StandardCharsets;

final class Protocol {
    static final String HELLO = "POSP_HELLO";
    static final String PING = "PING";
    static final String PONG = "PONG";
    static final String COLLECT_REPORT = "COLLECT_REPORT";
    static final String REPORT_BEGIN = "REPORT_BEGIN";
    static final String REPORT_END = "REPORT_END";

    static byte[] line(String s) {
        return (s + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private Protocol() {}
}
