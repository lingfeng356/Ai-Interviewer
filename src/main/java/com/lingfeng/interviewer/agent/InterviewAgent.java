package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.common.RedisKeyConstants;
import com.lingfeng.interviewer.dto.AgentReplyVO;
import com.lingfeng.interviewer.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 面试官 Agent 的一次执行。
 *
 * 它只负责“生成这一轮的文本 + 记录工具轨迹”，不负责写 redis、不负责 TTS、不负责组装 VO ——
 * 那些仍然在 InterviewServiceImpl#doReply 里完成，
 * 这样 Agent 模式和经典模式可以共用同一段“落库/转语音/返回”的代码，不会分叉。
 *
 * 关于工具执行循环：
 * 工具调用由 Spring AI 内置的 ToolCallingManager 自动执行（DeepSeekChatOptions 实现了
 * ToolCallingChatOptions，默认开启内部执行）。我们没有手写 5 步循环，原因是：
 *   - 手写循环需要从注册中心拿到 ChatModel 并自己构造 ToolResponseMessage，
 *     而当前的 LlmProvider 只暴露 ChatClient，改接口的收益不抵风险；
 *   - 可观测性并没有损失：每一次工具调用都在工具方法里记了 trace。
 * 步数/时间预算放在工具层（AgentTraceRecorder），超限后工具返回“别再调了”的提示，
 * 效果等价于限步，而且不依赖框架内部实现。
 */
@Slf4j
@Component
public class InterviewAgent {

    /** 本轮最多执行几次工具（超过就拦截，逼模型回到“提问”这条路上） */
    private static final int MAX_TOOL_CALLS = 5;
    /** 本轮工具执行的总时长预算（毫秒） */
    private static final long TOOL_BUDGET_MS = 30_000L;
    /** 注入 prompt 的笔记条数上限 */
    private static final int MEMO_IN_PROMPT = 8;

    @Autowired
    private RagService ragService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 一次生成的结果：文本 + 给前端的执行摘要 */
    public record Outcome(String text, AgentReplyVO meta) {
    }

    /**
     * 生成一轮回复。
     *
     * @param ctx        已经准备好的上下文（messages / 阶段 / 简历）
     * @param chatClient 本回合使用的模型
     */
    public Outcome generate(ReplyContext ctx, ChatClient chatClient) {
        long startedAt = System.currentTimeMillis();

        AgentTraceRecorder trace = new AgentTraceRecorder(MAX_TOOL_CALLS, TOOL_BUDGET_MS);
        AgentContext agentContext = new AgentContext(ctx);
        InterviewTools tools = new InterviewTools(agentContext, ragService, stringRedisTemplate, trace);

        String memoDigest = readMemoDigest(ctx.sessionId());
        String text;
        String mode = RedisKeyConstants.AGENT_MODE_AGENT;

        try {
            text = chatClient.prompt()
                    .system(AgentPromptBuilder.agent(
                            ctx.stage(), ctx.answeredRounds(), memoDigest, trace.remaining()))
                    .messages(ctx.messages())
                    .tools(tools)
                    .call()
                    .content();

            if (text == null || text.isBlank()) {
                throw new IllegalStateException("模型返回空内容");
            }
        } catch (Exception e) {
            // 降级：去掉工具，用经典 prompt 再要一次。
            // 这一步很重要 —— 演示现场网络/模型抖动时，功能不能整个挂掉。
            log.warn("Agent 执行失败，本轮降级为经典模式, sessionId={}, 原因={}",
                    ctx.sessionId(), e.toString());
            mode = RedisKeyConstants.AGENT_MODE_FALLBACK;
            text = chatClient.prompt()
                    .system(AgentPromptBuilder.classic(ctx.stage(), ctx.answeredRounds()))
                    .messages(ctx.messages())
                    .call()
                    .content();
        }

        AgentReplyVO meta = new AgentReplyVO();
        meta.setMode(mode);
        meta.setSteps(trace.count());
        meta.setElapsedMs(System.currentTimeMillis() - startedAt);
        meta.setToolCalls(trace.steps());

        return new Outcome(text, meta);
    }

    /**
     * 读取最近几条面试笔记，拼成给 prompt 用的一段文本。
     * Redis 里存的是 “类型|轮次|内容”，这里转成 “· [疑点] 内容”。
     */
    private String readMemoDigest(String sessionId) {
        try {
            String key = RedisKeyConstants.agentMemo(sessionId);
            Long size = stringRedisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                return "";
            }
            int from = (int) Math.max(0, size - MEMO_IN_PROMPT);
            List<String> raw = stringRedisTemplate.opsForList().range(key, from, size - 1);
            if (raw == null || raw.isEmpty()) {
                return "";
            }

            List<String> lines = new ArrayList<>();
            for (String item : raw) {
                String[] parts = item.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                lines.add("· [" + typeLabel(parts[0]) + "] " + parts[2]);
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            // 笔记读不到不影响面试，降级为“没有笔记”
            log.warn("读取面试笔记失败, sessionId={}", sessionId, e);
            return "";
        }
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "STRENGTH" -> "优点";
            case "WEAKNESS" -> "不足";
            default -> "疑点";
        };
    }
}
