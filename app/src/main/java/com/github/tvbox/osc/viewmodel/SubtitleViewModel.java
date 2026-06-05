package com.github.tvbox.osc.viewmodel;

import android.text.TextUtils;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.github.tvbox.osc.bean.SubtitleBean;
import com.github.tvbox.osc.bean.SubtitleData;
import com.github.tvbox.osc.ui.dialog.SearchSubtitleDialog;
import com.github.tvbox.osc.util.LOG;
import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.base.App;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubtitleViewModel extends ViewModel {

    public MutableLiveData<SubtitleData> searchResult;

    public SubtitleViewModel() {
        searchResult = new MutableLiveData<>();
    }

    public void searchResult(String title, int page) {
        searchResultFromAssrt(title, page);
    }

    public void getSearchResultSubtitleUrls(SubtitleBean subtitle) {
        getSearchResultSubtitleUrlsFromAssrt(subtitle);
    }

    public void getSubtitleUrl(SubtitleBean subtitle, SearchSubtitleDialog.SubtitleLoader subtitleLoader) {
        getSubtitleUrlFromAssrt(subtitle, subtitleLoader);
    }

    private void setSearchListData(List<SubtitleBean> data, boolean isNew, boolean isZip) {
        try {
            SubtitleData subtitleData = new SubtitleData();
            subtitleData.setSubtitleList(data);
            subtitleData.setIsNew(isNew);
            subtitleData.setIsZip(isZip);
            searchResult.postValue(subtitleData);
        } catch (Throwable e) {
            e.printStackTrace();
            searchResult.postValue(null);
            LOG.e(e);
        }
    }

    private int pagesTotal = -1;

    private void searchResultFromAssrt(String title, int page) {
        try {
            if (pagesTotal > 0 && page > pagesTotal) {
                setSearchListData(new ArrayList<>(), page <= 1, true);
                return;
            }
            if (page == 1) pagesTotal = -1;//第一页时 重置页大小
            String searchApiUrl = "https://secure.assrt.net/sub/";
            String finalSearchApiUrl = searchApiUrl + "?searchword=" + java.net.URLEncoder.encode(title, "UTF-8") + "&sort=rank&page=" + page + "&no_redir=1";
            new Thread(() -> {
                try {
                    okhttp3.Response resp = OkHttp.newCall(finalSearchApiUrl).execute();
                    String content = resp.body().string();
                    App.post(() -> {
                        try {
                            Document doc = Jsoup.parse(content);
                            Elements items = doc.select(".resultcard .sublist_box_title a.introtitle");
                            List<SubtitleBean> data = new ArrayList<>();
                            for (Element item : items) {
                                String t = item.attr("title");
                                String href = item.attr("href");
                                if (TextUtils.isEmpty(href)) continue;
                                SubtitleBean one = new SubtitleBean();
                                one.setName(t);
                                one.setUrl("https://assrt.net" + href);
                                one.setIsZip(true);
                                data.add(one);
                            }
                            setSearchListData(data, page <= 1, true);
                            Elements pages = doc.select(".pagelinkcard a");
                            if (pages.size() > 0) {
                                String[] ps = pages.last().text().split("/", 2);
                                if (ps.length == 2 && !TextUtils.isEmpty(ps[1])) {
                                    pagesTotal = Integer.valueOf(ps[1].trim());
                                }
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    App.post(() -> setSearchListData(null, page <= 1, true));
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            LOG.e(e);
        }
    }

    Pattern regexShooterFileOnclick = Pattern.compile("onthefly\\(\"(\\d+)\",\"(\\d+)\",\"([\\s\\S]*)\"\\)");

    private void getSearchResultSubtitleUrlsFromAssrt(SubtitleBean subtitle) {
        try {
            String url = subtitle.getUrl();
            new Thread(() -> {
                try {
                    okhttp3.Response resp = OkHttp.newCall(url).execute();
                    String content = resp.body().string();
                    App.post(() -> {
                        try {
                            List<SubtitleBean> data = new ArrayList<>();
                            Document doc = Jsoup.parse(content);
                            Elements items = doc.select("#detail-filelist .waves-effect");
                            if (items.size() > 0) {
                                for (Element item : items) {
                                    String onclick = item.attr("onclick");
                                    if (TextUtils.isEmpty(onclick)) continue;
                                    Matcher matcher = regexShooterFileOnclick.matcher(onclick);
                                    if (matcher.find()) {
                                        String fileUrl = String.format("https://secure.assrt.net/download/%s/-/%s/%s", matcher.group(1), matcher.group(2), matcher.group(3));
                                        SubtitleBean one = new SubtitleBean();
                                        Element name = item.selectFirst("#filelist-name");
                                        one.setName(name == null ? matcher.group(3) : name.text());
                                        one.setUrl(fileUrl);
                                        one.setIsZip(false);
                                        data.add(one);
                                    }
                                }
                                setSearchListData(data, true, false);
                            } else {
                                Element item = doc.selectFirst(".download a#btn_download");
                                String href = item.attr("href");
                                if (TextUtils.isEmpty(href)) setSearchListData(null, true, false);
                                String h2 = href.toLowerCase();
                                if (h2.endsWith("srt") || h2.endsWith("ass") || h2.endsWith("scc") || h2.endsWith("ttml")) {
                                    String fileUrl = "https://assrt.net" + href;
                                    SubtitleBean one = new SubtitleBean();
                                    String t = href.substring(href.lastIndexOf("/") + 1);
                                    one.setName(URLDecoder.decode(t));
                                    one.setUrl(fileUrl);
                                    one.setIsZip(false);
                                    data.add(one);
                                    setSearchListData(data, true, false);
                                } else {
                                    setSearchListData(null, true, false);
                                }
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                        }
                    });
                } catch (Exception e) {
                    App.post(() -> setSearchListData(null, true, true));
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
            LOG.e(e);
        }
    }

    private void getSubtitleUrlFromAssrt(SubtitleBean subtitle, SearchSubtitleDialog.SubtitleLoader subtitleLoader) {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.54 Safari/537.36";
        Request request = new Request.Builder()
                .url(subtitle.getUrl())
                .get()
                .addHeader("Referer", "https://secure.assrt.net")
                .addHeader("User-Agent", ua)
                .build();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .connectTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(true);
        OkHttpClient client = builder.build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                subtitle.setUrl(response.header("location"));
                subtitleLoader.loadSubtitle(subtitle);
            }
        });
    }
}
