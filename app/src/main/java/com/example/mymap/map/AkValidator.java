package com.example.mymap.map;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 直接问百度服务端：这个 AK 有效吗？它能调哪些服务？
 *
 * <p>SDK 只把服务端的错误码折叠成几个枚举（检索失败一律是
 * PERMISSION_UNFINISHED），分不清是「AK 不存在」「类型不对」还是「没勾选服务」。
 * 这里直接调 Web API 拿服务端的原始 message，能看清真实原因。
 */
final class AkValidator {

    private static final String TAG = "AkValidator";
    private static final String PLACE_API =
            "https://api.map.baidu.com/place/v2/search?query=%%E4%%BA%%BA%%E6%%B0%%91%%E5%%B9%%BF%%E5%%9C%%BA"
                    + "&region=%%E5%%85%%A8%%E5%%9B%%BD&output=json&ak=";
    private static final String WEATHER_API =
            "https://api.map.baidu.com/weather/v1?location=%%E5%%8C%%97%%E4%%BA%%AC"
                    + "&output=json&ak=";
    private static final int TIMEOUT_MS = 15000;

    private AkValidator() {
    }

    /** 一个服务的能力检查结果。 */
    private static final class Probe {
        final String name;
        final String message;

        Probe(String name, String message) {
            this.name = name;
            this.message = message;
        }
    }

    /**
     * 检查 AK 对几个服务的可用性。
     *
     * @param ak 要检查的 AK
     * @return 多行文本，每行一个服务的检查结果
     */
    static String check(String ak) {
        if (ak == null || ak.trim().isEmpty()) {
            return "AK 为空 —— 检索、地理编码与导航都不会工作";
        }
        String trimmed = ak.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("AK: ").append(mask(trimmed)).append('\n');
        Probe place = probe("地点检索", PLACE_API + trimmed);
        Probe weather = probe("天气查询", WEATHER_API + trimmed);
        sb.append(place.name).append(": ").append(place.message).append('\n');
        sb.append(weather.name).append(": ").append(weather.message).append('\n');
        return sb.toString();
    }

    private static Probe probe(String name, String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            String body = readBody(conn, code);
            String message = extractMessage(body);
            if (message == null || message.isEmpty()) {
                message = "HTTP " + code;
            }
            return new Probe(name, message);
        } catch (Exception e) {
            return new Probe(name, "请求失败：" + e.getClass().getSimpleName()
                    + " " + (e.getMessage() == null ? "" : e.getMessage()));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 从百度返回的 JSON 里取 message 字段。 */
    private static String extractMessage(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        int idx = body.indexOf("\"message\"");
        if (idx < 0) {
            // 天气接口成功时返回的是 HTML 包裹的 JSON
            int statusIdx = body.indexOf("\"status\"");
            if (statusIdx < 0) {
                return null;
            }
            return "返回正常（未含 message 字段）";
        }
        int colon = body.indexOf(':', idx);
        if (colon < 0) {
            return null;
        }
        int start = body.indexOf('"', colon);
        if (start < 0) {
            return null;
        }
        int end = body.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        return body.substring(start + 1, end);
    }

    private static String readBody(HttpURLConnection conn, int code) {
        try (InputStream in = (code >= 200 && code < 400)
                ? conn.getInputStream() : conn.getErrorStream()) {
            if (in == null) {
                return "";
            }
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    text.append(line);
                }
            }
            return text.toString();
        } catch (Exception e) {
            Log.w(TAG, "读取响应失败", e);
            return "";
        }
    }

    private static String mask(String ak) {
        if (ak.length() <= 8) {
            return ak;
        }
        return ak.substring(0, 4) + "…" + ak.substring(ak.length() - 4);
    }
}
