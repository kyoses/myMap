package com.example.mymap.map;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
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
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MarkerOptions;
import com.baidu.mapapi.map.MyLocationData;
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
    private TextView destinationText;
    private TextView hintText;
    private EditText searchInput;
    private ListView resultList;
    private ArrayAdapter<String> resultAdapter;
    private Button startButton;

    private double currentLat = 39.90882;
    private double currentLng = 116.39750;
    private String currentLocationName = "当前位置";
    private boolean mockNavigation;
    /** 导航初始化失败的原因，null 表示还在初始化或已成功。 */
    private String naviInitError;
    /** 已选择的终点坐标，null 表示还没选。 */
    private LatLng destination;
    private String destinationName = "";
    /** 搜索结果与列表显示文本一一对应。 */
    private final List<PoiSearchHelper.Poi> searchResults = new ArrayList<>();
    /** 地图上的终点标记。 */
    private Marker destinationMarker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        mapContainer = findViewById(R.id.map_container);
        naviContainer = findViewById(R.id.navi_container);
        statusView = findViewById(R.id.status_text);
        destinationText = findViewById(R.id.destination_text);
        hintText = findViewById(R.id.hint_text);
        searchInput = findViewById(R.id.search_input);
        resultList = findViewById(R.id.result_list);
        startButton = findViewById(R.id.start_button);

        resultAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        resultList.setAdapter(resultAdapter);
        resultList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < searchResults.size()) {
                selectDestination(searchResults.get(position).location,
                        searchResults.get(position).name);
                hideResultList();
            }
        });

        if (!initMap()) {
            return;
        }

        naviController = new NaviController(this, this);
        // sdcardRootPath 是引擎数据目录名，SDK 内部用 getExternalFilesDir(name)
        // 拼到应用私有外部目录下；传绝对路径会导致目录层级异常。
        naviController.init("BaiduNavi");

        startButton.setOnClickListener(v -> planAndNavigate());
        findViewById(R.id.locate_button).setOnClickListener(v -> requestLocation());
        findViewById(R.id.mock_button).setOnClickListener(v -> startMockNavigation());
        findViewById(R.id.search_button).setOnClickListener(v -> doSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            doSearch();
            return true;
        });

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
            // 打开定位图层，图上才会画出定位点与精度圈。
            baiduMap.setMyLocationEnabled(true);
            // 长按地图选择终点。
            baiduMap.setOnMapLongClickListener(point -> onMapLongClick(point));
            return true;
        } catch (Throwable t) {
            setStatus("地图初始化失败：" + describe(t));
            startButton.setEnabled(false);
            return false;
        }
    }

    /**
     * 把定位结果交给地图的定位图层，这样才会在图上画出定位点与精度圈。
     *
     * <p>只移动地图中心是不够的 —— 定位图层需要单独 setMyLocationEnabled(true)
     * 并喂给 MyLocationData，否则图上不会出现点位标记。
     */
    private void updateMyLocation(BDLocation location) {
        if (baiduMap == null) {
            return;
        }
        try {
            MyLocationData data = new MyLocationData.Builder()
                    .latitude(location.getLatitude())
                    .longitude(location.getLongitude())
                    .accuracy(location.getRadius())
                    .direction(location.getDerect())
                    .speed(location.getSpeed())
                    .satellitesNum(location.getSatelliteNumber())
                    .build();
            baiduMap.setMyLocationData(data);
        } catch (Throwable t) {
            Log.w("MyMap", "更新定位图层失败", t);
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
            // 定位 SDK 同样要求先同意隐私政策，否则构造时会抛异常。
            BaiduSdkInitializer.init(this, readAk());
            locationClient = new LocationClient(this.getApplicationContext());
        } catch (Throwable e) {
            setStatus("定位初始化失败：" + describe(e));
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
                updateMyLocation(location);
                if (baiduMap != null) {
                    baiduMap.setMapStatus(
                            MapStatusUpdateFactory.newLatLngZoom(
                                    new LatLng(currentLat, currentLng), 16.0f));
                }
                setStatus("定位: " + currentLocationName);
            }

            @Override
            public void onLocDiagnosticMessage(int errorCode, int message,
                    String detail) {
                // 定位失败时 SDK 走这里，否则界面会一直停在旧状态，看不出原因。
                Log.w("MyMap", "定位诊断 errorCode=" + errorCode + " " + detail);
            }
        });
        locationClient.start();
    }

    /** 按输入框里的地点名称搜索候选，终点由用户在列表中选择。 */
    private void doSearch() {
        String keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入地点名称", Toast.LENGTH_SHORT).show();
            return;
        }
        setStatus("正在搜索「" + keyword + "」...");
        PoiSearchHelper.search(keyword, "", new PoiSearchHelper.Callback<>() {
            @Override
            public void onSuccess(List<PoiSearchHelper.Poi> pois) {
                searchResults.clear();
                searchResults.addAll(pois);
                resultAdapter.clear();
                for (PoiSearchHelper.Poi poi : pois) {
                    resultAdapter.add(poi.toString());
                }
                resultAdapter.notifyDataSetChanged();
                resultList.setVisibility(View.VISIBLE);
                setStatus("找到 " + pois.size() + " 个结果，点选一个作为终点");
            }

            @Override
            public void onFailure(String reason) {
                hideResultList();
                setStatus("搜索失败：" + reason);
                Toast.makeText(MapActivity.this, "搜索失败：" + reason,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /** 长按地图选点：反向解析出地址后作为终点。 */
    private void onMapLongClick(LatLng point) {
        if (point == null) {
            return;
        }
        setStatus("正在解析该点地址...");
        PoiSearchHelper.reverseGeocode(point, new PoiSearchHelper.Callback<>() {
            @Override
            public void onSuccess(String address) {
                selectDestination(point, address);
                setStatus("已选择终点：" + address);
            }

            @Override
            public void onFailure(String reason) {
                // 解析不出地址也允许直接用坐标当终点。
                selectDestination(point, "地图选点");
                setStatus("已选择该点（" + reason + "）");
            }
        });
    }

    /** 设定终点并在地图上打标记。 */
    private void selectDestination(LatLng point, String name) {
        if (point == null) {
            return;
        }
        destination = point;
        destinationName = name == null ? "" : name;
        destinationText.setText("终点：" + destinationName
                + (destinationName.isEmpty() ? "" : "（" + point.latitude + ", "
                + point.longitude + "）"));

        if (baiduMap != null) {
            if (destinationMarker != null) {
                destinationMarker.remove();
            }
            MarkerOptions options = new MarkerOptions()
                    .position(point)
                    .title(destinationName)
                    .icon(BitmapDescriptorFactory.fromResource(
                            R.drawable.ic_map_destination));
            destinationMarker = (Marker) baiduMap.addOverlay(options);
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 16.0f));
        }
    }

    private void hideResultList() {
        resultList.setVisibility(View.GONE);
    }

    /** 用已选择的终点算路并进入导航。 */
    private void planAndNavigate() {
        if (destination == null) {
            Toast.makeText(this, "请先选择终点：搜索地点，或长按地图选点",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!ensureNaviReady()) {
            return;
        }
        mockNavigation = false;
        setStatus("正在规划路线...");
        naviController.planRoute(currentLat, currentLng, currentLocationName,
                destination.latitude, destination.longitude,
                destinationName.isEmpty() ? "终点" : destinationName);
    }

    /** 进入模拟导航，用于在没有真实路线时验证导航界面。 */
    private void startMockNavigation() {
        if (!ensureNaviReady()) {
            return;
        }
        mockNavigation = true;
        setStatus("正在规划路线...");
        if (destination != null) {
            naviController.planRoute(currentLat, currentLng, currentLocationName,
                    destination.latitude, destination.longitude,
                    destinationName.isEmpty() ? "终点" : destinationName);
        } else {
            // 没选终点时用默认的天安门，保证模拟导航仍能跑通。
            naviController.planRoute(currentLat, currentLng, currentLocationName,
                    39.90882, 116.39750, "北京天安门");
        }
    }

    /**
     * 确认导航 SDK 已就绪，未就绪时给出具体原因而不是笼统的报错。
     */
    private boolean ensureNaviReady() {
        if (naviController != null && naviController.isInitialized()) {
            return true;
        }
        String reason = naviInitError != null ? naviInitError
                : "导航 SDK 仍在初始化（需要联网授权），请稍候重试";
        setStatus(reason);
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
        return false;
    }

    @Override
    public void onInitSuccess() {
        naviInitError = null;
        setStatus("导航 SDK 初始化完成");
        String ak = readAk();
        if (!ak.isEmpty()) {
            naviController.loadAuth(ak, android.os.Build.SERIAL);
        } else {
            setStatus("未配置百度地图 AK，请在 local.properties 里设置 baiduMapAk");
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
        naviInitError = reason;
        setStatus(reason);
        Log.e("MyMap", "导航初始化失败: " + reason);
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
        // TTS 只是可选增强，不要用它的提示覆盖掉导航初始化的错误信息。
        Log.i("MyMap", reason);
        if (naviController == null || !naviController.isInitialized()) {
            setStatus(reason);
        }
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
