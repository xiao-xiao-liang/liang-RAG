package com.liang.rag.infra.lock;

/**
 * 分布式锁异常
 * <p>
 * 在加锁失败、锁 Key 未配置等场景下抛出。
 * </p>
 */
public class DistributeLockException extends RuntimeException {

    public DistributeLockException(String message) {
        super(message);
    }

    public DistributeLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
