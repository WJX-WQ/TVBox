package com.github.tvbox.osc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.ui.dialog.UpdateDialog;
import com.github.tvbox.osc.util.Github;
import com.github.tvbox.osc.util.ToastHelper;
import com.github.catvod.net.OkHttp;
import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.base.App;

import org.json.JSONObject;

import java.io.File;

/**
 * 更新检测和下载管理器
 */
public class Updater {
    
    private UpdateDialog updateDialog;
    private Activity activity;
    private boolean checking = false;
    
    /**
     * 检查更新
     */
    public void check(Activity activity) {
        if (checking) {
            ToastHelper.show("正在检查更新...");
            return;
        }
        this.activity = activity;
        checking = true;
        ToastHelper.show("正在检查更新...");
        
        new CheckUpdateTask().execute();
    }
    
    /**
     * 异步检查更新任务
     */
    private class CheckUpdateTask extends AsyncTask<Void, Void, UpdateInfo> {
        
        @Override
        protected UpdateInfo doInBackground(Void... voids) {
            try {
                String jsonUrl = Github.getJson();
                String jsonContent = OkHttp.string(jsonUrl);
                
                if (jsonContent == null || jsonContent.isEmpty()) {
                    return null;
                }
                
                JSONObject json = new JSONObject(jsonContent);
                UpdateInfo info = new UpdateInfo();
                info.name = json.optString("name", "");
                info.desc = json.optString("desc", "");
                info.code = json.optInt("code", 0);
                info.apkUrl = Github.getApk();
                
                return info;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        
        @Override
        protected void onPostExecute(UpdateInfo updateInfo) {
            checking = false;
            
            if (updateInfo == null) {
                ToastHelper.show("检查更新失败");
                return;
            }
            
            int currentCode = BuildConfig.VERSION_CODE;
            if (updateInfo.code > currentCode) {
                // 有新版本，显示更新对话框
                showUpdateDialog(updateInfo);
            } else {
                ToastHelper.show("已是最新版本");
            }
        }
    }
    
    /**
     * 显示更新对话框
     */
    private void showUpdateDialog(UpdateInfo info) {
        if (updateDialog != null && updateDialog.isShowing()) {
            updateDialog.dismiss();
        }
        
        updateDialog = new UpdateDialog(activity);
        updateDialog.setVersion(info.name);
        updateDialog.setDescription(info.desc);
        updateDialog.setOnConfirmListener(() -> {
            downloadApk(info.apkUrl);
        });
        updateDialog.show();
    }
    
    /**
     * 下载 APK
     */
    private void downloadApk(String apkUrl) {
        if (updateDialog != null) {
            updateDialog.setDownloading(true);
        }
        
        File downloadDir = new File(activity.getExternalFilesDir(null), "download");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        File apkFile = new File(downloadDir, "TVBox_update.apk");
        
        new Thread(() -> {
            try {
                okhttp3.Response response = OkHttp.newCall(apkUrl).execute();
                long total = response.body().contentLength();
                java.io.InputStream is = response.body().byteStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(apkFile);
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;
                    if (updateDialog != null) {
                        final int pct = (int) (downloaded * 100 / total);
                        App.post(() -> updateDialog.setProgress(pct));
                    }
                }
                fos.close();
                is.close();
                App.post(() -> {
                    if (updateDialog != null) updateDialog.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                App.post(() -> {
                    if (updateDialog != null) updateDialog.setDownloading(false);
                    ToastHelper.show("下载失败: " + e.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * 安装 APK
     */
    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            Uri apkUri;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                // 使用 FileProvider
                apkUri = androidx.core.content.FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".fileprovider",
                    apkFile
                );
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            ToastHelper.show("安装失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新信息数据类
     */
    private static class UpdateInfo {
        String name;
        String desc;
        int code;
        String apkUrl;
    }
}
