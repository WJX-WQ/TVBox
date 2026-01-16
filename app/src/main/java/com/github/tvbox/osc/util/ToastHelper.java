package com.github.tvbox.osc.util;

import android.content.Context;
import android.widget.Toast;
import android.os.Looper;

import com.github.tvbox.osc.base.App;

public class ToastHelper {

    public static void showToast(Context context, String text) {
        new Thread(new Runnable() {
            public void run() {
                Looper.prepare();
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }).start();
    }

    /**
     * 使用 Application context 显示 Toast
     */
    public static void show(String text) {
        App app = App.getInstance();
        if (app != null) {
            showToast(app, text);
        }
    }

    public static void debugToast(Context context, String text) {
        if (HawkConfig.isDebug()) {
            showToast(context, text);
        }
    }
}