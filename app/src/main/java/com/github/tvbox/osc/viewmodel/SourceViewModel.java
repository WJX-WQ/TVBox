package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.catvod.crawler.JsLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.AbsJson;
import com.github.tvbox.osc.bean.AbsSortJson;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;
import com.github.tvbox.osc.util.FileUtils;
import com.github.tvbox.osc.util.SpiderExecutor;
import com.github.tvbox.osc.util.SpiderParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import com.orhanobut.hawk.Hawk;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import org.apache.commons.lang3.BooleanUtils;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class SourceViewModel extends ViewModel {
    public MutableLiveData<AbsSortXml> sortResult;
    public MutableLiveData<AbsXml> listResult;
    public MutableLiveData<AbsXml> searchResult;
    public MutableLiveData<AbsXml> quickSearchResult;
    public MutableLiveData<AbsXml> detailResult;
    public MutableLiveData<JSONObject> playResult;
    private final SpiderExecutor spiderExecutor = new SpiderExecutor();
    public Gson gson;

    /** HTTP GET 辅助 — 后台线程执行，结果通过 App.post 回到主线程 */
    private void httpGet(String url, java.util.Map<String, String> params, java.util.function.Consumer<String> onSuccess, java.util.function.Consumer<String> onError) {
        new Thread(() -> {
            try {
                String finalUrl = url;
                if (params != null && !params.isEmpty()) {
                    okhttp3.HttpUrl.Builder hb = okhttp3.HttpUrl.parse(url).newBuilder();
                    for (java.util.Map.Entry<String, String> e : params.entrySet()) hb.addQueryParameter(e.getKey(), e.getValue());
                    finalUrl = hb.build().toString();
                }
                okhttp3.Response response = OkHttp.newCall(finalUrl).execute();
                String body = response.body() != null ? response.body().string() : "";
                com.github.tvbox.osc.base.App.post(() -> onSuccess.accept(body));
            } catch (Exception e) {
                com.github.tvbox.osc.base.App.post(() -> onError.accept(e.getMessage()));
            }
        }).start();
    }

    private void httpGet(String url, java.util.function.Consumer<String> onSuccess, java.util.function.Consumer<String> onError) {
        httpGet(url, null, onSuccess, onError);
    }

    public void initExecutor() {
        spiderExecutor.init();
    }

    public void execute(Runnable runnable) {
        spiderExecutor.execute(runnable);
    }

    public List<Runnable> shutdownNow() {
        return spiderExecutor.shutdownNow();
    }

    public void destroyExecutor() {
        spiderExecutor.destroy();
    }

    public SourceViewModel() {
        sortResult = new MutableLiveData<>();
        listResult = new MutableLiveData<>();
        searchResult = new MutableLiveData<>();
        quickSearchResult = new MutableLiveData<>();
        detailResult = new MutableLiveData<>();
        playResult = new MutableLiveData<>();
        gson=new Gson();
    }

    public static final ExecutorService spThreadPool = SpiderExecutor.spThreadPool;

    //homeContent缓存，最多存储5个sourceKey的AbsSortXml对象
    private static final Map<String, AbsSortXml> sortCache = new LinkedHashMap<String, AbsSortXml>(5, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Entry<String, AbsSortXml> eldest) {
            return size() > 5;
        }
    };
    // homeContent
    public void getSort(final String sourceKey) {
        LOG.i("echo--getSort-start");
        if (sourceKey == null) {
            sortResult.postValue(null);
            return;
        }

        // 优先检查缓存
        AbsSortXml cached = sortCache.get(sourceKey);
        if (cached != null) {
            LOG.i("echo--getSort-cached--"+sourceKey);
            int homeRec = Hawk.get(HawkConfig.HOME_REC, 0);
            boolean shouldUseCache = (homeRec != 1) || (cached.videoList != null && !cached.videoList.isEmpty());
            if (shouldUseCache) {
                sortResult.postValue(cached);
                return;
            }
        }

        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        final int type = sourceBean.getType();
        if (type == 3) {
            Runnable waitResponse = new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            Spider sp = ApiConfig.get().getCSP(sourceBean);
                            return sp.homeContent(true);
                        }
                    });
                    String sortJson = null;
                    try {
                        sortJson = future.get(20, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        future.cancel(true);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        if (sortJson != null) {
                            final AbsSortXml sortXml = sortJson(sortResult, sortJson);
                            if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                                AbsXml absXml = json(null, sortJson, sourceBean.getKey());
                                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                                    sortXml.videoList = absXml.movie.videoList;
                                    sortResult.postValue(sortXml);
                                    sortCache.put(sourceKey, sortXml);
                                } else {
                                    getHomeRecList(sourceBean, null, new HomeRecCallback() {
                                        @Override
                                        public void done(List<Movie.Video> videos) {
                                            sortXml.videoList = videos;
                                            sortResult.postValue(sortXml);
                                            sortCache.put(sourceKey, sortXml);
                                        }
                                    });
                                }
                            } else {
                                sortResult.postValue(sortXml);
                                sortCache.put(sourceKey, sortXml);
                            }
                        } else {
                            sortResult.postValue(null);
                        }
                        try {
                            executor.shutdown();
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                }
            };
            spThreadPool.execute(waitResponse);
        } else if (type == 0 || type == 1) {
            httpGet(sourceBean.getApi(), body -> {
                            AbsSortXml sortXml = null;
                            if (type == 0) {
                                String xml = response.body();
                                sortXml = sortXml(sortResult, xml);
                            } else if (type == 1) {
                                String json = response.body();
                                sortXml = sortJson(sortResult, json);
                            }
                            if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1 && sortXml.list != null && sortXml.list.videoList != null && sortXml.list.videoList.size() > 0) {
                                ArrayList<String> ids = new ArrayList<>();
                                for (Movie.Video vod : sortXml.list.videoList) {
                                    ids.add(vod.id);
                                }
                                final AbsSortXml finalSortXml = sortXml;
                                getHomeRecList(sourceBean, ids, new HomeRecCallback() {
                                    @Override
                                    public void done(List<Movie.Video> videos) {
                                        finalSortXml.videoList = videos;
                                        sortResult.postValue(finalSortXml);
                                        sortCache.put(sourceKey, finalSortXml);
                                    }
                                });
                            } else {
                                sortResult.postValue(sortXml);
                                sortCache.put(sourceKey, sortXml);
                            }
                        }

            }, error -> sortResult.postValue(null));
        } else if (type == 4) {
            String extend=sourceBean.getExt();
            extend=getFixUrl(extend);
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("filter", "true");
            params.put("extend", extend);
            httpGet(sourceBean.getApi(), params, body -> {
                            String sortJson = body;
                            if (sortJson != null) {
                                final AbsSortXml sortXml = sortJson(sortResult, sortJson);
                                if (sortXml != null && Hawk.get(HawkConfig.HOME_REC, 0) == 1) {
                                    AbsXml absXml = json(null, sortJson, sourceBean.getKey());
                                    if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                                        sortXml.videoList = absXml.movie.videoList;
                                        sortResult.postValue(sortXml);
                                        sortCache.put(sourceKey, sortXml);
                                    } else {
                                        getHomeRecList(sourceBean, null, new HomeRecCallback() {
                                            @Override
                                            public void done(List<Movie.Video> videos) {
                                                sortXml.videoList = videos;
                                                sortResult.postValue(sortXml);
                                                sortCache.put(sourceKey, sortXml);
                                            }
                                        });
                                    }
                                } else {
                                    sortResult.postValue(sortXml);
                                    sortCache.put(sourceKey, sortXml);
                                }
                            } else {
                                sortResult.postValue(null);
                            }
                        }

            }, error -> sortResult.postValue(null));
        } else {
            sortResult.postValue(null);
        }
    }

    // categoryContent
    public void getList(MovieSort.SortData sortData, int page) {
        SourceBean homeSourceBean = ApiConfig.get().getHomeSourceBean();
        int type = homeSourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        Spider sp = ApiConfig.get().getCSP(homeSourceBean);
                        json(listResult, sp.categoryContent(sortData.id, page + "", true, sortData.filterSelect), homeSourceBean.getKey());
                    } catch (Throwable th) {
                        th.printStackTrace();
                        listResult.postValue(null);
                    }
                }
            });
        } else if (type == 0 || type == 1) {
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("ac", type == 0 ? "videolist" : "detail");
            params.put("t", sortData.id);
            params.put("pg", page + "");
            if (sortData.filterSelect != null) {
                for (java.util.Map.Entry<String, String> e : sortData.filterSelect.entrySet()) params.put(e.getKey(), e.getValue());
            }
            params.put("f", (sortData.filterSelect == null || sortData.filterSelect.size() <= 0) ? "" : new JSONObject(sortData.filterSelect).toString());
            httpGet(homeSourceBean.getApi(), params, body -> {
            if (type == 0) {
                xml(listResult, body, homeSourceBean.getKey());
            } else {
                json(listResult, body, homeSourceBean.getKey());
            }
        }, error -> listResult.postValue(null));
        } else if (type == 4) {
            String ext = "";
            if (sortData.filterSelect != null && sortData.filterSelect.size() > 0) {
                try {
                    LOG.i(new JSONObject(sortData.filterSelect).toString());
                    ext = Base64.encodeToString(new JSONObject(sortData.filterSelect).toString().getBytes("UTF-8"), Base64.DEFAULT | Base64.NO_WRAP);
                    LOG.i(ext);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("ac", "detail");
            p.put("filter", "true");
            p.put("t", sortData.id);
            p.put("pg", page + "");
            p.put("ext", ext);
            httpGet(homeSourceBean.getApi(), p, body -> {
                json(listResult, body, homeSourceBean.getKey());
            }, error -> listResult.postValue(null));
        } else {
            listResult.postValue(null);
        }
    }

    interface HomeRecCallback {
        void done(List<Movie.Video> videos);
    }

    //    homeVideoContent
    void getHomeRecList(SourceBean sourceBean, ArrayList<String> ids, HomeRecCallback callback) {
        int type = sourceBean.getType();
        if (type == 3) {
            Runnable waitResponse = new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() throws Exception {
                            Spider sp = ApiConfig.get().getCSP(sourceBean);
                            return sp.homeVideoContent();
                        }
                    });
                    String sortJson = null;
                    try {
                        sortJson = future.get(15, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        e.printStackTrace();
                        future.cancel(true);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } finally {
                        if (sortJson != null) {
                            AbsXml absXml = json(null, sortJson, sourceBean.getKey());
                            if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                                callback.done(absXml.movie.videoList);
                            } else {
                                callback.done(null);
                            }
                        } else {
                            callback.done(null);
                        }
                        try {
                            executor.shutdown();
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    }
                }
            };
            spThreadPool.execute(waitResponse);
        } else if (type == 0 || type == 1) {
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("ac", sourceBean.getType() == 0 ? "videolist" : "detail");
            p.put("ids", TextUtils.join(",", ids));
            httpGet(sourceBean.getApi(), p, body -> {
                AbsXml absXml;
                if (sourceBean.getType() == 0) {
                    absXml = xml(null, body, sourceBean.getKey());
                } else {
                    absXml = json(null, body, sourceBean.getKey());
                }
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null) {
                    callback.done(absXml.movie.videoList);
                } else {
                    callback.done(null);
                }
            }, error -> callback.done(null));
        } else {
            callback.done(null);
        }
    }

    // detailContent
    public void getDetail(String sourceKey, String urlid) {

        if (urlid.startsWith("push://") && ApiConfig.get().getSource("push_agent") != null) {
            String pushUrl = urlid.substring(7);
            if (pushUrl.startsWith("b64:")) {
                try {
                    pushUrl = new String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                pushUrl = URLDecoder.decode(pushUrl);
            }
            sourceKey = "push_agent";
            urlid = pushUrl;
        }
        String id = urlid;
    
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        if (sourceBean == null) {
            detailResult.postValue(null);
            Log.e("sourceBean", "get sourceBean got null, this should not be happended, maybe apiconfig get from http failed and use cache, sourceKey is " + sourceKey);
            return;
        }
        int type = sourceBean.getType();
        if (type == 3) {
            spThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String> future = executor.submit(new Callable<String>() {
                        @Override
                        public String call() {
                            Spider sp = ApiConfig.get().getCSP(sourceBean);
                            List<String> ids = new ArrayList<>();
                            ids.add(id);
                            try {
                                return sp.detailContent(ids);
                            } catch (Exception e) {
                                LOG.i("echo--getDetail--error: " + e.getMessage());
                                return "";
                            }
                        }
                    });

                    String json = null;
                    try {
                        json = future.get(15, TimeUnit.SECONDS);
                        LOG.i("echo--getDetail--result:" + json);
                    } catch (TimeoutException e) {
                        LOG.i("echo--getDetail--timeout");
                        future.cancel(true);
                    } catch (Exception e) {
                        LOG.i("echo--getDetail--error: " + e.getMessage());
                    } finally {
                        json(detailResult, json, sourceBean.getKey());
                        executor.shutdown();
                    }
                }
            });
        } else if (type == 0 || type == 1|| type == 4) {
            String extend=sourceBean.getExt();
            extend=getFixUrl(extend);
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("ac", type == 0 ? "videolist" : "detail");
            p.put("ids", id);
            p.put("extend", extend);
            httpGet(sourceBean.getApi(), p, body -> {
                if (type == 0) {
                    xml(detailResult, body, sourceBean.getKey());
                } else {
                    json(detailResult, body, sourceBean.getKey());
                }
            }, error -> json(detailResult, "", sourceBean.getKey()));
        } else {
            detailResult.postValue(null);
        }
    }

    // searchContent
    public void getSearch(String sourceKey, String wd) {
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        int type = sourceBean.getType();
        if (type == 3) {
            try {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                String search = sp.searchContent(wd, false);
                if (!TextUtils.isEmpty(search)) {
                    json(searchResult, search, sourceBean.getKey());
                } else {
                    json(searchResult, "", sourceBean.getKey());
                }
            } catch (Throwable th) {
                th.printStackTrace();
                json(searchResult, "", sourceBean.getKey());
            }
        } else if (type == 0 || type == 1) {
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("wd", wd);
            if (type == 1) p.put("ac", "detail");
            httpGet(sourceBean.getApi(), p, body -> {
                if (type == 0) {
                    xml(searchResult, body, sourceBean.getKey());
                } else {
                    json(searchResult, body, sourceBean.getKey());
                }
            }, error -> EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)));
        } else if (type == 4) {
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("wd", wd);
            p.put("ac", "detail");
            p.put("quick", "false");
            httpGet(sourceBean.getApi(), p, body -> {
                json(searchResult, body, sourceBean.getKey());
            }, error -> EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)));
        } else {
            searchResult.postValue(null);
        }
    }

    // searchContent
    public void getQuickSearch(String sourceKey, String wd) {
        SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
        int type = sourceBean.getType();
        if (type == 3) {
            try {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                json(quickSearchResult, sp.searchContent(wd, true), sourceBean.getKey());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        } else if (type == 0 || type == 1) {
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("wd", wd);
            if (type == 1) p.put("ac", "detail");
            httpGet(sourceBean.getApi(), p, body -> {
                if (type == 0) {
                    xml(quickSearchResult, body, sourceBean.getKey());
                } else {
                    json(quickSearchResult, body, sourceBean.getKey());
                }
            }, error -> EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null)));
        } else if (type == 4) {
            java.util.Map<String, String> p = new java.util.HashMap<>();
            p.put("wd", wd);
            p.put("ac", "detail");
            p.put("quick", "true");
            httpGet(sourceBean.getApi(), p, body -> {
                json(quickSearchResult, body, sourceBean.getKey());
            }, error -> EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null)));
        } else {
            quickSearchResult.postValue(null);
        }
    }

    // playerContent
    //开销会不会太大了 参考 FongMi 写法优化 获取播放地址代码
    public ExecutorService threadPoolGetPlay = null;

    public void getPlay(String sourceKey, String playFlag, String progressKey, String url, String subtitleKey) {
        if (threadPoolGetPlay != null) threadPoolGetPlay.shutdownNow();
        threadPoolGetPlay = Executors.newFixedThreadPool(2);
        Callable<JSONObject> callable = () -> {
            if (Thread.currentThread().isInterrupted()) return null;
            SourceBean sourceBean = ApiConfig.get().getSource(sourceKey);
            int type = sourceBean.getType();
            JSONObject result = null;
            if (type == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                String json = sp.playerContent(playFlag, url, ApiConfig.get().getVipParseFlags());
                result = new JSONObject(json);
            } else if (type == 0 || type == 1) {
                result = new JSONObject();
                String playUrl = sourceBean.getPlayerUrl().trim();
                boolean parse = DefaultConfig.isVideoFormat(url) && playUrl.isEmpty();
                result.put("parse", BooleanUtils.toInteger(!parse));
                result.put("url", url);
                result.put("playUrl", playUrl);
                //直接就有
            } else if (type == 4) {
                String extend=sourceBean.getExt();
                extend=getFixUrl(extend);
                String json = com.github.catvod.net.OkHttp.string(sourceBean.getApi() + "?play=" + java.net.URLEncoder.encode(url, "UTF-8") + "&flag=" + playFlag + "&extend=" + java.net.URLEncoder.encode(extend, "UTF-8"));
                result = new JSONObject(json);
            }
            if (result != null) {
                result.put("key", url);
                result.put("proKey", progressKey);
                result.put("subtKey", subtitleKey);
                if (!result.has("flag"))
                    result.put("flag", playFlag);
            }
            return result;
        };
        threadPoolGetPlay.execute(() -> {
            Future<JSONObject> future = threadPoolGetPlay.submit(callable);
            try {
                JSONObject jsonObject = future.get(15, TimeUnit.SECONDS);
                playResult.postValue(jsonObject);
            } catch (Throwable e) {
                e.printStackTrace();
                playResult.postValue(null);
            }
        });
    }
    private static final ConcurrentHashMap<String, String> extendCache = new ConcurrentHashMap<>();
    private String getFixUrl(final String extend) {
        if(!extend.startsWith("http"))return extend;
        final String key = MD5.string2MD5(extend);
        if (extendCache.containsKey(key)) {
            LOG.i("echo-getFixUrl Cache");
            return extendCache.get(key);
        }
        LOG.i("echo-getFixUrl load");
        Future<String> future = spThreadPool.submit(new Callable<String>() {
            @Override
            public String call() {
                String result = extend;
                if (extend.startsWith("http://127.0.0.1")) {
                    String path = extend.replaceAll("^http.+/file/", FileUtils.getRootPath() + "/");
                    path = path.replaceAll("localhost/", "/");
                    result = FileUtils.readFileToString(path, "UTF-8");
                    result = tryMinifyJson(result);
                    extendCache.putIfAbsent(key, result);
                } else if (extend.startsWith("http")) {
                    result = OkHttpUtil.string(extend, null);
                    if (!result.isEmpty()) {
                        result = tryMinifyJson(result);
                        extendCache.putIfAbsent(key, result);
                    }
                }
                return result;
            }
        });

        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            te.printStackTrace();
            future.cancel(true);
            return extend;
        } catch (Exception e) {
            e.printStackTrace();
            return extend;
        }
    }

    private String tryMinifyJson(String raw) {
        return SpiderParser.tryMinifyJson(raw);
    }

    private MovieSort.SortFilter getSortFilter(JsonObject obj) {
        return SpiderParser.getSortFilter(obj);
    }

    private AbsSortXml sortJson(MutableLiveData<AbsSortXml> result, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            AbsSortJson sortJson = gson.fromJson(obj, new TypeToken<AbsSortJson>() {
            }.getType());
            AbsSortXml data = sortJson.toAbsSortXml();
            try {
                if (obj.has("filters")) {
                    LinkedHashMap<String, ArrayList<MovieSort.SortFilter>> sortFilters = new LinkedHashMap<>();
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
            } catch (Throwable th) {

            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }

    private AbsSortXml sortXml(MutableLiveData<AbsSortXml> result, String xml) {
        return SpiderParser.sortXml(xml);
    }

    private void absXml(AbsXml data, String sourceKey) {
        SpiderParser.enrichVideoUrls(data, sourceKey);
    }

    private AbsXml checkPush(AbsXml data) {
        if (data.movie != null && data.movie.videoList != null && data.movie.videoList.size() > 0) {
            Movie.Video video = data.movie.videoList.get(0);
            if (video != null && video.urlBean != null && video.urlBean.infoList != null && video.urlBean.infoList.size() > 0) {
                for (int i = 0; i < video.urlBean.infoList.size(); i++) {
                    Movie.Video.UrlBean.UrlInfo urlinfo = video.urlBean.infoList.get(i);
                    if (urlinfo != null && urlinfo.beanList != null && !urlinfo.beanList.isEmpty()) {
                        for (Movie.Video.UrlBean.UrlInfo.InfoBean infoBean : urlinfo.beanList) {
                            if (infoBean.url.startsWith("push://")) {
                                String pushUrl = infoBean.url.substring(7);
                                if (pushUrl.startsWith("b64:")) {
                                    try {
                                        pushUrl = new String(Base64.decode(pushUrl.substring(4), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    pushUrl = URLDecoder.decode(pushUrl);
                                }

                                final AbsXml[] resData = {null};

                                final CountDownLatch countDownLatch = new CountDownLatch(1);
                                ExecutorService threadPool = Executors.newSingleThreadExecutor();
                                String finalPushUrl = pushUrl;
                                threadPool.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        SourceBean sb = ApiConfig.get().getSource("push_agent");
                                        if (sb == null) {
                                            countDownLatch.countDown();
                                            return;
                                        }
                                        if (sb.getType() == 4) {
                                            java.util.Map<String, String> p = new java.util.HashMap<>();
                                            p.put("ac", "detail");
                                            p.put("ids", finalPushUrl);
                                            httpGet(sb.getApi(), p, res -> {
                                                if (!TextUtils.isEmpty(res)) {
                                                    try {
                                                        AbsJson absJson = gson.fromJson(res, new TypeToken<AbsJson>() {
                                                        }.getType());
                                                        resData[0] = absJson.toAbsXml();
                                                        absXml(resData[0], sb.getKey());
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                countDownLatch.countDown();
                                            }, error -> countDownLatch.countDown());
                                        } else {
                                            try {
                                                Spider sp = ApiConfig.get().getCSP(sb);
                                                //   ApiConfig.get().setPlayJarKey(sb.getJar());
                                                List<String> ids = new ArrayList<>();
                                                ids.add(finalPushUrl);
                                                String res = sp.detailContent(ids);
                                                if (!TextUtils.isEmpty(res)) {
                                                    try {
                                                        AbsJson absJson = gson.fromJson(res, new TypeToken<AbsJson>() {
                                                        }.getType());
                                                        resData[0] = absJson.toAbsXml();
                                                        absXml(resData[0], sb.getKey());
                                                    } catch (Exception e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            } catch (Throwable th) {
                                                th.printStackTrace();
                                            }
                                            countDownLatch.countDown();
                                        }
                                    }
                                });
                                try {
                                    countDownLatch.await(15, TimeUnit.SECONDS);
                                    threadPool.shutdown();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                if (resData[0] != null) {
                                    AbsXml res = resData[0];
                                    if (res.movie != null && res.movie.videoList != null && res.movie.videoList.size() > 0) {
                                        Movie.Video resVideo = res.movie.videoList.get(0);
                                        if (resVideo != null && resVideo.urlBean != null && resVideo.urlBean.infoList != null && resVideo.urlBean.infoList.size() > 0) {
                                            if (urlinfo.beanList.size() == 1) {
                                                video.urlBean.infoList.remove(i);
                                            } else {
                                                urlinfo.beanList.remove(infoBean);
                                            }
                                            for (Movie.Video.UrlBean.UrlInfo resUrlinfo : resVideo.urlBean.infoList) {
                                                if (resUrlinfo != null && resUrlinfo.beanList != null && !resUrlinfo.beanList.isEmpty()) {
                                                    video.urlBean.infoList.add(resUrlinfo);
                                                }
                                            }
                                            video.sourceKey = "push_agent";
                                            return data;
                                        }
                                    }
                                }
                                infoBean.name = "解析失败 >>> " + infoBean.name;
                            }
                        }
                    }
                }
            }
        }
        return data;
    }

    public void checkThunder(AbsXml data, int index) {
        boolean thunderParse = false;
        if (data.movie != null && data.movie.videoList != null && data.movie.videoList.size() == 1) {
            Movie.Video video = data.movie.videoList.get(0);
            if (video != null && video.urlBean != null && video.urlBean.infoList != null) {
            	boolean hasThunder=false;
                thunderLoop:
                for (int idx=0;idx<video.urlBean.infoList.size();idx++) {
                    Movie.Video.UrlBean.UrlInfo urlInfo = video.urlBean.infoList.get(idx);
                    for (Movie.Video.UrlBean.UrlInfo.InfoBean infoBean : urlInfo.beanList) {
                        if(Thunder.isSupportUrl(infoBean.url)){
                            hasThunder=true;
                            break thunderLoop;
                        }
                    }
                }
                if (hasThunder) {
                    thunderParse = true;
                    Thunder.parse(App.getInstance(), video.urlBean, new Thunder.ThunderCallback() {
                        @Override
                        public void status(int code, String info) {
                            if (code >= 0) {
                                LOG.i(info);
                            } else {
                                video.urlBean.infoList.get(0).beanList.get(0).name = info;
                                detailResult.postValue(data);
                            }
                        }

                        @Override
                        public void list(Map<Integer, String> urlMap) {
                            for (int key : urlMap.keySet()) {
                                String playList=urlMap.get(key);
                                video.urlBean.infoList.get(key).urls = playList;
                                String[] str = playList.split("#");
                                List<Movie.Video.UrlBean.UrlInfo.InfoBean> infoBeanList = new ArrayList<>();
                                for (String s : str) {
                                    if (s.contains("$")) {
                                        String[] ss = s.split("\\$");

                                        if (ss.length > 0) {
                                            if (ss.length >= 2) {
                                                infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean(ss[0], ss[1]));
                                            } else {
                                                infoBeanList.add(new Movie.Video.UrlBean.UrlInfo.InfoBean((infoBeanList.size() + 1) + "", ss[0]));
                                            }
                                        }
                                    }
                                }
                                video.urlBean.infoList.get(key).beanList = infoBeanList;
                            }
                            detailResult.postValue(data);
                        }

                        @Override
                        public void play(String url) {

                        }
                    });
                }
            }
        }
        if (!thunderParse && index==0) {
            detailResult.postValue(data);
        }
    }

    private AbsXml xml(MutableLiveData<AbsXml> result, String xml, String sourceKey) {
        try {
            XStream xstream = new XStream(new DomDriver());//创建Xstram对象
            xstream.autodetectAnnotations(true);
            xstream.processAnnotations(AbsXml.class);
            xstream.ignoreUnknownElements();
            xstream.allowTypes(new Class[]{AbsXml.class});
            if (xml.contains("<year></year>")) {
                xml = xml.replace("<year></year>", "<year>0</year>");
            }
            if (xml.contains("<state></state>")) {
                xml = xml.replace("<state></state>", "<state>0</state>");
            }
            AbsXml data = (AbsXml) xstream.fromXML(xml);
            absXml(data, sourceKey);
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data));
            } else if (result != null) {
                if (result == detailResult) {
                    data = checkPush(data);
                    checkThunder(data, 0);
                } else {
                    result.postValue(data);
                }
            }
            return data;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            } else if (result != null) {
                result.postValue(null);
            }
            return null;
        }
    }

    private AbsXml json(MutableLiveData<AbsXml> result, String json, String sourceKey) {
        try {
            // 测试数据
            /*json = "{\n" +
                    "\t\"list\": [{\n" +
                    "\t\t\"vod_id\": \"137133\",\n" +
                    "\t\t\"vod_name\": \"磁力测试\",\n" +
                    "\t\t\"vod_pic\": \"https:/img9.doubanio.com/view/photo/s_ratio_poster/public/p2656327176.webp\",\n" +
                    "\t\t\"type_name\": \"剧情 / 爱情 / 古装\",\n" +
                    "\t\t\"vod_year\": \"2022\",\n" +
                    "\t\t\"vod_area\": \"中国大陆\",\n" +
                    "\t\t\"vod_remarks\": \"40集全\",\n" +
                    "\t\t\"vod_actor\": \"刘亦菲\",\n" +
                    "\t\t\"vod_director\": \"杨阳\",\n" +
                    "\t\t\"vod_content\": \"　　在钱塘开茶铺的赵盼儿（刘亦菲 饰）惊闻未婚夫、新科探花欧阳旭（徐海乔 饰）要另娶当朝高官之女，不甘命运的她誓要上京讨个公道。在途中她遇到了出自权门但生性正直的皇城司指挥顾千帆（陈晓 饰），并卷入江南一场大案，两人不打不相识从而结缘。赵盼儿凭借智慧解救了被骗婚而惨遭虐待的“江南第一琵琶高手”宋引章（林允 饰）与被苛刻家人逼得离家出走的豪爽厨娘孙三娘（柳岩 饰），三位姐妹从此结伴同行，终抵汴京，见识世间繁华。为了不被另攀高枝的欧阳旭从东京赶走，赵盼儿与宋引章、孙三娘一起历经艰辛，将小小茶坊一步步发展为汴京最大的酒楼，揭露了负心人的真面目，收获了各自的真挚感情和人生感悟，也为无数平凡女子推开了一扇平等救赎之门。\",\n" +
                    "\t\t\"vod_play_from\": \"磁力测试\",\n" +
                    "\t\t\"vod_play_url\": \"0$magnet:?xt=urn:btih:9e9358b946c427962533472efdd2efd9e9e38c67&dn=%e9%98%b3%e5%85%89%e7%94%b5%e5%bd%b1www.ygdy8.com.%e7%83%ad%e8%a1%80.2022.BD.1080P.%e9%9f%a9%e8%af%ad%e4%b8%ad%e8%8b%b1%e5%8f%8c%e5%ad%97.mkv&tr=udp%3a%2f%2ftracker.opentrackr.org%3a1337%2fannounce&tr=udp%3a%2f%2fexodus.desync.com%3a6969%2fannounce\"\n" +
                    "\t}]\n" +
                    "}";*/
            AbsJson absJson = gson.fromJson(json, new TypeToken<AbsJson>() {
            }.getType());
            AbsXml data = absJson.toAbsXml();
            absXml(data, sourceKey);
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, data));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, data));
            } else if (result != null) {
                if (result == detailResult) {
                    data = checkPush(data);
                    checkThunder(data, 0);
                } else {
                    result.postValue(data);
                }
            }
            return data;
        } catch (Exception e) {
            if (searchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_SEARCH_RESULT, null));
            } else if (quickSearchResult == result) {
                EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_QUICK_SEARCH_RESULT, null));
            } else if (result != null) {
                result.postValue(null);
            }
            return null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        closeExecutor(threadPoolGetPlay);

    }

    private void closeExecutor(ExecutorService executorService) {
        if (executorService != null) {
            try {
                executorService.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
    }
}
