package com.lingfeng.interviewer.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 Redis 里的对话历史（“user:...” / “assistant:...”）还原成问答对，并按关键词匹配。
 *
 * 为什么抽成纯函数：
 * 1. 不依赖 Redis 和 Spring，可以直接单测；
 * 2. 历史格式的解析规则集中在一处，工具方法只负责取数据和拼文本。
 *
 * 历史的结构是：assistant(开场白) → user → assistant → user → ...
 * 所以一条 assistant 加上它后面那条 user，就是一轮完整问答。
 */
public final class ChatHistoryMatcher {

    private ChatHistoryMatcher() {
    }

    /**
     * @param questionNo 第几问（1 开始，开场白算第 1 问）
     * @param question   面试官问的话
     * @param answer     候选人的回答
     */
    public record QaPair(int questionNo, String question, String answer) {
    }

    /**
     * 取开场白：历史里第一条 assistant 消息。
     * 幂等命中时要用它把"开场白"重放给前端，而不是返回一句提示语。
     */
    public static String opening(List<String> raw) {
        if (raw == null) {
            return null;
        }
        for (String item : raw) {
            if (item != null && item.startsWith("assistant:")) {
                return item.substring("assistant:".length()).trim();
            }
        }
        return null;
    }

    /** 候选人回答了几轮：只数 user 条数，开场白（assistant）不算 */
    public static int answeredCount(List<String> raw) {
        if (raw == null) {
            return 0;
        }
        int count = 0;
        for (String item : raw) {
            if (item != null && item.startsWith("user:")) {
                count++;
            }
        }
        return count;
    }

    /** 把原始历史还原成问答对（末尾还没被回答的那一问不会出现在结果里） */
    public static List<QaPair> pairs(List<String> raw) {
        List<QaPair> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }

        String pendingQuestion = null;
        int questionNo = 0;
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            int idx = item.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String role = item.substring(0, idx);
            String content = item.substring(idx + 1).trim();

            if ("assistant".equals(role)) {
                pendingQuestion = content;
                questionNo++;
            } else if ("user".equals(role) && pendingQuestion != null) {
                result.add(new QaPair(questionNo, pendingQuestion, content));
                pendingQuestion = null;
            }
        }
        return result;
    }

    /**
     * 关键词匹配（问题或回答命中都算），只保留最近 limit 条。
     * 取最近的而不是最早的：重复提问通常发生在“记不清刚才问过什么”的时候。
     */
    public static List<QaPair> match(List<String> raw, String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        if (kw.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<QaPair> hits = new ArrayList<>();
        for (QaPair pair : pairs(raw)) {
            if (pair.question().toLowerCase().contains(kw)
                    || pair.answer().toLowerCase().contains(kw)) {
                hits.add(pair);
            }
        }

        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(hits.size() - limit, hits.size()));
        }
        return hits;
    }
}
