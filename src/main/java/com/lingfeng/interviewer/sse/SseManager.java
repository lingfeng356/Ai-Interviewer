package com.lingfeng.interviewer.sse;

import com.lingfeng.interviewer.common.RedisKeyConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * SSE 连接管理器
 *
 * 改造点（对比原来的“只推一条字符串”）：
 * 1. 支持命名事件（前端用 addEventListener 精确接收，不再靠字符串包含判断）
 * 2. 超时从 60s 提到 5 分钟（报告生成可能要几十秒，原来的连接会提前断掉）
 * 3. 进度同时写 Redis，刷新页面 / 多实例部署也能拿到当前进度
 * 4. 推送失败只清理连接、不抛异常 —— 推送不该影响评分主流程
 */
@Component
public class SseManager {

    /** 连接最长存活 5 分钟 */
    private static final long TIMEOUT_MS = 5 * 60 * 1000L;

    /** 进度缓存 10 分钟，用于刷新页面后恢复 */
    private static final Duration PROGRESS_TTL = Duration.ofMinutes(10);

    /**
     * key -> 连接。
     * 报告进度用 sessionId 作 key；流式回答用 sessionId:chat 作 key，两者互不干扰。
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

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
        emitter.onCompletion(() -> emitterMap.remove(key));
        emitter.onTimeout(() -> emitterMap.remove(key));
        emitter.onError(e -> emitterMap.remove(key));
        emitterMap.put(key, emitter);

        if (sessionId != null) {
            String last = getProgress(sessionId);
            if (last != null) {
                send(key, "PROGRESS", last);
            }
        }
        return emitter;
    }

    /** 只建立连接，不补发进度（流式回答用） */
    public SseEmitter create(String key) {
        return create(key, null);
    }

    /**
     * 推送一个命名事件。
     * 连接不存在（浏览器没连 / 已断开）时静默返回，不影响调用方业务。
     */
    public void send(String key, String event, String data) {
        SseEmitter emitter = emitterMap.get(key);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(
                    SseEmitter.event()                    // 1. 创建一个事件
                            .name(event)                      // 2. 设置事件名
                            .data(data, MediaType.TEXT_PLAIN) // 3. 设置事件数据
            );
        } catch (Exception e) {
            // 连接已断开：清理掉即可，不要把异常抛回业务线程
            emitterMap.remove(key);
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

    /** 结束连接 */
    public void complete(String key) {
        SseEmitter emitter = emitterMap.remove(key);
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 已经断开的连接 complete 会抛异常，忽略即可
            }
        }
    }
}
