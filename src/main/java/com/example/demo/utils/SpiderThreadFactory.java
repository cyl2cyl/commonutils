package com.example.demo.utils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class SpiderThreadFactory {

    /**
     * 普通线程池
     * 1、支持普通线程的并发执行
     * 2、支持Future线程并发执行
     */
    private static ThreadPoolExecutor simplePoolExecutor;

    /**
     * 获取一个普通的线程池， 最大50并发，初始化10， 每个线程保持100秒生存时间
     */
    public static ThreadPoolExecutor getSimpleExecutorInstance() {
        if (simplePoolExecutor == null) {
            synchronized (ThreadPoolExecutor.class) {
                if (simplePoolExecutor == null) {
                    simplePoolExecutor = new ThreadPoolExecutor(10, 50, 100, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
                }
            }
        }
        return simplePoolExecutor;
    }

    public static boolean shutdownAndWait(int secnods) {

        boolean result = false;

        if (simplePoolExecutor != null) {
            try {
                simplePoolExecutor.shutdown();
                result = simplePoolExecutor.awaitTermination(secnods, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
        return false;
    }
}
