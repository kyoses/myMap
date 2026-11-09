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
                    callback.onFailure("检索失败，错误码 "
                            + (result == null ? "null" : result.error));
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
                    callback.onFailure("地址解析失败");
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
                    callback.onFailure("反向解析失败");
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
}
