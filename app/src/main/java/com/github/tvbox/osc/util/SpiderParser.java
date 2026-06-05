package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import java.util.ArrayList;
import java.util.List;

/**
 * Spider 结果解析工具类
 * 提供 XML/JSON 解析、数据转换等纯函数
 */
public class SpiderParser {

    private static final Gson gson = new Gson();

    /**
     * 解析 XML → AbsSortXml
     */
    public static AbsSortXml sortXml(String xml) {
        try {
            XStream xstream = new XStream(new DomDriver());
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsSortXml.class);
            xstream.ignoreUnknownElements();
            xstream.allowTypes(new Class[]{AbsSortXml.class});
            AbsSortXml data = (AbsSortXml) xstream.fromXML(xml);
            for (MovieSort.SortData sort : data.classes.sortList) {
                if (sort.filters == null) {
                    sort.filters = new ArrayList<>();
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 JSON → AbsSortXml（含筛选器）
     */
    public static AbsSortXml sortJson(String json, String sourceKey) {
        try {
            AbsSortJson sortJson = gson.fromJson(json, AbsSortJson.class);
            AbsSortXml data = sortJson.toAbsSortXml();
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            if (obj.has("filters")) {
                java.util.LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new java.util.LinkedHashMap<>();
                JsonObject filters = obj.getAsJsonObject("filters");
                for (String key : filters.keySet()) {
                    ArrayList<MovieSort.SortFilter> sortFilter = new ArrayList<>();
                    JsonElement one = filters.get(key);
                    if (one.isJsonObject()) {
                        sortFilter.add(getSortFilter(one.getAsJsonObject()));
                    } else {
                        for (JsonElement ele : one.getAsJsonArray()) {
                            sortFilter.add(getSortFilter(ele.getAsJsonObject()));
                        }
                    }
                    sortFilters.put(key, sortFilter);
                }
                for (MovieSort.SortData sort : data.classes.sortList) {
                    if (sortFilters.containsKey(sort.id) && sortFilters.get(sort.id) != null) {
                        sort.filters = sortFilters.get(sort.id);
                    }
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析单个筛选器对象
     */
    public static MovieSort.SortFilter getSortFilter(JsonObject obj) {
        MovieSort.SortFilter filter = new MovieSort.SortFilter();
        filter.key = DefaultConfig.safeJsonString(obj, "key", "");
        filter.name = DefaultConfig.safeJsonString(obj, "name", "");
        JsonElement val = obj.get("value");
        if (val != null) {
            if (val.isJsonArray()) {
                ArrayList<MovieSort.SortFilterValue> values = new ArrayList<>();
                for (JsonElement opt : val.getAsJsonArray()) {
                    JsonObject valueObj = opt.getAsJsonObject();
                    MovieSort.SortFilterValue filterValue = new MovieSort.SortFilterValue();
                    filterValue.n = DefaultConfig.safeJsonString(valueObj, "n", "");
                    filterValue.v = DefaultConfig.safeJsonString(valueObj, "v", "");
                    values.add(filterValue);
                }
                filter.values = values;
            } else {
                ArrayList<MovieSort.SortFilterValue> values = new ArrayList<>();
                MovieSort.SortFilterValue filterValue = new MovieSort.SortFilterValue();
                filterValue.n = val.getAsString();
                filterValue.v = val.getAsString();
                values.add(filterValue);
                filter.values = values;
            }
        }
        return filter;
    }

    /**
     * 最小化 JSON 字符串（去除空白和换行）
     */
    public static String tryMinifyJson(String jsonStr) {
        try {
            return gson.fromJson(jsonStr, JsonElement.class).toString();
        } catch (Throwable th) {
            return jsonStr;
        }
    }

    /**
     * 解析视频列表 URL 信息（#/$ 分隔格式）
     */
    public static void enrichVideoUrls(AbsXml data, String sourceKey) {
        if (data.movie == null || data.movie.videoList == null) return;
        for (Movie.Video video : data.movie.videoList) {
            if (video.urlBean == null || video.urlBean.infoList == null) continue;
            for (Movie.Video.UrlBean.UrlInfo urlInfo : video.urlBean.infoList) {
                String[] str = urlInfo.urls.contains("#") ? urlInfo.urls.split("#") : new String[]{urlInfo.urls};
                List<Movie.Video.UrlBean.UrlInfo.InfoBean> infoBeanList = new ArrayList<>();
                for (String s : str) {
                    String[] ss = s.split("\\$");
                    if (ss.length > 0) {
                        if (ss.length >= 2) {
                            infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1]));
                        } else {
                            infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size() + 1) + "", ss[0]));
                        }
                    }
                }
                urlInfo.beanList = infoBeanList;
            }
            if (video.sourceKey == null)
                video.sourceKey = sourceKey;
        }
    }
}
