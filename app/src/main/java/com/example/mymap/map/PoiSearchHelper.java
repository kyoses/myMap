package com.example.mymap.map;

import android.util.Log;

import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.search.core.PoiInfo;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.geocode.GeoCodeOption;
import com.baidu.mapapi.search.geocode.GeoCodeResult;
import com.baidu.mapapi.search.geocode.GeoCoder;
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption;
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult;
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener;
import com.baidu.mapapi.search.poi.PoiCitySearchOption;
import com.baidu.mapapi.search.poi.PoiDetailResult;
import com.baidu.mapapi.search.poi.PoiDetailSearchResult;
import com.baidu.mapapi.search.poi.PoiIndoorResult;
import com.baidu.mapapi.search.poi.PoiResult;
import com.baidu.mapapi.search.poi.PoiSearch;

import java.util.ArrayList;
import java.util.List;

/**
 * 地点检索与地理编码。
 *
 * <p>用的是百度地图检索 SDK，不依赖导航引擎 —— 所以即使导航 SDK 初始化失败
 * （例如 AK 没开通驾车导航权限），选点和搜索功能依然可用。
 */
final class PoiSearchHelper {

    private static final String TAG = "PoiSearch";

    /** 检索回调统一走主线程，SDK 的回调线程不固定。 */
    interface Callback<T> {
        void onSuccess(T result);

        void onFailure(String reason);
    }

    /** 候选地点。 */
    static final class Poi {
        final String name;
        final String address;
        final LatLng location;

        Poi(String name, String address, LatLng location) {
            this.name = name;
            this.address = address;
            this.location = location;
        }

        @Override
        public String toString() {
            return address == null || address.isEmpty() ? name : name + "（" + address + "）";
        }
    }

    private PoiSearchHelper() {
    }

    /**
     * 按关键词搜索地点。
     *
     * @param keyword 关键词，如「天安门」「望京SOHO」
     * @param city 限定城市，空表示不限
     */
    static void search(String keyword, String city, Callback<List<Poi>> callback) {
        if (keyword == null || keyword.trim().isEmpty()) {
            callback.onFailure("请输入地点名称");
            return;
        }
        PoiSearch searcher = PoiSearch.newInstance();
        searcher.setOnGetPoiSearchResultListener(new OnGetPoiSearchResultListener() {
            @Override
            public void onGetPoiResult(PoiResult result) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    callback.onFailure(describeError(result == null ? null : result.error));
                    return;
                }
                List<Poi> pois = new ArrayList<>();
                List<PoiInfo> all = result.getAllPoi();
                if (all != null) {
                    for (PoiInfo info : all) {
                        if (info == null || info.getLocation() == null) {
                            continue;
                        }
                        pois.add(new Poi(info.getName(), info.getAddress(), info.getLocation()));
                    }
                }
                if (pois.isEmpty()) {
                    callback.onFailure("没有找到匹配的地点");
                } else {
                    callback.onSuccess(pois);
                }
            }

            @Override
            public void onGetPoiDetailResult(PoiDetailResult result) {
                // 详情检索用不到
            }

            @Override
            public void onGetPoiDetailResult(PoiDetailSearchResult result) {
                // 详情检索用不到
            }

            @Override
            public void onGetPoiIndoorResult(PoiIndoorResult result) {
                // 室内检索用不到
            }
        });

        PoiCitySearchOption option = new PoiCitySearchOption();
        option.mKeyword = keyword.trim();
        option.mCity = (city == null || city.trim().isEmpty()) ? "全国" : city.trim();
        option.mPageNum = 0;
        option.mPageCapacity = 20;
        option.mIsReturnAddr = true;
        option.mScope = 2;

        if (!searcher.searchInCity(option)) {
            callback.onFailure("发起检索失败");
        }
    }

    /**
     * 地址解析：把「人民广场」这类文字地址转成坐标。
     *
     * @param address 地址
     * @param city 城市，空表示不限
     */
    static void geocode(String address, String city, Callback<Poi> callback) {
        if (address == null || address.trim().isEmpty()) {
            callback.onFailure("请输入地址");
            return;
        }
        GeoCoder coder = GeoCoder.newInstance();
        coder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult result) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR
                        || result.getLocation() == null) {
                    callback.onFailure("地址解析失败：" + describeError(
                            result == null ? null : result.error));
                    return;
                }
                callback.onSuccess(new Poi(address.trim(), "", result.getLocation()));
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                // 反向解析用不到
            }
        });

        GeoCodeOption option = new GeoCodeOption();
        option.address(address.trim());
        option.city((city == null || city.trim().isEmpty()) ? "全国" : city.trim());
        if (!coder.geocode(option)) {
            callback.onFailure("发起地址解析失败");
        }
    }

    /**
     * 反向地理编码：坐标转地址，用于地图长按选点后显示地名。
     */
    static void reverseGeocode(LatLng location, Callback<String> callback) {
        if (location == null) {
            callback.onFailure("坐标无效");
            return;
        }
        GeoCoder coder = GeoCoder.newInstance();
        coder.setOnGetGeoCodeResultListener(new OnGetGeoCoderResultListener() {
            @Override
            public void onGetGeoCodeResult(GeoCodeResult result) {
                // 正向解析用不到
            }

            @Override
            public void onGetReverseGeoCodeResult(ReverseGeoCodeResult result) {
                if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                    callback.onFailure("反向解析失败：" + describeError(
                            result == null ? null : result.error));
                    return;
                }
                String address = result.getAddress();
                if (address == null || address.trim().isEmpty()) {
                    callback.onFailure("该点没有地址信息");
                } else {
                    callback.onSuccess(address);
                }
            }
        });

        ReverseGeoCodeOption option = new ReverseGeoCodeOption();
        option.location(location);
        if (!coder.reverseGeoCode(option)) {
            callback.onFailure("发起反向解析失败");
        }
    }

    static void logError(String op, Throwable t) {
        Log.w(TAG, op + " failed", t);
    }

    /** 把检索错误码翻译成可读原因。 */
    private static String describeError(SearchResult.ERRORNO error) {
        if (error == null) {
            return "检索失败：没有返回结果";
        }
        switch (error) {
            case PERMISSION_UNFINISHED:
                return "检索权限未开通。该 AK 的「启动服务」里没有勾选检索，"
                        + "请到百度地图开放平台 → 应用管理 → 我的应用 → 点击 AK 设置 → "
                        + "勾选所需服务后重新申请 AK";
            case NO_ADVANCED_PERMISSION:
                return "检索的高级权限未开通（如行政区边界、深度检索），"
                        + "请检查 AK 勾选的检索权限项";
            case KEY_ERROR:
                return "AK 无效，请检查 local.properties 里的 baiduMapAk，"
                        + "并确认 AK 与包名 com.example.mymap、签名 SHA1 匹配";
            case NETWORK_TIME_OUT:
            case NETWORK_ERROR:
                return "检索请求失败，请检查网络后重试";
            case SEARCH_SERVER_INTERNAL_ERROR:
                return "检索服务返回异常。若使用 http 协议在 Android 9+ 上会触发此错误，"
                        + "请确认走的是 https";
            case RESULT_NOT_FOUND:
                return "没有找到匹配的地点，换个关键词试试";
            case AMBIGUOUS_KEYWORD:
                return "关键词过于模糊，请补充城市或更具体的名称";
            case SEARCH_OPTION_ERROR:
            case REQUEST_ERROR:
                return "检索参数有误，请重试";
            default:
                return "检索失败：" + error;
        }
    }
}
