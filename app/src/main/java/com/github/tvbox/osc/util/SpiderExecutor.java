package com.github.tvbox.osc.util;

import com.github.catvod.crawler.JsLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 蜘蛛线程池管理器
 * 管理搜索/分类请求的线程池，支持动态创建和销毁
 */
public class SpiderExecutor {

    private ExecutorService searchExecutorService;

    /** 全局单线程池，用于首页内容加载等串行操作 */
    public static final ExecutorService spThreadPool = Executors.newSingleThreadExecutor();

    /**
     * 初始化搜索线程池（5 线程）
     */
    public void init() {
        destroy();
        searchExecutorService = Executors.newFixedThreadPool(5);
    }

    /**
     * 提交任务到搜索线程池
     */
    public void execute(Runnable runnable) {
        if (searchExecutorService != null) {
            searchExecutorService.execute(runnable);
        }
    }

    /**
     * 立即关闭搜索线程池
     */
    public List<Runnable> shutdownNow() {
        if (searchExecutorService == null) return new ArrayList<>();
        List<Runnable> tasks = searchExecutorService.shutdownNow();
        searchExecutorService = null;
        JsLoader.stopAll();
        return tasks;
    }

    /**
     * 销毁搜索线程池
     */
    public void destroy() {
        if (searchExecutorService != null) {
            searchExecutorService.shutdownNow();
            searchExecutorService = null;
            JsLoader.stopAll();
        }
    }

    /**
     * 安全关闭指定线程池
     */
    public static void close(ExecutorService executorService) {
        if (executorService != null) {
            try {
                executorService.shutdownNow();
            } catch (Throwable ignored) {
            }
        }
    }
}
