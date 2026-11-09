package com.example.mymap.map;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;

import com.baidu.navisdk.adapter.BNaviCommonParams;
import com.baidu.navisdk.adapter.BNRoutePlanNode;
import com.baidu.navisdk.adapter.BaiduNaviManagerFactory;
import com.baidu.navisdk.adapter.IBaiduNaviManager;
import com.baidu.navisdk.adapter.IBNLicenseListener;
import com.baidu.navisdk.adapter.IBNaviListener;
import com.baidu.navisdk.adapter.IBNaviViewListener;
import com.baidu.navisdk.adapter.IBNRouteGuideManager;
import com.baidu.navisdk.adapter.IBNRoutePlanManager;
import com.baidu.navisdk.adapter.struct.BNGuideConfig;
import com.baidu.navisdk.adapter.struct.BNaviInitConfig;
import com.baidu.navisdk.adapter.struct.BNRoutePlanInfos;
import com.baidu.navisdk.adapter.struct.BNTTsInitConfig;
import com.baidu.navisdk.tts.ITTSInitListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 百度导航 SDK 的薄封装：初始化、算路、进入专业导航。
 *
 * <p>SDK 的入口全部通过 {@link BaiduNaviManagerFactory} 获取管理，
 * 算路结果通过 Handler 回调 {@code MSG_NAVI_ROUTE_PLAN_*} 通知。
 */
final class NaviController {

    private static final String TAG = "NaviController";

    /** 百度授权失败码，见 com.baidu.lbsapi.auth.LBSAuthManager。 */
    private static final int AUTH_CODE_SUCCESS = 0;
    private static final int AUTH_CODE_KEY_NOT_EXIST = 101;
    private static final int AUTH_CODE_NETWORK_INVALID = -10;
    private static final int AUTH_CODE_NETWORK_FAILED = -11;

    private final Activity activity;
    private final Callback callback;

    private IBaiduNaviManager naviManager;
    private IBNRouteGuideManager routeGuideManager;
    private boolean initialized;
    private boolean routePlanned;

    interface Callback {
        void onInitSuccess();

        void onInitFailed(String reason);

        void onRoutePlanned();

        void onRouteFailed(String reason);

        void onTtsReady();

        void onTtsUnavailable(String reason);
    }

