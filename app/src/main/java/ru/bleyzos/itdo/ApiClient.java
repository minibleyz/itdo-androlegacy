package ru.bleyzos.itdo;

import android.webkit.CookieManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Minimal blocking JSON client for the site API. Cookies are shared with the WebView's
 * CookieManager, so a native login immediately signs the WebView in. Call off the main thread.
 */
final class ApiClient {

    static final class Result {
        int code;
        JSONObject json;
        /** Server message ("error" field) or null. */
        String error;
        /** No answer from the server at all. */
        boolean network;

        boolean ok() {
            return !network && code >= 200 && code < 300;
        }
    }

    private ApiClient() {
    }

    static Result get(String path) {
        return request("GET", path, null);
    }

    static Result post(String path, JSONObject body) {
        return request("POST", path, body);
    }

    private static Result request(String method, String path, JSONObject body) {
        Result r = new Result();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BuildConfig.BASE_URL + path).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            String ua = System.getProperty("http.agent");
            c.setRequestProperty("User-Agent", (ua == null ? "" : ua + " ") + "ITDOAndroid/" + BuildConfig.VERSION_NAME);

            CookieManager cookies = CookieManager.getInstance();
            String cookie = cookies.getCookie(BuildConfig.BASE_URL);
            if (cookie != null) c.setRequestProperty("Cookie", cookie);

            if (body != null) {
                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(data);
                }
            }

            r.code = c.getResponseCode();

            for (Map.Entry<String, List<String>> h : c.getHeaderFields().entrySet()) {
                if (h.getKey() != null && h.getKey().equalsIgnoreCase("Set-Cookie")) {
                    for (String sc : h.getValue()) cookies.setCookie(BuildConfig.BASE_URL, sc);
                }
            }
            cookies.flush();

            InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            try {
                r.json = new JSONObject(text);
                if (r.json.has("error") && !r.json.isNull("error")) r.error = r.json.optString("error");
            } catch (JSONException ignored) {
                r.json = null;
            }
        } catch (IOException e) {
            r.network = true;
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }
}
