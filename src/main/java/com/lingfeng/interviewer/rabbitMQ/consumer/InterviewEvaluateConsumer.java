package com.lingfeng.interviewer.rabbitMQ.consumer;

import com.lingfeng.interviewer.common.RedisKeyConstants;
import com.lingfeng.interviewer.common.StructuredOutputInvoker;
import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.config.RabbitMQConfig;
import com.lingfeng.interviewer.dto.InterviewReportVO;
import com.lingfeng.interviewer.entity.InterviewReport;
import com.lingfeng.interviewer.mapper.InterviewReportMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class InterviewEvaluateConsumer extends AbstractStreamConsumer<String>{

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    @Autowired
    private InterviewReportMapper interviewReportMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = RabbitMQConfig.EVALUATE_QUEUE)
    public void onMessage(String sessionId) {
        handle(sessionId);
    }

    @Override
    protected void handle(String sessionId){
        //1.读redis消息历史
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

        //2.调用AI评分
        String evaluatePrompt = """
                你是一个资深的 Java 后端面试官，现在面试已经结束。
                请根据以下对话记录，对候选人进行综合评估。
                输出 JSON，字段包括：
                totalScore（0-100 的整数）、summary（总体评价）、
                strengths（优点）、weaknesses（不足）、suggestion（改进建议）。
                """;

        ChatClient chatClient = llmProviderRegistry.getDefault();

        StructuredOutputInvoker invoker = new StructuredOutputInvoker();
        InterviewReportVO report = invoker.invoke(
                chatClient,
                evaluatePrompt,
                "对话记录\n" + historyText,
                InterviewReportVO.class
        );
        //3.存mysql
        InterviewReport entity = new InterviewReport();
        entity.setSessionId(sessionId);
        entity.setTotalScore(report.getTotalScore());
        entity.setSummary(report.getSummary());
        entity.setStrengths(report.getStrengths() != null ? String.join(",", report.getStrengths()) : "");
        entity.setWeaknesses(report.getWeaknesses() != null ? String.join(",", report.getWeaknesses()) : "");
        entity.setSuggestion(report.getSuggestion());
        entity.setCreatedTime(LocalDateTime.now());
        interviewReportMapper.insert(entity);
    }
}
