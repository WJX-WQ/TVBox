package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Updater;
import com.github.tvbox.osc.BuildConfig;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    private Updater updater;

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        
        // 添加版本号显示
        TextView versionText = findViewById(R.id.about_version);
        if (versionText != null) {
            versionText.setText("版本: " + BuildConfig.VERSION_NAME);
            versionText.setOnClickListener(v -> {
                // 点击版本号检查更新
                if (context instanceof Activity) {
                    if (updater == null) {
                        updater = new Updater();
                    }
                    updater.check((Activity) context);
                }
            });
        }
    }
}