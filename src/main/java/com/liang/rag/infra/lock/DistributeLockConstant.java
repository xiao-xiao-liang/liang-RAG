package com.liang.rag.infra.lock;

/**
 * 分布式锁常量
 */
public final class DistributeLockConstant {

    private DistributeLockConstant() {
        // 常量类禁止实例化
    }

    /**
     * 未设置 key 的哨兵值
     */
    public static final String NONE_KEY = "NONE";

    /**
     * 默认过期时间（-1 表示不设置过期时间，由 Redisson Watchdog 自动续期）
     */
    public static final int DEFAULT_EXPIRE_TIME = -1;

    /**
     * 默认等待时间（-1 表示不设置等待时间，使用 lock() 无限等待直到获取锁）
     */
    public static final int DEFAULT_WAIT_TIME = -1;
}
