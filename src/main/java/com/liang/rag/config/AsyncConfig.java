package com.liang.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 * <p>用于 PDF 文档异步转换、Embedding 向量化等耗时操作</p>
 *
 * @author liang
 */
@Configuration
@EnableScheduling
public class AsyncConfig {

    /**
     * 文档处理线程池
     * <p>用于 PDF 文档异步转换等耗时操作</p>
     */
    @Bean("documentProcessExecutor")
    public Executor documentProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-process-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Embedding 向量化线程池
     * <p>
     * 专用于调用 DashScope Embedding API + 写入 Milvus 的 I/O 密集型任务。
     * 与 ForkJoinPool.commonPool() 隔离，避免影响其他组件。
     * </p>
     *
     * <table>
     *     <tr><th>参数</th><th>值</th><th>理由</th></tr>
     *     <tr><td>corePoolSize</td><td>4</td><td>I/O 密集型，4 线程足以填满 API 管线</td></tr>
     *     <tr><td>maxPoolSize</td><td>8</td><td>峰值可扩展，但不超过 DashScope 并发限流</td></tr>
     *     <tr><td>queueCapacity</td><td>32</td><td>不宜过大，避免任务堆积导致内存压力</td></tr>
     * </table>
     */
    @Bean("embeddingExecutor")
    public Executor embeddingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("embedding-");
        // 拒绝策略：由调用方线程执行，不丢任务，同时天然限流
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
