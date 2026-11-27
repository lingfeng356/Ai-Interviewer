package com.lingfeng.interviewer.rabbitMQ.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingfeng.interviewer.common.RedisKeyConstants;
import com.lingfeng.interviewer.common.StructuredOutputInvoker;
import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.config.RabbitMQConfig;
import com.lingfeng.interviewer.dto.InterviewReportVO;
import com.lingfeng.interviewer.entity.InterviewReport;
import com.lingfeng.interviewer.mapper.InterviewReportMapper;
import com.lingfeng.interviewer.sse.SseManager;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class InterviewEvaluateConsumer extends AbstractStreamConsumer<String>{

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    @Autowired
    private SseManager sseManager;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 和简历解析用的是同一个结构化输出调用器 */
    @Autowired
    private StructuredOutputInvoker structuredOutputInvoker;

    /**
     * 手动 ack：
     * - 处理成功（或报告已存在）→ basicAck；
     * - 处理失败 → basicNack(requeue=false)，消息进死信队列，不做无意义的无限重投。
     * 如果进程在处理中挂掉、没来得及 ack，Broker 会重新投递 —— 这就是 at-least-once。
     */
    @RabbitListener(queues = RabbitMQConfig.EVALUATE_QUEUE)
    public void onMessage(String sessionId,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            handle(sessionId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // handle 里已经推送过 FAILED 事件；这里只负责把消息送去死信
            log.error("评估消息处理失败，转入死信队列, sessionId={}", sessionId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * 异步评分。
     * 相比原来“一口气跑完最后推一个 DONE”，这里把过程拆成 5 个阶段实时上报，
     * 前端进度条才能真正动起来；异常时也会推 FAILED，避免页面一直转圈。
     */
    @Override
    protected void handle(String sessionId){
        try {
            // 0.幂等：消息可能被重复投递（ack 前进程重启、人工重投），
            //   报告已存在就直接确认，不再花钱调一次模型
            InterviewReport existing = interviewReportMapper.selectOne(
                    new LambdaQueryWrapper<InterviewReport>()
                            .eq(InterviewReport::getSessionId, sessionId)
                            .last("limit 1"));
            if (existing != null) {
                progress(sessionId, "DONE", 100, "报告已存在，跳过重复评估");
                sseManager.complete(sessionId);
                return;
            }

            progress(sessionId, "QUEUED", 10, "评估任务已受理");

            // 1.读redis消息历史
            progress(sessionId, "READING_HISTORY", 30, "正在读取面试对话记录");

            String redisKey = RedisKeyConstants.interviewHistory(sessionId);
            Long sizeLong = stringRedisTemplate.opsForList().size(redisKey);
            int size = (sizeLong == null) ? 0 : sizeLong.intValue();

            List<String> rawHistory = new ArrayList<>();
            if (size > 0) {
                rawHistory = stringRedisTemplate.opsForList().range(redisKey, 0, size - 1);
            }

            StringBuilder historyText = new StringBuilder();
            for (String item : rawHistory) {
                int idx = item.indexOf(":");
                if (idx < 0) continue;
                String role = item.substring(0, idx);
                String content = item.substring(idx + 1);
                historyText.append("user".equals(role) ? "候选人：" : "面试官：")
                        .append(content).append("\n");
            }

            // 2.调用AI评分（最耗时的一步）
            progress(sessionId, "AI_EVALUATING", 60, "AI 正在综合评估");

            // Agent 模式下面试官会把观察记成笔记，这里一并交给评估模型：
            // 这样报告能说出“你提到用过 Redis，但没答出持久化方案”这种有依据的话，
            // 而不是把几千字对话重新读一遍再猜。
            String memoText = readMemoDigest(sessionId);

            String evaluatePrompt = """
                    你是一个资深的 Java 后端面试官，现在面试已经结束。
                    请根据以下对话记录，对候选人进行综合评估。
                    如果同时给了“面试官笔记”，可以参考其中的疑点与亮点；
                    但笔记只是辅助，与对话记录冲突时以对话记录为准，不要只凭笔记下结论。
                    输出 JSON，字段包括：
                    totalScore（0-100 的整数）、summary（总体评价）、
                    strengths（优点）、weaknesses（不足）、suggestion（改进建议）。
                    """;

            ChatClient chatClient = llmProviderRegistry.getDefault();

            String evaluateInput = "对话记录\n" + historyText
                    + (memoText.isBlank()
                        ? ""
                        : "\n\n面试官笔记（面试过程中记录，供参考）：\n" + memoText);

            InterviewReportVO report = structuredOutputInvoker.invoke(
                    chatClient,
                    evaluatePrompt,
                    evaluateInput,
                    InterviewReportVO.class
            );

            // 3.存mysql
            progress(sessionId, "SAVING_REPORT", 90, "正在保存评估报告");

            InterviewReport entity = new InterviewReport();
            entity.setSessionId(sessionId);
            entity.setTotalScore(report.getTotalScore());
            entity.setSummary(report.getSummary());
            entity.setStrengths(report.getStrengths() != null ? String.join(",", report.getStrengths()) : "");
            entity.setWeaknesses(report.getWeaknesses() != null ? String.join(",", report.getWeaknesses()) : "");
            entity.setSuggestion(report.getSuggestion());
            entity.setCreatedTime(LocalDateTime.now());
            try {
                interviewReportMapper.insert(entity);
            } catch (DuplicateKeyException e) {
                // 并发消费（多个消费者实例）时，另一个已经先写入了。
                // 唯一索引兜底，这里视为成功，不能让已经生成好的报告被标成失败。
                log.warn("报告已存在（并发消费），忽略本次插入, sessionId={}", sessionId);
            }

            // 4.完成
            progress(sessionId, "DONE", 100, "报告生成完成");
            sseManager.complete(sessionId);
        } catch (Exception e) {
            // 失败也要通知前端，否则报告页会一直等到轮询超时
            log.error("面试评估失败, sessionId={}", sessionId, e);
            progress(sessionId, "FAILED", -1, "报告生成失败：" + e.getMessage());
            sseManager.complete(sessionId);
            // 抛回去让 onMessage 做 basicNack(requeue=false)：
            // 消息进死信队列，而不是无限重回原队列把日志刷爆
            throw new IllegalStateException("面试评估失败: " + sessionId, e);
        }
    }

    /**
     * 读取面试官 Agent 记下的笔记，拼成一段文本；没有就返回空串。
     * Redis 里存的是 “类型|轮次|内容”。
     */
    private String readMemoDigest(String sessionId) {
        try {
            List<String> raw = stringRedisTemplate.opsForList()
                    .range(RedisKeyConstants.agentMemo(sessionId), 0, -1);
            if (raw == null || raw.isEmpty()) {
                return "";
            }
            List<String> lines = new ArrayList<>();
            for (String item : raw) {
                String[] parts = item.split("\\|", 3);
                if (parts.length < 3) {
                    continue;
                }
                String label = switch (parts[0]) {
                    case "STRENGTH" -> "优点";
                    case "WEAKNESS" -> "不足";
                    default -> "疑点";
                };
                lines.add("· [" + label + "] " + parts[2]);
            }
            return String.join("\n", lines);
        } catch (Exception e) {
            // 笔记读不到不影响评分，退化为只用对话记录
            log.warn("读取面试笔记失败, sessionId={}", sessionId, e);
            return "";
        }
    }

    /**
     * 上报一个进度事件：写 Redis（供刷新恢复）+ 推送当前 SSE 连接
     */
    private void progress(String sessionId, String stage, int progress, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stage", stage);
        payload.put("progress", progress);
        payload.put("message", message);
        try {
            sseManager.sendProgress(sessionId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // 进度推送失败不能影响评分主流程
            log.warn("进度推送失败, sessionId={}, stage={}", sessionId, stage);
        }
    }
}
