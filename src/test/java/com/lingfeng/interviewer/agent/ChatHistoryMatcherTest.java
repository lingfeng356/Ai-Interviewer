package com.lingfeng.interviewer.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史问答对解析与匹配的单元测试。
 * 这些逻辑是 searchHistory 工具的核心，抽成纯函数后可以不连 Redis 直接测。
 */
class ChatHistoryMatcherTest {

    private List<String> sample() {
        List<String> raw = new ArrayList<>();
        raw.add("assistant:请先做个自我介绍");
        raw.add("user:我是大二学生，主要写 Java");
        raw.add("assistant:说说你项目里 Redis 是怎么用的");
        raw.add("user:用来做商品缓存，QPS 从 800 提到 5000");
        raw.add("assistant:那缓存和数据库不一致怎么办");
        return raw;
    }

    @Test
    @DisplayName("能取到开场白，并数出候选人回答了几轮")
    void openingAndAnsweredCount() {
        assertEquals("请先做个自我介绍", ChatHistoryMatcher.opening(sample()));
        assertEquals(2, ChatHistoryMatcher.answeredCount(sample()));

        assertNull(ChatHistoryMatcher.opening(null));
        assertEquals(0, ChatHistoryMatcher.answeredCount(null));
        assertEquals(0, ChatHistoryMatcher.answeredCount(List.of()));
    }

    @Test
    @DisplayName("能把历史还原成问答对，末尾未回答的那一问不计入")
    void pairsUnansweredTailIgnored() {
        List<ChatHistoryMatcher.QaPair> pairs = ChatHistoryMatcher.pairs(sample());

        assertEquals(2, pairs.size());
        assertEquals(1, pairs.get(0).questionNo());
        assertTrue(pairs.get(0).question().contains("自我介绍"));
        assertTrue(pairs.get(1).answer().contains("QPS"));
    }

    @Test
    @DisplayName("格式不合法或为空的历史不会抛异常")
    void pairsToleratesBadInput() {
        assertTrue(ChatHistoryMatcher.pairs(null).isEmpty());
        assertTrue(ChatHistoryMatcher.pairs(List.of()).isEmpty());
        // 没有冒号、没有对应问题的 user，都应该被跳过
        assertTrue(ChatHistoryMatcher.pairs(List.of("assistant 没有冒号", "user:孤立的回答")).isEmpty());
    }

    @Test
    @DisplayName("关键词命中问题或回答都算命中")
    void matchByQuestionOrAnswer() {
        // “缓存”只出现在回答里
        List<ChatHistoryMatcher.QaPair> byAnswer = ChatHistoryMatcher.match(sample(), "缓存", 3);
        assertEquals(1, byAnswer.size());
        assertEquals(2, byAnswer.get(0).questionNo());

        // “自我介绍”只出现在问题里
        List<ChatHistoryMatcher.QaPair> byQuestion = ChatHistoryMatcher.match(sample(), "自我介绍", 3);
        assertEquals(1, byQuestion.size());
        assertEquals(1, byQuestion.get(0).questionNo());
    }

    @Test
    @DisplayName("没命中返回空列表；空关键词也返回空")
    void matchMissAndBlank() {
        assertTrue(ChatHistoryMatcher.match(sample(), "Kafka", 3).isEmpty());
        assertTrue(ChatHistoryMatcher.match(sample(), "  ", 3).isEmpty());
        assertTrue(ChatHistoryMatcher.match(sample(), null, 3).isEmpty());
    }

    @Test
    @DisplayName("超出条数时保留最近的几条")
    void matchKeepsLatest() {
        List<String> raw = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            raw.add("assistant:第 " + i + " 个 Redis 问题");
            raw.add("user:第 " + i + " 个回答");
        }

        List<ChatHistoryMatcher.QaPair> hits = ChatHistoryMatcher.match(raw, "Redis", 2);

        assertEquals(2, hits.size());
        assertEquals(4, hits.get(0).questionNo());
        assertEquals(5, hits.get(1).questionNo());
    }
}
