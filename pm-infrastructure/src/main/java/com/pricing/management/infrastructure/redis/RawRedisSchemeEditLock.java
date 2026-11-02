/*
 * Copyright (c) 2026 Company. All rights reserved.
 *
 * @Author: houzhangbo
 * @Date: 2026-11-01
 */
package com.pricing.management.infrastructure.redis;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于"原始 Redis"命令（SETNX/GET/EXPIRE/DEL，不使用 Lua 脚本）实现的
 * 方案编辑器分布式锁（不依赖 Redisson），支持同线程可重入。
 *
 * <p>加锁流程：
 * <ol>
 *   <li>锁值 token = 当前线程 id。重入判定依据：Redis 里已存的值等于本线程 id，
 *       即视为同一线程再次进入。注意线程 id 仅在单 JVM 内唯一，多实例部署理论上可撞值，
 *       如需严格区分可升级为"实例标识 + 线程 id"拼接；</li>
 *   <li>SET key token NX PX ttl —— 一条命令原子完成"不存在才写入 + 设置兜底过期时间"。
 *       写入成功即首次抢锁；锁含 TTL，即使持有者宕机来不及解锁也会自动释放，不会死锁；</li>
 *   <li>SETNX 未命中时走重入判断：GET 现存值与本线程 id 比对，相等则 EXPIRE 刷新 TTL 后放行，
 *       不相等说明锁在其他线程/实例手里，抢锁失败（不等待、不重试，由调用方快速失败）。
 *       注意：SETNX → GET → EXPIRE 是三条独立命令、非原子，极端时序下（比对瞬间锁恰好易主）
 *       可能误判，这是放弃 Lua 换取"只用原始命令"的已知取舍；</li>
 *   <li>抢锁/重入成功后，本地 ThreadLocal 按 schemeId 记录重入次数 +1，供释放时逐层回退。</li>
 * </ol>
 *
 * <p>解锁流程：内层重入的 release 只回退本地计数、不碰 Redis，
 * 防止内层把外层还需要的锁提前删掉；计数归零（最外层）才先 GET 比对 token、
 * 相等才 DEL，只删自己加的那把锁，避免"我的锁超时被 B 抢到、我再把 B 的锁删掉"的误删问题。
 * 同样因不使用 Lua，GET 与 DEL 之间存在非原子窗口，已由 TTL 兜底把风险压到可接受。
 *
 * <p>容错口径：Redis 连接异常时本组件不做降级，直接向上抛出异常，
 * 调用方按 fail-close（拒绝访问）处理，绝不无锁放行。
 *
 * @author houzhangbo
 */
@Component
public class RawRedisSchemeEditLock {

    /** 锁 key 前缀。与 Redisson 写锁（pm:lock:edit:，Redis 里是 hash 结构）分属不同命名空间，
     *  避免同一 key 混用 string 与 hash 两种数据结构触发 WRONGTYPE 错误。 */
    private static final String LOCK_KEY_PREFIX = "pm:lock:editor:";

    /** 锁最长持有时间。编辑器视图构建是低频、毫秒级的短操作，30s 足够覆盖正常执行，
     *  又能在持有者宕机后自动兜底释放。 */
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    /** 本线程持有锁的重入计数：schemeId -> 已进入层数。
     *  Redis 侧只记录"锁属于哪个线程"，层数必须记在本地——同线程嵌套调用时
     *  中间层 release 不能真删锁，否则最外层还没退出锁就没了。 */
    private static final ThreadLocal<Map<Long, Integer>> REENTRY_COUNT = ThreadLocal.withInitial(HashMap::new);

    private final StringRedisTemplate redis;

    public RawRedisSchemeEditLock(StringRedisTemplate redis) { this.redis = redis; }

    /** 锁 key：前缀 + 方案 ID，粒度为单个方案。 */
    private String lockKey(long schemeId) { return LOCK_KEY_PREFIX + schemeId; }

    /**
     * 尝试加锁（SETNX 实现，不等待、不重试；同一线程重复进入视为重入并直接放行）。
     *
     * @param schemeId 方案 ID，锁粒度
     * @return 加锁或重入成功返回锁标识 token（即当前线程 id，释放锁时必须原样传回）；锁被他人占用返回 null
     */
    public String tryAcquire(long schemeId) {
        // 步骤1：锁值 token = 当前线程 id，Redis 里现存值与它相等即同一线程，可重入
        String token = String.valueOf(Thread.currentThread().getId());
        String key = lockKey(schemeId);
        // 步骤2：SET key token NX PX ttl —— 原子完成"不存在才写入 + 设置过期时间"
        Boolean firstAcquired = redis.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (Boolean.TRUE.equals(firstAcquired)) {
            // 步骤2a：NX 命中，首次抢锁成功，本线程对该方案的重入计数记为 1 层
            REENTRY_COUNT.get().put(schemeId, 1);
            return token;
        }
        // 步骤3：NX 未命中，锁已被持有——GET 现存值与本线程 id 比对，做重入判断
        String heldBy = redis.opsForValue().get(key);
        if (token.equals(heldBy)) {
            // 步骤3a：重入命中（同一线程嵌套进入）。EXPIRE 刷新 TTL 续命，本地计数 +1
            redis.expire(key, LOCK_TTL);
            REENTRY_COUNT.get().merge(schemeId, 1, Integer::sum);
            return token;
        }
        // 步骤3b：heldBy 是本线程 id 之外的值（其他线程/实例持有），或为 null（比对瞬间锁刚好过期）
        //        ——均视为抢锁失败，由调用方快速失败
        return null;
    }

    /**
     * 释放锁一层：内层重入只回退本地计数、不删 Redis 锁；
     * 计数归零（最外层）才 GET 比对 token、相等才 DEL，只删自己加的那把锁。
     * token 不匹配（例如锁已超时易主）时跳过删除，不影响业务结果。
     *
     * @param schemeId 方案 ID
     * @param token    tryAcquire 返回的锁标识（当前线程 id），必须非空
     */
    public void release(long schemeId, String token) {
        // 步骤1：取本线程对该方案的重入计数，减一层
        Map<Long, Integer> counts = REENTRY_COUNT.get();
        int remaining = counts.getOrDefault(schemeId, 1) - 1;
        if (remaining > 0) {
            // 步骤2a：还在内层重入——只回退计数，Redis 里的锁必须保留给最外层持有者
            counts.put(schemeId, remaining);
            return;
        }
        // 步骤2b：计数归零，本次是最外层释放
        counts.remove(schemeId);
        if (counts.isEmpty()) REENTRY_COUNT.remove(); // 线程池复用场景下清理 ThreadLocal，防止泄漏
        // 步骤3：GET 比对 token，相等才 DEL——只删自己持有的锁。
        //        不用 Lua 时"比对 + 删除"非原子：若比对后、删除前锁超时易主，
        //        可能删掉他人刚拿到的锁；TTL 30s 远大于本方法执行耗时，此窗口概率极低，属已知取舍。
        String key = lockKey(schemeId);
        if (token.equals(redis.opsForValue().get(key))) redis.delete(key);
    }
}
