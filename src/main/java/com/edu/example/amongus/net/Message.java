package com.edu.example.amongus.net;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单行协议构造与解析工具
 * 格式: TYPE|k1=<base64(v1)>;k2=<base64(v2)>;...
 */
public class Message {
    public static String build(String type, Map<String, String> payload) {
        StringBuilder sb = new StringBuilder();
        if (payload != null) {
            payload.forEach((k, v) -> {
                String enc = Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));
                sb.append(k).append("=").append(enc).append(";");
            });
        }
        return type + "|" + sb.toString();
    }

    public static Parsed parse(String line) {
        if (line == null) return null;
        int sep = line.indexOf('|');
        if (sep < 0) return new Parsed(line.trim(), new HashMap<>());
        String type = line.substring(0, sep).trim();
        String payload = line.substring(sep + 1);
        Map<String, String> map = new HashMap<>();
        if (!payload.isEmpty()) {
            String[] parts = payload.split(";");
            for (String p : parts) {
                if (p.isEmpty()) continue;
                int eq = p.indexOf('=');
                if (eq <= 0) continue;
                String k = p.substring(0, eq);
                String v64 = p.substring(eq + 1);
                try {
                    String v = new String(Base64.getDecoder().decode(v64), StandardCharsets.UTF_8);
                    map.put(k, v);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return new Parsed(type, map);
    }

    public static class Parsed {
        public final String type;
        public final Map<String, String> payload;
        public Parsed(String type, Map<String, String> payload) {
            this.type = type;
            this.payload = payload;
        }
    }
}
