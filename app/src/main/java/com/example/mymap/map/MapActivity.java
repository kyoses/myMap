package com.example.mymap.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.model.LatLng;
import com.baidu.navisdk.adapter.BaiduNaviManagerFactory;
import com.baidu.navisdk.adapter.IBNaviListener;
import com.baidu.navisdk.adapter.IBNaviViewListener;
import com.baidu.navisdk.adapter.struct.BNaviInfo;
import com.baidu.navisdk.adapter.struct.BNaviLocation;
import com.example.mymap.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 地图 + 导航页面：底图用 MapView 展示，导航用百度专业导航 View 覆盖在上面。
 */
public class MapActivity extends android.app.Activity implements NaviController.Callback {

    private static final int REQUEST_LOCATION = 1001;

    private MapView mapView;
    private BaiduMap baiduMap;
    private NaviController naviController;
    private LocationClient locationClient;

    private FrameLayout mapContainer;
    private FrameLayout naviContainer;
    private TextView statusView;
    private EditText destinationInput;
    private Button startButton;

    private double currentLat = 39.90882;
    private double currentLng = 116.39750;
    private String currentLocationName = "当前位置";
    private boolean mockNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mapContainer = findViewById(R.id.map_container);
        naviContainer = findViewById(R.id.navi_container);
        statusView = findViewById(R.id.status_text);
        destinationInput = findViewById(R.id.destination_input);
        startButton = findViewById(R.id.start_button);

        if (!initMap()) {
            return;
        }

        naviController = new NaviController(this, this);
        naviController.init(Environment.getExternalStorageDirectory().getAbsolutePath());

        startButton.setOnClickListener(v -> planAndNavigate());
        findViewById(R.id.locate_button).setOnClickListener(v -> requestLocation());
        findViewById(R.id.mock_button).setOnClickListener(v -> startMockNavigation());

