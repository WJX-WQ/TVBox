package com.github.tvbox.osc.util.webview;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.VideoParseRuler;
import com.orhanobut.hawk.Hawk;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import me.jessyan.autosize.utils.AutoSize;

/**
 * WebView 管理器 — 管理系统 WebView 的初始化、视频嗅探和生命周期
 */
public class WebViewManager {

    public interface Callback {
        void onVideoFound(String url, HashMap<String, String> headers);
    }

    private final Context context;
    private final Handler handler;
    private final VodController controller;
    private final SourceBean sourceBean;
    private final Callback callback;

    private WebView mSysWebView;
    private String webUrl;
    private String webUserAgent;
    private Map<String, String> webHeaderMap;
    private final Map<String, Boolean> loadedUrls = new HashMap<>();
    private final LinkedList<String> loadFoundVideoUrls = new LinkedList<>();
    private final HashMap<String, HashMap<String, String>> loadFoundVideoUrlsHeader = new HashMap<>();
    private final AtomicInteger loadFoundCount = new AtomicInteger(0);

    public WebViewManager(Context context, Handler handler, VodController controller,
                          SourceBean sourceBean, Callback callback) {
        this.context = context;
        this.handler = handler;
        this.controller = controller;
        this.sourceBean = sourceBean;
        this.callback = callback;
    }

    public void loadUrl(String url, String userAgent, Map<String, String> headers) {
        this.webUrl = url;
        this.webUserAgent = userAgent;
        this.webHeaderMap = headers;
        if (mSysWebView == null) {
            mSysWebView = new MyWebView(context);
            configWebViewSys(mSysWebView);
        }
        doLoadUrl();
    }

    private void doLoadUrl() {
        final String url = webUrl;
        final String userAgent = webUserAgent;
        final Map<String, String> headers = webHeaderMap;
        App.post(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                if (userAgent != null) {
                    mSysWebView.getSettings().setUserAgentString(userAgent);
                }
                if (headers != null) {
                    mSysWebView.loadUrl(url, headers);
                } else {
                    mSysWebView.loadUrl(url);
                }
            }
        });
    }

    public void stopLoad(boolean destroy) {
        App.post(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.loadUrl("about:blank");
                if (destroy) {
                    mSysWebView.removeAllViews();
                    mSysWebView.destroy();
                    mSysWebView = null;
                }
            }
        });
    }

    public void destroy() {
        stopLoad(true);
        loadedUrls.clear();
        loadFoundVideoUrls.clear();
        loadFoundVideoUrlsHeader.clear();
    }

    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (context instanceof Activity)
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) context);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configWebViewSys(WebView webView) {
        if (webView == null) return;

        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400)
                : new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        if (context instanceof Activity) {
            ((Activity) context).addContentView(webView, layoutParams);
        }

        WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        settings.setBlockNetworkImage(!Hawk.get(HawkConfig.DEBUG_OPEN, false));
        settings.setUseWideViewPort(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });

        webView.setWebViewClient(new SysWebClient());
        webView.setBackgroundColor(Color.BLACK);
    }

    private class SysWebClient extends WebViewClient {

        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, android.net.http.SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            LOG.i("echo-onPageFinished url:" + url);
            if (!url.equals("about:blank")) {
                controller.evaluateScript(sourceBean, url, view);
            }
            handler.sendEmptyMessage(200);
        }

        @Nullable
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            LOG.i("shouldInterceptRequest url:" + url);

            if (url.endsWith("/favicon.ico")) {
                if (url.startsWith("http://127.0.0.1")) {
                    return new WebResourceResponse("image/x-icon", "UTF-8", null);
                }
                return null;
            }

            boolean isFilter = VideoParseRuler.isFilter(webUrl, url);
            if (isFilter) {
                LOG.i("shouldInterceptLoadRequest filter:" + url);
                return null;
            }

            boolean ad;
            if (!loadedUrls.containsKey(url)) {
                ad = AdBlocker.isAd(url);
                loadedUrls.put(url, ad);
            } else {
                ad = loadedUrls.get(url);
            }

            if (!ad && checkVideoFormat(url)) {
                HashMap<String, String> webHeaders = new HashMap<>();
                Map<String, String> hds = request.getRequestHeaders();
                if (hds != null) {
                    for (String k : hds.keySet()) {
                        if (k.equalsIgnoreCase("user-agent")
                                || k.equalsIgnoreCase("referer")
                                || k.equalsIgnoreCase("origin")) {
                            webHeaders.put(k, " " + hds.get(k));
                        }
                    }
                }
                loadFoundVideoUrls.add(url);
                loadFoundVideoUrlsHeader.put(url, webHeaders);
                LOG.i("loadFoundVideoUrl:" + url);
                if (loadFoundCount.incrementAndGet() == 1) {
                    url = loadFoundVideoUrls.poll();
                    handler.removeMessages(100);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (!TextUtils.isEmpty(cookie)) {
                        webHeaders.put("Cookie", " " + cookie);
                    }
                    callback.onVideoFound(url, webHeaders);
                    stopLoad(false);
                }
            }

            return (ad || loadFoundCount.get() > 0)
                    ? AdBlocker.createEmptyResource()
                    : null;
        }

        @Override
        public void onLoadResource(WebView webView, String url) {
            super.onLoadResource(webView, url);
        }
    }

    private boolean checkVideoFormat(String url) {
        if (url.contains("url=http") || url.contains(".html")) return false;
        try {
            if (sourceBean.getType() == 3) {
                Spider sp = ApiConfig.get().getCSP(sourceBean);
                if (sp != null && sp.manualVideoCheck())
                    return sp.isVideoFormat(url);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return VideoParseRuler.checkIsVideoForParse(webUrl, url);
    }
}
