package com.lingfeng.interviewer.sse;

import com.lingfeng.interviewer.common.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * SSE 连接管理器
 *
 * 设计点：
 * 1. 命名事件（前端用 addEventListener 精确接收，不靠字符串包含判断）
 * 2. 超时 5 分钟（报告生成几十秒，60 秒会提前断）
 * 3. 进度同时写 Redis，刷新页面 / 多实例也能拿到当前进度
 * 4. 推送失败只清理连接、不抛异常 —— 推送不该影响评分主流程
 * 5. 一个 session 允许多个连接（同一场面试开了两个标签页，两个都要能收到进度）
 */
@Component
public class SseManager {

    /** 连接最长存活 5 分钟 */
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /** 进度缓存 10 分钟，用于刷新页面后恢复 */
    private static final Duration PROGRESS_TTL = Duration.ofMinutes(10);

    /**
     * key(这里就是 sessionId) -> 多个连接。
     * 用 CopyOnWriteArrayList：读多写少（推送频繁、建连/断开很少），加锁代价最低。
     */
    private final Map<String, List<SseEmitter>> emitterMap = new ConcurrentHashMap<>();

    private final StringRedisTemplate stringRedisTemplate;

    public SseManager(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 建立连接。
     * sessionId 不为空时，会把 Redis 里缓存的最近一次进度立刻补发一遍（刷新页面恢复进度）
     */
    public SseEmitter create(String key, String sessionId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitterMap.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        // 三个回调都要清理，否则断开的 emitter 会一直留在 list 里
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));

        if (sessionId != null) {
            String last = getProgress(sessionId);
            if (last != null) {
                send(key, "PROGRESS", last);
            }
        }
        return emitter;
    }

    /**
     * 推送给这个 key 下的所有连接。
     * 连接不存在（浏览器没连 / 已断开）时静默返回，不影响调用方业务。
     */
    public void send(String key, String event, String data) {
        List<SseEmitter> list = emitterMap.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(
                        SseEmitter.event()
                                .name(event)
                                .data(data, MediaType.TEXT_PLAIN)
                );
            } catch (Exception e) {
                // 这个连接已断开：只移除它，其它连接继续
                remove(key, emitter);
            }
        }
    }

    /**
     * 推送“进度”事件，并写入 Redis。
     * 写 Redis 是为了刷新页面后还能显示进度（内存里的 Map 刷新就没了）。
     */
    public void sendProgress(String sessionId, String json) {
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstants.interviewProgress(sessionId), json, PROGRESS_TTL);
        send(sessionId, "PROGRESS", json);
    }

    /** 读取缓存的进度（没有则返回 null） */
    public String getProgress(String sessionId) {
        return stringRedisTemplate.opsForValue()
                .get(RedisKeyConstants.interviewProgress(sessionId));
    }

    /** 结束这个 key 下的所有连接 */
    public void complete(String key) {
        List<SseEmitter> list = emitterMap.remove(key);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 已经断开的连接 complete 会抛异常，忽略即可
            }
        }
    }

    /** 移除单个连接；列表空了就把 key 一起删掉，避免 Map 无限增长 */
    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> list = emitterMap.get(key);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitterMap.remove(key, list);
        }
    }
}
