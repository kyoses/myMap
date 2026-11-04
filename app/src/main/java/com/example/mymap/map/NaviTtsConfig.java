package com.example.mymap.map;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

/**
 * 从 manifest 的 meta-data 里读取百度 TTS 授权参数。
 *
 * <p>密钥由 Gradle 的 manifestPlaceholders 注入，不落在源码里。四个参数任一缺失时
 * {@link #isConfigured()} 返回 false，调用方据此跳过 TTS 初始化。
 */
final class NaviTtsConfig {

    private static final String META_APP_ID = "com.baidu.navisdk.tts.APP_ID";
    private static final String META_APP_KEY = "com.baidu.navisdk.tts.APP_KEY";
    private static final String META_SECRET_KEY = "com.baidu.navisdk.tts.SECRET_KEY";
    private static final String META_AUTH_SN = "com.baidu.navisdk.tts.AUTH_SN";

    final String appId;
    final String appKey;
    final String secretKey;
    final String authSn;

    private NaviTtsConfig(String appId, String appKey, String secretKey, String authSn) {
        this.appId = appId;
        this.appKey = appKey;
        this.secretKey = secretKey;
        this.authSn = authSn;
    }

    static NaviTtsConfig read(Context context) {
        return new NaviTtsConfig(
                readMeta(context, META_APP_ID),
                readMeta(context, META_APP_KEY),
                readMeta(context, META_SECRET_KEY),
                readMeta(context, META_AUTH_SN));
    }

    boolean isConfigured() {
        return !isEmpty(appId) && !isEmpty(appKey)
                && !isEmpty(secretKey) && !isEmpty(authSn);
    }

    private static String readMeta(Context context, String key) {
        try {
            ApplicationInfo info = context.getPackageManager()
                    .getApplicationInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA);
            if (info.metaData == null) {
                return "";
            }
            Object value = info.metaData.get(key);
            return value == null ? "" : value.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