        requestLocation();
    }

    /**
     * 创建地图视图。
     *
     * <p>百度地图的原生库按 ABI 分发：gradle 仓库里的导航版本只带 arm64-v8a 与
     * armeabi-v7a，在 x86/x86_64 设备（含模拟器）上加载会抛 UnsatisfiedLinkError。
     * 这里捕获后提示用户，避免整个页面崩掉。
     */
    private boolean initMap() {
        try {
            // 必须先初始化 SDK：隐私合规接口与 AK 校验都在这里，缺少任一项
            // 地图都无法初始化（官方文档要求 SDK 接口调用前先 setAgreePrivacy）。
            BaiduSdkInitializer.init(this, readAk());
            mapView = new MapView(this);
            baiduMap = mapView.getMap();
            mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            baiduMap.setMapStatus(MapStatusUpdateFactory.zoomTo(15.0f));
            return true;
        } catch (Throwable t) {
            setStatus("地图初始化失败：" + describe(t));
            startButton.setEnabled(false);
            return false;
        }
    }

    /** 把异常链拼成可读文本，便于直接从界面判断原因。 */
    private String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 5) {
            if (depth > 0) {
                sb.append(" <- ");
            }
            sb.append(cur.getClass().getSimpleName());
            if (cur.getMessage() != null && !cur.getMessage().trim().isEmpty()) {
                sb.append(": ").append(cur.getMessage().trim());
            }
            cur = cur.getCause();
            depth++;
        }
        String text = sb.toString();
        if (readAk().isEmpty()) {
            text += "｜未配置百度地图 AK（local.properties 的 baiduMapAk）";
        }
        return text;
    }

    private void requestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION);
            return;
        }
        startLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocation();
            } else {
                setStatus("未授予定位权限，使用默认坐标");
            }
        }
    }

    private void startLocation() {
        if (locationClient != null) {
            locationClient.restart();
            return;
        }
        try {
            locationClient = new LocationClient(this.getApplicationContext());
        } catch (Exception e) {
            setStatus("定位初始化失败: " + e.getMessage());
            return;
        }
        LocationClientOption option = new LocationClientOption();
        option.setCoorType("bd09ll");
        option.setIsNeedAddress(true);
        option.setIsNeedLocationDescribe(true);
        option.setOpenGps(true);
        option.setScanSpan(1000);
        locationClient.setLocOption(option);
        locationClient.registerLocationListener(new BDAbstractLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation location) {
                if (location == null) {
                    return;
                }
                currentLat = location.getLatitude();
                currentLng = location.getLongitude();
                if (location.getStreet() != null && !location.getStreet().isEmpty()) {
                    currentLocationName = location.getStreet();
                } else if (location.getLocationDescribe() != null
                        && !location.getLocationDescribe().isEmpty()) {
                    currentLocationName = location.getLocationDescribe();
                }
                if (baiduMap != null) {
                    baiduMap.setMapStatus(
                            MapStatusUpdateFactory.newLatLngZoom(
                                    new LatLng(currentLat, currentLng), 16.0f));
                }
                setStatus("定位: " + currentLocationName);
            }
        });
        locationClient.start();
    }

    /** 用输入框里的终点（"纬度,经度"）算路并进入导航。 */
    private void planAndNavigate() {
        String text = destinationInput.getText().toString().trim();
        double[] target = parseCoordinate(text);
        if (target == null) {
            Toast.makeText(this, "终点格式应为 纬度,经度，例如 39.90882,116.39750",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mockNavigation = false;
        setStatus("正在规划路线...");
        naviController.planRoute(currentLat, currentLng, currentLocationName,
                target[0], target[1], "终点");
    }

    /** 用默认终点进入模拟导航，用于在没有真实路线时验证导航界面。 */
    private void startMockNavigation() {
        mockNavigation = true;
        setStatus("正在规划路线...");
        naviController.planRoute(currentLat, currentLng, currentLocationName,
                39.90882, 116.39750, "北京天安门");
    }

    private double[] parseCoordinate(String text) {
        if (text.isEmpty()) {
            return null;
        }
        String[] parts = text.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            if (Math.abs(lat) > 90 || Math.abs(lng) > 180) {
                return null;
            }
            return new double[]{lat, lng};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void onInitSuccess() {
        setStatus("导航 SDK 初始化完成");
        String ak = readAk();
        if (!ak.isEmpty()) {
            naviController.loadAuth(ak, android.os.Build.SERIAL);
        } else {
            setStatus("未配置百度地图 AK，请在 gradle.properties 设置 baiduMapAk");
        }
        naviController.initTts(NaviTtsConfig.read(this));
        naviController.setNaviListener(new IBNaviListener() {
            @Override
            public void onNaviGuideEnd() {
                setStatus("导航结束");
            }

            @Override
            public void onArriveDestination() {
                setStatus("已到达目的地");
            }

            @Override
            public void onGuideInfoUpdate(BNaviInfo naviInfo,
                    com.baidu.navisdk.adapter.struct.GuidePanelMessage message) {
                if (naviInfo != null && naviInfo.getDistance() > 0) {
                    setStatus("距 " + naviInfo.getRoadName() + " "
                            + naviInfo.getDistance() + " 米");
                }
            }

            @Override
            public void onLocationChange(BNaviLocation naviLocation) {
                if (naviLocation == null) {
                    return;
                }
                currentLat = naviLocation.getLatitude();
                currentLng = naviLocation.getLongitude();
            }
        });
        naviController.setNaviViewListener(new IBNaviViewListener() {
            @Override
            public void onNaviBackClick() {
                finish();
            }
        });
    }

    @Override
    public void onInitFailed(String reason) {
        setStatus(reason);
    }

    @Override
    public void onRoutePlanned() {
        setStatus("路线规划成功");
        showGuideView(mockNavigation);
        naviController.startNavi();
    }

    @Override
    public void onRouteFailed(String reason) {
        setStatus(reason);
    }

    @Override
    public void onTtsReady() {
        setStatus("语音播报已就绪");
    }

    @Override
    public void onTtsUnavailable(String reason) {
        setStatus(reason);
    }

    /** 创建并挂载专业导航 View。 */
    private void showGuideView(boolean mock) {
        if (naviContainer.getChildCount() > 0) {
            return;
        }
        View guideView = naviController.createGuideView(mock);
        if (guideView == null) {
            setStatus("导航视图创建失败");
            return;
        }
        naviContainer.removeAllViews();
        naviContainer.addView(guideView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        startButton.setEnabled(false);
        setStatus(mock ? "模拟导航中" : "导航中");
    }

    private void setStatus(String text) {
        runOnUiThread(() -> statusView.setText(text));
    }

    /** 从 Manifest 的 meta-data 里读取百度地图 AK。 */
    private String readAk() {
        try {
            android.content.pm.ApplicationInfo info = getPackageManager()
                    .getApplicationInfo(getPackageName(),
                            android.content.pm.PackageManager.GET_META_DATA);
            Object value = info.metaData.get("com.baidu.lbsapi.API_KEY");
            return value == null ? "" : value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (naviController != null) {
            naviController.onStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (naviController != null) {
            naviController.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (naviController != null) {
            naviController.onPause();
        }
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (naviController != null) {
            naviController.onStop();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (locationClient != null) {
            locationClient.stop();
        }
        if (naviController != null) {
            naviController.onDestroy();
        }
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroy();
    }
}
