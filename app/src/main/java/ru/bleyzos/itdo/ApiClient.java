package ru.bleyzos.itdo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal JSON HTTP client for the native screens (Wallet, Pro, Pixel Battle, Clips,
 * Notifications, Top, Quests, Agent, Settings).
 *
 * The site's API (see /api/*.php) authenticates via a PHP session cookie. Since the user is
 * already logged in inside the app's WebView, we simply forward that same cookie on every
 * request instead of re-implementing login here.
 */
final class ApiClient {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Callback {
        void onResult(JSONObject json, String error);
    }

    /** Raw-line callback used for the agent's Server-Sent-Events chat stream. */
    interface StreamCallback {
        void onLine(String sseDataLine);
        void onDone(String error);
    }

    static void get(Context ctx, String path, Callback cb) {
        request(ctx, "GET", path, null, cb);
    }

    static void post(Context ctx, String path, JSONObject body, Callback cb) {
        request(ctx, "POST", path, body, cb);
    }

    static void delete(Context ctx, String path, JSONObject body, Callback cb) {
        request(ctx, "DELETE", path, body, cb);
    }

    static void patch(Context ctx, String path, JSONObject body, Callback cb) {
        request(ctx, "PATCH", path, body, cb);
    }

    private static void request(Context ctx, String method, String path, JSONObject body, Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open(app, method, path);
                if (body != null) writeBody(conn, body);
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                String text = readAll(is);
                JSONObject json = new JSONObject(text.isEmpty() ? "{}" : text);
                if (code >= 400 && !json.has("error")) {
                    json.put("error", "HTTP " + code);
                }
                deliver(cb, json, null);
            } catch (Exception e) {
                deliver(cb, null, e.getMessage() == null ? e.toString() : e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    /** Streams the ITDO Agent's chat SSE response (text/event-stream, "data: {json}\n\n" frames). */
    static void stream(Context ctx, String path, JSONObject body, StreamCallback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open(app, "POST", path);
                conn.setReadTimeout(180000);
                writeBody(conn, body);
                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String payload = line.substring(5).trim();
                        MAIN.post(() -> cb.onLine(payload));
                    }
                }
                MAIN.post(() -> cb.onDone(null));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.toString() : e.getMessage();
                MAIN.post(() -> cb.onDone(msg));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private static HttpURLConnection open(Context app, String method, String path) throws IOException {
        URL url = new URL(BuildConfig.BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("Accept", "application/json, text/event-stream");
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
        String cookie = CookieManager.getInstance().getCookie(BuildConfig.BASE_URL);
        if (cookie != null && !cookie.isEmpty()) {
            conn.setRequestProperty("Cookie", cookie);
        }
        return conn;
    }

    private static void writeBody(HttpURLConnection conn, JSONObject body) throws IOException {
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] data = (body == null ? new JSONObject() : body).toString().getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(data.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data);
        }
    }

    private static void deliver(Callback cb, JSONObject json, String err) {
        MAIN.post(() -> cb.onResult(json, err));
    }

    private static String readAll(InputStream is) throws IOException {
        if (is == null) return "{}";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    private ApiClient() {}
}
