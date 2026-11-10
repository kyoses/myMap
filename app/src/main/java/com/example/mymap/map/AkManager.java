package com.example.mymap.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.SDKInitializer;

/**
 * 运行时管理百度 AK。
 *
 * <p>AK 默认来自 manifest（构建期注入），但可以在应用内改成别的值 —— 方便
 * 换一个应用（比如另一个包名/签名下的 AK）时不用重新打包。
 *
 * <p>注意：修改 AK 后需要重新初始化地图与检索 SDK 才生效：
 * <ul>
 *   <li>{@code SDKInitializer.setApiKey(ak)} 设置地图 SDK 的 AK；</li>
 *   <li>{@code LBSAuthManager.setKey(ak)} 设置检索/定位 SDK 读的 AK ——
 *       源码里它写入静态字段 q，authenticate() 时才读，所以要在发起请求前设置；</li>
 *   <li>{@code SDKInitializer.initialize(...)} 会校验 AK，包名或签名不匹配时直接失败。</li>
 * </ul>
 */
final class AkManager {

    private static final String TAG = "AkManager";
    private static final String PREFS = "mymap_prefs";
    private static final String KEY_USER_AK = "user_ak";

    private AkManager() {
    }

    /** 用户在应用内设置的 AK，没有则返回空。 */
    static String getUserAk(Context context) {
        return prefs(context).getString(KEY_USER_AK, "");
    }

    /** 保存用户设置的 AK，空字符串表示恢复成 manifest 里的 AK。 */
    static void setUserAk(Context context, String ak) {
        prefs(context).edit()
                .putString(KEY_USER_AK, ak == null ? "" : ak.trim())
                .apply();
    }

    /**
     * 实际生效的 AK：优先用户设置的，其次 manifest 里的。
     */
    static String effectiveAk(Context context) {
        String userAk = getUserAk(context);
        if (!userAk.isEmpty()) {
            return userAk;
        }
        return NaviController.readAkFromManifest(context);
    }

    /**
     * 应用新的 AK。
     *
     * @return null 表示成功，否则是失败原因
     */
    static String applyAk(Context context, String ak) {
        if (ak == null || ak.trim().isEmpty()) {
            return "AK 不能为空";
        }
        String trimmed = ak.trim();
        try {
            // 检索/定位 SDK 读的是 LBSAuthManager 里的静态 AK
            com.baidu.lbsapi.auth.LBSAuthManager.getInstance(context)
                    .setKey(trimmed);
            // 地图 SDK 用的 setApiKey
            SDKInitializer.setApiKey(trimmed);
            LocationClient.setAgreePrivacy(true);
            Log.i(TAG, "已切换 AK");
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "设置 AK 失败", t);
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