    NaviController(Activity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    /**
     * 初始化导航 SDK。
     *
     * <p>初始化分两步且都是异步的：先 {@code authenticate()} 做联网授权，
     * 授权成功后才回调 {@code initSuccess()}。授权失败时 SDK 只给一个错误码，
     * 这里把它翻译成可读原因。
     *
     * @param sdcardRootPath 引擎数据目录名，会落在应用私有外部目录下
     */
    void init(String sdcardRootPath) {
        if (initialized) {
            return;
        }
        naviManager = BaiduNaviManagerFactory.getBaiduNaviManager();
        routeGuideManager = BaiduNaviManagerFactory.getRouteGuideManager();

        BNaviInitConfig config = new BNaviInitConfig.Builder()
                .sdcardRootPath(sdcardRootPath)
                .naviInitListener(new IBaiduNaviManager.INaviInitListener() {
                    @Override
                    public void onAuthResult(int errorCode, String message) {
                        Log.i(TAG, "onAuthResult: " + errorCode + " " + message);
                        if (errorCode != AUTH_CODE_SUCCESS) {
                            callback.onInitFailed(describeAuthError(errorCode, message));
                        }
                    }

                    @Override
                    public void initStart() {
                        Log.i(TAG, "navi init start");
                    }

                    @Override
                    public void initSuccess() {
                        initialized = true;
                        callback.onInitSuccess();
                    }

                    @Override
                    public void initFailed(int errorCode) {
                        Log.e(TAG, "navi init failed: " + errorCode);
                        callback.onInitFailed(describeInitError(errorCode));
                    }
                })
                .build();
        naviManager.init(activity.getApplicationContext(), config);
    }

    /** 把授权错误码翻译成可读原因。 */
    private static String describeAuthError(int code, String message) {
        String detail = (message == null || message.trim().isEmpty()) ? "" : "：" + message;
        switch (code) {
            case AUTH_CODE_KEY_NOT_EXIST:
                return "AK 无效或不存在，请检查 local.properties 里的 baiduMapAk，"
                        + "并确认 AK 与包名 com.example.mymap、签名 SHA1 匹配" + detail;
            case AUTH_CODE_NETWORK_INVALID:
            case AUTH_CODE_NETWORK_FAILED:
                return "导航授权需要联网，请检查网络后重试" + detail;
            default:
                return "导航授权失败，错误码 " + code
                        + "。若地图能用但导航不可用，通常是该 AK 未开通「驾车导航」权限，"
                        + "需要在百度地图开放平台为应用勾选该权限" + detail;
        }
    }

    /**
     * 把引擎初始化错误码翻译成可读原因。
     *
     * <p>这些码来自 {@code com.baidu.navisdk.api.common.error.c}，是**导航授权**
     * 相关的一组：地图能用不代表导航授权能通过。
     */
    private static String describeInitError(int code) {
        switch (code) {
            case 6:
                return "导航授权失败：本地序列号校验不通过（错误码 "
                        + code + "）。请确认 AK 与包名、签名 SHA1 一致";
            case 7:
                return "导航授权失败：生成安装码失败（错误码 " + code + "）";
            case 8:
                return "导航授权失败：本地激活码校验不通过（错误码 " + code + "）";
            case 9:
                return "导航授权失败：本地授权已过期或不可用（错误码 " + code + "）";
            case 10:
                return "导航授权失败：授权服务器返回的数据无法解析（错误码 " + code
                        + "）。通常是该 AK 未开通「驾车导航」权限，"
                        + "请到百度地图开放平台为应用勾选该权限后重新生成 AK";
            case 11:
                return "导航授权失败：授权数据异常（错误码 " + code + "）";
            case 100:
                return "导航授权失败：授权模块错误（错误码 " + code + "）";
            case 101:
                return "导航授权失败：授权已过期或不可用（错误码 " + code + "）";
            case 102:
                return "导航授权失败：本地授权已过期或不可用（错误码 " + code + "）";
            case 103:
                return "导航授权失败：本地授权参数错误（错误码 " + code + "）";
            case 104:
                return "导航授权失败：授权服务器返回的数据无法解析（错误码 " + code
                        + "）。通常是该 AK 未开通「驾车导航」权限";
            case 105:
                return "导航授权失败：生成安装码失败（错误码 " + code + "）";
            case 106:
                return "导航授权失败：本地序列号校验不通过（错误码 " + code + "）";
            case 107:
                return "导航授权失败：本地激活码校验不通过（错误码 " + code + "）";
            case 108:
                return "导航授权失败：授权文件写入失败（错误码 " + code + "）";
            case 109:
                return "导航授权失败：授权文件读取失败（错误码 " + code + "）";
            case 110:
                return "导航授权失败：授权参数错误（错误码 " + code + "）";
            default:
                return "导航初始化失败，错误码 " + code
                        + "。若地图可用但导航不可用，通常是该 AK 未开通「驾车导航」权限";
        }
    }

    /** 申请 SDK 授权。AK 需与包名、签名 SHA1 一致，否则导航不可用。 */
    void loadAuth(String ak, String deviceId) {
        if (naviManager == null) {
            callback.onInitFailed("导航 SDK 尚未初始化，无法申请授权");
            return;
        }
        if (ak == null || ak.trim().isEmpty()) {
            callback.onInitFailed("未配置百度地图 AK，导航不可用");
            return;
        }
        naviManager.loadAuth(ak, deviceId, new IBNLicenseListener() {
            @Override
            public void onSuccess(boolean success) {
                Log.i(TAG, "loadAuth success: " + success);
            }

            @Override
            public void onError(int errorCode, String message) {
                Log.e(TAG, "loadAuth error: " + errorCode + " " + message);
                callback.onInitFailed("导航授权失败 (" + errorCode + "): " + message);
            }
        });
    }

    /**
     * 初始化内置 TTS（语音播报）。
     *
     * <p>未配置 TTS 授权参数时直接跳过，导航仍可正常使用，只是没有语音。
     */
    void initTts(NaviTtsConfig config) {
        if (config == null || !config.isConfigured()) {
            Log.w(TAG, "TTS 授权参数未配置，跳过语音播报初始化");
            callback.onTtsUnavailable("未配置 TTS 授权参数，导航将无语音播报");
            return;
        }
        BNTTsInitConfig ttsConfig = new BNTTsInitConfig.Builder()
                .context(activity.getApplicationContext())
                .appId(config.appId)
                .appKey(config.appKey)
                .secretKey(config.secretKey)
                .authSn(config.authSn)
                .listener(new ITTSInitListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "TTS init success");
                        callback.onTtsReady();
                    }

                    @Override
                    public void onFail(int errorCode) {
                        Log.e(TAG, "TTS init failed: " + errorCode);
                        callback.onTtsUnavailable("TTS 初始化失败，错误码 " + errorCode);
                    }
                })
                .build();
        BaiduNaviManagerFactory.getTTSManager().initTTS(ttsConfig);
    }

    /** 发起驾车路线规划。 */
    void planRoute(double startLat, double startLng, String startName,
            double endLat, double endLng, String endName) {
        if (!initialized) {
            callback.onRouteFailed("导航 SDK 尚未初始化完成，请等待初始化成功后重试");
            return;
        }

        BNRoutePlanNode start = new BNRoutePlanNode.Builder()
                .latitude(startLat)
                .longitude(startLng)
                .name(startName)
                .description(startName)
                .build();
        BNRoutePlanNode end = new BNRoutePlanNode.Builder()
                .latitude(endLat)
                .longitude(endLng)
                .name(endName)
                .description(endName)
                .build();

        List<BNRoutePlanNode> nodes = new ArrayList<>();
        nodes.add(start);
        nodes.add(end);

        routePlanned = false;
        BaiduNaviManagerFactory.getRoutePlanManager().routePlan(nodes,
                IBNRoutePlanManager.RoutePlanPreference.ROUTE_PLAN_PREFERENCE_DEFAULT,
                null,
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_START:
                                Log.i(TAG, "route plan start");
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_SUCCESS:
                                routePlanned = true;
                                Log.i(TAG, "route plan success");
                                callback.onRoutePlanned();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_FAILED:
                                Log.e(TAG, "route plan failed: " + msg.arg1);
                                callback.onRouteFailed("算路失败，错误码 " + msg.arg1);
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_TO_NAVI:
                                break;
                            default:
                                break;
                        }
                    }
                });
    }

    /**
     * 创建专业导航视图并返回，由调用方挂到布局里。
     *
     * @param mock true 为模拟导航，false 为真实导航
     */
    View createGuideView(boolean mock) {
        if (routeGuideManager == null) {
            return null;
        }
        Bundle params = new Bundle();
        // ADD_MAP 保持默认 true：专业导航页自带地图，路线与诱导面板都在这个 View 内。
        params.putBoolean(BNaviCommonParams.ProGuideKey.IS_REALNAVI, !mock);
        params.putBoolean(BNaviCommonParams.ProGuideKey.IS_SUPPORT_FULL_SCREEN, true);

        BNGuideConfig config = new BNGuideConfig.Builder()
                .params(params)
                .build();
        return routeGuideManager.onCreate(activity, config);
    }

    /** 算路成功后开启专业导航。 */
    boolean startNavi() {
        if (routeGuideManager == null) {
            return false;
        }
        return BaiduNaviManagerFactory.getLightNaviManager().startProfessionalNavi();
    }

    void onStart() {
        if (routeGuideManager != null) {
            routeGuideManager.onStart();
        }
    }

    void onResume() {
        if (routeGuideManager != null) {
            routeGuideManager.onResume();
        }
    }

    void onPause() {
        if (routeGuideManager != null) {
            routeGuideManager.onPause();
        }
    }

    void onStop() {
        if (routeGuideManager != null) {
            routeGuideManager.onStop();
        }
    }

    void onDestroy() {
        if (routeGuideManager != null) {
            routeGuideManager.onDestroy(false);
        }
    }

    void setNaviListener(IBNaviListener listener) {
        if (routeGuideManager != null) {
            routeGuideManager.setNaviListener(listener);
        }
    }

    void setNaviViewListener(IBNaviViewListener listener) {
        if (routeGuideManager != null) {
            routeGuideManager.setNaviViewListener(listener);
        }
    }

    void stopNavi() {
        if (routeGuideManager != null) {
            routeGuideManager.stopNavi();
        }
    }

    boolean isRoutePlanned() {
        return routePlanned;
    }

    boolean isInitialized() {
        return initialized;
    }

    BNRoutePlanInfos getRoutePlanInfos() {
        return BaiduNaviManagerFactory.getRoutePlanManager().getRoutePlanInfo();
    }
}
