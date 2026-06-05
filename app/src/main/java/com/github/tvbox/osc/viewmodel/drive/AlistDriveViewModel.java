package com.github.tvbox.osc.viewmodel.drive;

import com.github.tvbox.osc.bean.DriveFolderFile;
import com.github.tvbox.osc.util.UA;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.github.catvod.net.OkHttp;
import com.github.tvbox.osc.base.App;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

public class AlistDriveViewModel extends AbstractDriveViewModel {



    public final String getUrl(String str) {
        String str2;
        if (str != null) {
            try {
                URL url = new URL(str);
                if (url.getPort() > 0) {
                    str2 = ":" + url.getPort();
                } else {
                    str2 = "";
                }
                return url.getProtocol() + "://" + url.getHost() + str2;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    @Override
    public String loadData(LoadDataCallback callback) {
        JsonObject config = currentDrive.getConfig();
        if (currentDriveNote == null) {
            currentDriveNote = new DriveFolderFile(null,
                    config.has("initPath") ? config.get("initPath").getAsString() : "", 0, false, null, null);
        }
        String targetPath = currentDriveNote.getAccessingPathStr() + currentDriveNote.name;

        if (currentDriveNote.getChildren() == null) {
            new Thread() {
                public void run() {
                    String webLink = getUrl(config.get("url").getAsString());
                    try {
                        if (currentDrive.version == 0) {
                            String result = OkHttp.string(webLink + "/api/public/settings");
                            JSONObject opt = new JSONObject(result);
                            Object obj = new JSONTokener(opt.optString("data")).nextValue();
                            if (obj instanceof JSONObject) {
                                currentDrive.version = 3;
                            } else if (obj instanceof JSONArray) {
                                currentDrive.version = 2;
                            }
                        }

                        if (currentDrive.version == 2) {
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("path", targetPath.isEmpty() ? "/" : targetPath);
                            requestBody.put("password", currentDrive.getConfig().get("password").getAsString());
                            requestBody.put("page_num", 1);
                            requestBody.put("page_size", 200);
                            String jsonBody = requestBody.toString();
                            String reqUrl = webLink + "/api/public/path";
                            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder().url(reqUrl).post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json;charset=UTF-8"), jsonBody));
                            reqBuilder.header("User-Agent", UA.random());
                            if (webLink != null && !webLink.isEmpty()) {
                                String origin = webLink;
                                if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
                                reqBuilder.header("origin", origin);
                                reqBuilder.header("Referer", origin);
                            }
                            reqBuilder.header("accept", "application/json, text/plain, */*");
                            reqBuilder.header("content-type", "application/json;charset=UTF-8");
                            new Thread(() -> {
                                try {
                                    okhttp3.Response resp = com.github.catvod.net.OkHttp.client().newCall(reqBuilder.build()).execute();
                                    String respBody = resp.body().string();
                                    App.post(() -> {
                                    String respBody = response.body();
                                    try {
                                        JsonObject respData = JsonParser.parseString(respBody).getAsJsonObject();
                                        List<DriveFolderFile> items = new ArrayList<>();
                                        if (respData.get("code").getAsInt() == 200) {
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                            for (JsonElement file : respData.get("data").getAsJsonObject().get("files").getAsJsonArray()) {
                                                JsonObject fileObj = file.getAsJsonObject();
                                                String fileName = fileObj.get("name").getAsString();
                                                int extNameStartIndex = fileName.lastIndexOf(".");
                                                boolean isFile = fileObj.get("type").getAsInt() != 1;
                                                String fileUrl = null;
                                                if (fileObj.has("url") && !fileObj.get("url").getAsString().isEmpty())
                                                    fileUrl = fileObj.get("url").getAsString();
                                                try {
                                                    DriveFolderFile driveFile = new DriveFolderFile(currentDriveNote, fileName, currentDrive.version, isFile,
                                                            isFile && extNameStartIndex >= 0 && extNameStartIndex < fileName.length() ?
                                                                    fileName.substring(extNameStartIndex + 1) : null,
                                                            dateFormat.parse(fileObj.get("updated_at").getAsString()).getTime());
                                                    if (fileUrl != null)
                                                        driveFile.fileUrl = fileUrl;
                                                    items.add(driveFile);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                        sortData(items);
                                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                                        backItem.parentFolder = backItem;
                                        items.add(0, backItem);
                                        currentDriveNote.setChildren(items);
                                        if (callback != null)
                                            callback.callback(currentDriveNote.getChildren(), false);
                                    } catch (Exception ex) {
                                        if (callback != null)
                                            callback.fail("无法访问，请注意地址格式");
                                    }
                                }
                            });
                        } else if (currentDrive.version == 3) {
                            JSONObject requestBody = new JSONObject();
                            requestBody.put("path", targetPath.isEmpty() ? "/" : targetPath);
                            requestBody.put("password", currentDrive.getConfig().get("password").getAsString());
                            requestBody.put("page", 1);
                            requestBody.put("per_page", 200);
                            requestBody.put("refresh", false);
                            String jsonBody = requestBody.toString();
                            String reqUrl = webLink + "/api/fs/list";
                            okhttp3.Request.Builder reqBuilder = new okhttp3.Request.Builder().url(reqUrl).post(okhttp3.RequestBody.create(okhttp3.MediaType.parse("application/json;charset=UTF-8"), jsonBody));
                            reqBuilder.header("User-Agent", UA.random());
                            if (webLink != null && !webLink.isEmpty()) {
                                String origin = webLink;
                                if (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
                                reqBuilder.header("origin", origin);
                                reqBuilder.header("Referer", origin);
                            }
                            reqBuilder.header("accept", "application/json, text/plain, */*");
                            reqBuilder.header("content-type", "application/json;charset=UTF-8");
                            new Thread(() -> {
                                try {
                                    okhttp3.Response resp = com.github.catvod.net.OkHttp.client().newCall(reqBuilder.build()).execute();
                                    String respBody = resp.body().string();
                                    com.github.tvbox.osc.base.App.post(() -> {
                                    String respBody = response.body();
                                    try {
                                        JsonObject respData = JsonParser.parseString(respBody).getAsJsonObject();
                                        List<DriveFolderFile> items = new ArrayList<>();
                                        if (respData.get("code").getAsInt() == 200) {
                                            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                                            for (JsonElement file : respData.get("data").getAsJsonObject().get("content").getAsJsonArray()) {
                                                JsonObject fileObj = file.getAsJsonObject();
                                                String fileName = fileObj.get("name").getAsString();
                                                int extNameStartIndex = fileName.lastIndexOf(".");
                                                boolean isFile = !fileObj.get("is_dir").getAsBoolean();

                                                try {
                                                    DriveFolderFile driveFile = new DriveFolderFile(currentDriveNote, fileName, currentDrive.version, isFile,
                                                            isFile && extNameStartIndex >= 0 && extNameStartIndex < fileName.length() ?
                                                                    fileName.substring(extNameStartIndex + 1) : null,
                                                            dateFormat.parse(fileObj.get("modified").getAsString()).getTime());
                                                    items.add(driveFile);
                                                } catch (ParseException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                        }
                                        sortData(items);
                                        DriveFolderFile backItem = new DriveFolderFile(null, null, 0, false, null, null);
                                        backItem.parentFolder = backItem;
                                        items.add(0, backItem);
                                        currentDriveNote.setChildren(items);
                                        if (callback != null)
                                            callback.callback(currentDriveNote.getChildren(), false);
                                    } catch (Exception ex) {
                                        if (callback != null)
                                            callback.fail("无法访问，请注意地址格式");
                                    }
                                });
                            } catch (Exception ignored) {}
                        }).start();
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.start();
            return targetPath;
        } else {
            sortData(currentDriveNote.getChildren());
            if (callback != null)
                callback.callback(currentDriveNote.getChildren(), true);
        }
        return targetPath;
    }

    public void loadFile(DriveFolderFile targetFile, LoadFileCallback callback) {
        JsonObject config = currentDrive.getConfig();
        String webLink = getUrl(config.get("url").getAsString());
        String targetPath = targetFile.getAccessingPathStr() + targetFile.name;
        try {
            if (callback != null) {
                if (targetFile.fileUrl != null && !targetFile.fileUrl.isEmpty()) {
                    callback.callback(targetFile.fileUrl);
                } else {
                    callback.callback(URLDecoder.decode(webLink + "/d" + targetPath, "UTF-8"));
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            callback.fail(e.getMessage());
        }

    }

    public interface LoadFileCallback {
        void callback(String fileUrl);

        void fail(String msg);
    }
}