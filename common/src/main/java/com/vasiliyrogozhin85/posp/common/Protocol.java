package com.vasiliyrogozhin85.posp.common;
import java.nio.charset.StandardCharsets;
public final class Protocol {
    public static final String HELLO="POSP_HELLO", PING="PING", PONG="PONG",
        COLLECT_REPORT="COLLECT_REPORT", REPORT_BEGIN="REPORT_BEGIN", REPORT_END="REPORT_END";
    public static byte[] line(String s){ return (s+"\n").getBytes(StandardCharsets.UTF_8); }
    private Protocol(){}
}
