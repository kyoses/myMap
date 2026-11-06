package com.example.mymap.map;

import android.content.Context;
import android.util.Log;

import com.baidu.location.LocationClient;
import com.baidu.mapapi.SDKInitializer;

/**
 * 百度地图 / 定位 SDK 的初始化。
 *
 * <p>两个前置条件缺一不可，官方文档明确要求：
 * <ol>
 *   <li>调用任何 SDK 接口前先 {@code setAgreePrivacy(context, true)}，
 *       否则地图 SDK 不支持发起检索、路线规划等数据请求；</li>
 *   <li>地图 SDK 需要先 {@code SDKInitializer.initialize(context)} 才会去校验 AK，
 *       否则地图无法初始化。</li>
 * </ol>
 * 定位 SDK 的隐私接口是独立的一份，签名也不同，需要单独设置。
 */
final class BaiduSdkInitializer {

    private static final String TAG = "BaiduSdkInit";
    private static boolean initialized;

    private BaiduSdkInitializer() {
    }

    /**
     * 初始化百度 SDK。
     *
     * @param ak 百度地图 AK，没有有效 AK 时地图无法初始化
     */
    static synchronized void init(Context context, String ak) {
        if (initialized) {
            return;
        }
        Context appContext = context.getApplicationContext();

        // 隐私合规：地图 SDK 与定位 SDK 各有一份接口，都要在使用前设置。
        try {
            SDKInitializer.setAgreePrivacy(appContext, true);
            LocationClient.setAgreePrivacy(true);
        } catch (Throwable t) {
            Log.e(TAG, "setAgreePrivacy 失败", t);
        }

        if (ak != null && !ak.trim().isEmpty()) {
            try {
                SDKInitializer.setApiKey(ak.trim());
            } catch (Throwable t) {
                Log.e(TAG, "setApiKey 失败", t);
            }
        } else {
            Log.w(TAG, "未配置百度地图 AK，地图与检索功能不可用");
        }

        try {
            SDKInitializer.initialize(appContext);
            initialized = true;
            Log.i(TAG, "百度地图 SDK 初始化完成");
        } catch (Throwable t) {
            Log.e(TAG, "SDKInitializer.initialize 失败", t);
        }
    }
}
