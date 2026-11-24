package com.yompix.yurk;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Tiny HTTPS client for the panel. code 0 = no connection. */
final class Http {
    static final class Result {
        final int code; final String body;
        Result(int c, String b) { code = c; body = b; }
    }

    static Result request(String method, String url, String body, String token) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("x-app-token", token == null ? "" : token);
            c.setRequestProperty("user-agent", "YURK-Android/" + BuildConfig.VERSION_NAME);
            if (body != null && "POST".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("content-type", "application/json");
                try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            return new Result(code, in == null ? "" : read(in));
        } catch (Exception e) {
            return new Result(0, "{\"error\":\"network\"}");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String read(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}
