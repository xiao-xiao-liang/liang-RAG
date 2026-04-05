package com.liang.rag.infra.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁切面
 * <p>
 * 通过 AOP 拦截标注了 {@link DistributeLock} 注解的方法，自动完成加锁/解锁流程。
 * 支持静态 Key 和 SpEL 动态 Key 两种方式，以及四种锁模式组合（按 waitTime/expireTime 配置）。
 * </p>
 */
@Slf4j
@Aspect
@Component
@Order(Integer.MIN_VALUE)
@RequiredArgsConstructor
public class DistributeLockAspect {

    private final RedissonClient redissonClient;

    /**
     * SpEL 表达式解析器（线程安全，全局复用）
     */
    private static final SpelExpressionParser SPEL_PARSER = new SpelExpressionParser();

    /**
     * 方法参数名发现器（线程安全，全局复用）
     */
    private static final StandardReflectionParameterNameDiscoverer PARAM_DISCOVERER
            = new StandardReflectionParameterNameDiscoverer();

    /**
     * 分布式锁环绕通知
     * <p>
     * 使用全限定类名匹配注解，通过反射手动获取注解实例，
     * 避免 {@code @annotation(param)} 参数绑定方式在 CGLIB 代理链中的 JoinPointMatch 绑定问题。
     * </p>
     *
     * @param pjp 连接点
     * @return 业务方法返回值
     * @throws Throwable 透传业务方法抛出的原始异常
     */
    @Around("@annotation(com.liang.rag.infra.lock.DistributeLock)")
    public Object process(ProceedingJoinPoint pjp) throws Throwable {
        // 通过反射获取注解实例
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        DistributeLock distributeLock = method.getAnnotation(DistributeLock.class);

        // 解析锁 Key
        String key = resolveLockKey(pjp, distributeLock);
        String scene = distributeLock.scene();
        String lockKey = scene + "#" + key;

        int expireTime = distributeLock.expireTime();
        int waitTime = distributeLock.waitTime();
        RLock rLock = redissonClient.getLock(lockKey);

        try {
            boolean lockResult = acquireLock(rLock, lockKey, expireTime, waitTime);
            if (!lockResult) {
                log.warn("[分布式锁] 加锁失败, key={}, waitTime={}ms", lockKey, waitTime);
                throw new DistributeLockException("acquire lock failed... key: " + lockKey);
            }
            log.debug("[分布式锁] 加锁成功, key={}, expire={}ms", lockKey, expireTime);
            return pjp.proceed();
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
                log.debug("[分布式锁] 解锁, key={}", lockKey);
            }
        }
    }

    /**
     * 解析锁 Key：优先取静态 key()，其次解析 SpEL 表达式 keyExpression()
     *
     * @param pjp            连接点
     * @param distributeLock 分布式锁注解
     * @return 解析后的锁 Key
     */
    private String resolveLockKey(ProceedingJoinPoint pjp, DistributeLock distributeLock) {
        String key = distributeLock.key();
        if (!DistributeLockConstant.NONE_KEY.equals(key)) {
            return key;
        }

        // 静态 key 未设置，尝试解析 SpEL 表达式
        if (DistributeLockConstant.NONE_KEY.equals(distributeLock.keyExpression())) {
            throw new DistributeLockException("分布式锁未配置 key 或 keyExpression，请检查 @DistributeLock 注解");
        }

        Expression expression = SPEL_PARSER.parseExpression(distributeLock.keyExpression());

        // 使用 SimpleEvaluationContext 限制 SpEL 能力，仅允许读取属性（纵深防御）
        SimpleEvaluationContext.Builder contextBuilder = SimpleEvaluationContext.forReadOnlyDataBinding();
        SimpleEvaluationContext context = contextBuilder.build();

        // 获取方法参数名并绑定到 SpEL 上下文
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object[] args = pjp.getArgs();
        String[] parameterNames = PARAM_DISCOVERER.getParameterNames(method);

        if (parameterNames != null) {
            for (int i = 0; i < parameterNames.length; i++) {
                context.setVariable(parameterNames[i], args[i]);
            }
        }

        return String.valueOf(expression.getValue(context));
    }

    /**
     * 根据 waitTime 和 expireTime 的组合选择加锁策略
     *
     * @param rLock      Redisson 锁对象
     * @param lockKey    锁 Key（用于日志）
     * @param expireTime 过期时间（毫秒），-1 表示自动续期
     * @param waitTime   等待时间（毫秒），-1 表示无限等待
     * @return 是否加锁成功
     * @throws InterruptedException 等待锁期间被中断
     */
    private boolean acquireLock(RLock rLock, String lockKey, int expireTime, int waitTime)
            throws InterruptedException {
        if (waitTime == DistributeLockConstant.DEFAULT_WAIT_TIME) {
            // 无限等待模式：lock() 会阻塞直到获取锁
            if (expireTime == DistributeLockConstant.DEFAULT_EXPIRE_TIME) {
                log.debug("[分布式锁] 加锁(无限等待+自动续期), key={}", lockKey);
                rLock.lock();
            } else {
                log.debug("[分布式锁] 加锁(无限等待+固定过期), key={}, expire={}ms", lockKey, expireTime);
                rLock.lock(expireTime, TimeUnit.MILLISECONDS);
            }
            return true;
        } else {
            // 限时等待模式：tryLock() 在超时后返回 false
            if (expireTime == DistributeLockConstant.DEFAULT_EXPIRE_TIME) {
                log.debug("[分布式锁] 尝试加锁(限时等待+自动续期), key={}, wait={}ms", lockKey, waitTime);
                return rLock.tryLock(waitTime, TimeUnit.MILLISECONDS);
            } else {
                log.debug("[分布式锁] 尝试加锁(限时等待+固定过期), key={}, expire={}ms, wait={}ms",
                        lockKey, expireTime, waitTime);
                return rLock.tryLock(waitTime, expireTime, TimeUnit.MILLISECONDS);
            }
        }
    }
}
