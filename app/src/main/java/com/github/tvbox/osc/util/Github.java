package com.github.tvbox.osc.util;

import com.github.tvbox.osc.BuildConfig;

/**
 * GitHub 更新检测工具类
 * 用于从 GitHub Actions 构建的 gh-pages 分支获取版本信息
 */
public class Github {
    // 更新检测的基础 URL
    // 格式: https://raw.githubusercontent.com/{owner}/{repo}/gh-pages/apk/zyqfork/TVBox/{branch}/
    // 注意：这个 URL 会在构建时通过 GitHub Actions 自动替换
    public static final String BASE_URL = "https://raw.githubusercontent.com/zyqfork/TVBoxOSC/gh-pages/apk/zyqfork/TVBox";
    
    /**
     * 获取当前构建的分支名称
     * 如果 BuildConfig 中没有定义，默认使用 "main"
     */
    private static String getBranch() {
        try {
            // 尝试从 BuildConfig 获取分支信息
            // 如果构建时没有设置，使用默认值 "main"
            String branch = BuildConfig.BUILD_BRANCH;
            if (branch != null && !branch.isEmpty()) {
                return branch;
            }
        } catch (Exception e) {
            // 忽略异常，使用默认值
        }
        return "main"; // 默认分支
    }
    
    /**
     * 获取当前构建的 flavor 信息
     * 格式: {brand}-{abi}-{mode}
     */
    private static String getFlavor() {
        String brand = getFlavorBrand();
        String abi = getFlavorAbi();
        String mode = getFlavorMode();
        return brand + "-" + abi + "-" + mode;
    }
    
    /**
     * 获取 brand flavor (generic 或 hisense)
     */
    private static String getFlavorBrand() {
        try {
            String brand = BuildConfig.FLAVOR_brand;
            if (brand != null && !brand.isEmpty()) {
                return brand;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return "generic"; // 默认
    }
    
    /**
     * 获取 abi flavor (armeabi 或 arm64)
     */
    private static String getFlavorAbi() {
        try {
            String abi = BuildConfig.FLAVOR_abi;
            if (abi != null && !abi.isEmpty()) {
                return abi;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return "arm64"; // 默认
    }
    
    /**
     * 获取 mode flavor (normal/java 或 python)
     * 注意：BuildConfig 中可能是 "normal"，但 JSON 文件名使用 "java"
     */
    private static String getFlavorMode() {
        try {
            String mode = BuildConfig.FLAVOR_mode;
            if (mode != null && !mode.isEmpty()) {
                // normal 对应 java
                if ("normal".equals(mode)) {
                    return "java";
                }
                return mode;
            }
        } catch (Exception e) {
            // 忽略异常
        }
        return "java"; // 默认
    }
    
    /**
     * 获取版本检测 JSON 文件的 URL
     * 格式: {BASE_URL}/{branch}/{flavor}-{branch}.json
     * 例如: .../main/generic-arm64-java-main.json
     */
    public static String getJson() {
        String branch = getBranch();
        String flavor = getFlavor();
        return BASE_URL + "/" + branch + "/" + flavor + "-" + branch + ".json";
    }
    
    /**
     * 获取 APK 下载 URL
     * 格式: {BASE_URL}/{branch}/TVBox_release-{flavor}.apk
     * 例如: .../main/TVBox_release-generic-arm64-java.apk
     */
    public static String getApk() {
        String branch = getBranch();
        String flavor = getFlavor();
        return BASE_URL + "/" + branch + "/TVBox_release-" + flavor + ".apk";
    }
}
