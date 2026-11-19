package com.lingfeng.interviewer.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingfeng.interviewer.common.InterviewStage;
import com.lingfeng.interviewer.questionbank.QuestionBankService;
import com.lingfeng.interviewer.service.RagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 核心逻辑的单元测试（不依赖 MySQL / Redis / 大模型）。
 *
 * 只测三件最容易改坏、又不需要外部环境的事：
 * 1. 经典 prompt 没被改坏（阶段进度文案）
 * 2. Agent prompt 带上了工具说明与额度
 * 3. 工具预算的守卫、简历检索的命中/未命中
 */
class AgentCoreTest {

    /* ==================== 1. Prompt 组装 ==================== */

    @Test
    @DisplayName("经典 prompt 仍包含阶段进度，且切换环节时提示自然过渡")
    void classicPromptKeepsStageProgress() {
        String p1 = AgentPromptBuilder.classic(InterviewStage.SELF_INTRO, 0);
        assertTrue(p1.contains("第 1 题"), "开场应显示本环节第 1 题");
        assertTrue(p1.contains("自我介绍"));

        // 进入项目环节的第 1 题（已回答 2 轮）
        String p2 = AgentPromptBuilder.classic(InterviewStage.PROJECT, 2);
        assertTrue(p2.contains("项目经历"));
        assertTrue(p2.contains("刚刚从上一个环节切换"), "环节切换时应提示自然过渡");
    }

    @Test
    @DisplayName("Agent prompt 带上三个工具与剩余额度")
    void agentPromptMentionsToolsAndBudget() {
        String prompt = AgentPromptBuilder.agent(InterviewStage.TECH, 6, "· [疑点] 没说清索引失效", 3);

        assertTrue(prompt.contains("searchResume"));
        assertTrue(prompt.contains("searchKnowledge"));
        assertTrue(prompt.contains("saveMemo"));
        assertTrue(prompt.contains("searchHistory"));
        assertTrue(prompt.contains("getInterviewProgress"));
        assertTrue(prompt.contains("本轮最多还能调用 3 次工具"));
        assertTrue(prompt.contains("没说清索引失效"), "笔记应注入 prompt");
        assertTrue(prompt.contains("技术基础"));
    }

    @Test
    @DisplayName("没有笔记时不应出现空笔记区块")
    void agentPromptWithoutMemo() {
        String prompt = AgentPromptBuilder.agent(InterviewStage.REVERSE, 12, "", 5);
        assertFalse(prompt.contains("你自己的面试笔记"));
    }

    /* ==================== 2. 工具预算 ==================== */

    @Test
    @DisplayName("工具预算：用满次数后 exhausted() 为 true，remaining() 归零")
    void traceBudgetByCount() {
        AgentTraceRecorder trace = new AgentTraceRecorder(2, 60_000);
        assertFalse(trace.exhausted());
        assertEquals(2, trace.remaining());

        trace.record("searchResume", "Redis", "命中 1 段", 10, true);
        trace.record("saveMemo", "DOUBT", "已记录", 5, true);

        assertTrue(trace.exhausted());
        assertEquals(0, trace.remaining());
        assertEquals(2, trace.count());
        assertEquals("searchResume", trace.steps().get(0).getTool());
        assertEquals(1, trace.steps().get(0).getStep());
    }

    @Test
    @DisplayName("工具预算：超时后同样视为耗尽")
    void traceBudgetByTime() {
        AgentTraceRecorder trace = new AgentTraceRecorder(10, -1); // 预算立刻过期
        assertTrue(trace.exhausted());
    }

    /* ==================== 3. 简历检索工具 ==================== */

    @Test
    @DisplayName("searchResume 命中时返回原文片段，并在 trace 里留下记录")
    void searchResumeHits() {
        AgentTraceRecorder trace = new AgentTraceRecorder(5, 60_000);
        ReplyContext ctx = contextWithResume("实习期间用 Redis 做过商品缓存，QPS 从 800 提到 5000。",
                trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.searchResume("Redis");

        assertTrue(result.contains("Redis"));
        assertTrue(result.contains("简历中与「Redis」相关"));
        assertEquals(1, trace.count());
        assertTrue(trace.steps().get(0).isSuccess());
    }

    @Test
    @DisplayName("searchResume 未命中时给出明确提示，不抛异常")
    void searchResumeMiss() {
        AgentTraceRecorder trace = new AgentTraceRecorder(5, 60_000);
        ReplyContext ctx = contextWithResume("熟悉 Java 与 Spring Boot。", trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.searchResume("Kafka");

        assertTrue(result.contains("没有找到"));
        assertEquals(1, trace.count());
    }

    @Test
    @DisplayName("预算耗尽后工具被拦截，返回“别再调工具”的指令")
    void searchResumeBlockedWhenBudgetExhausted() {
        AgentTraceRecorder trace = new AgentTraceRecorder(0, 60_000); // 一次都不许调
        ReplyContext ctx = contextWithResume("Redis 缓存。", trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.searchResume("Redis");

        assertTrue(result.contains("工具调用次数或时间已达上限"));
    }

    /* ==================== 4. 题库工具 ==================== */

    @Test
    @DisplayName("题库能按知识点命中，并返回答题要点")
    void questionBankHit() {
        AgentTraceRecorder trace = new AgentTraceRecorder(5, 60_000);
        ReplyContext ctx = contextWithResume("简历", trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.searchQuestionBank("HashMap", "2");

        assertTrue(result.contains("题库命中"));
        assertTrue(result.contains("HashMap"));
        assertTrue(result.contains("答题要点"));
        assertEquals(1, trace.count());
    }

    @Test
    @DisplayName("题库未命中时给出可用分类，而不是抛异常")
    void questionBankMiss() {
        AgentTraceRecorder trace = new AgentTraceRecorder(5, 60_000);
        ReplyContext ctx = contextWithResume("简历", trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.searchQuestionBank("量子计算", "1");

        assertTrue(result.contains("没有命中"));
        assertTrue(result.contains("可用分类"));
    }

    @Test
    @DisplayName("题库文件能正常加载，且包含足够的题目")
    void questionBankLoads() {
        assertTrue(bank().size() >= 50, "题库应至少有 50 道题");
        assertTrue(bank().categories().contains("Redis"));
    }

    @Test
    @DisplayName("进度工具返回环节、场次和剩余题数")
    void progressToolReportsStage() {
        AgentTraceRecorder trace = new AgentTraceRecorder(5, 60_000);
        ReplyContext ctx = contextWithResume("简历", trace);
        InterviewTools tools = new InterviewTools(new AgentContext(ctx), new NoopRag(), bank(), null, trace);

        String result = tools.getInterviewProgress();

        assertTrue(result.contains("项目经历"));
        assertTrue(result.contains("整场第 4 问"));
        assertTrue(result.contains("约剩 3 题"));
        assertEquals(1, trace.count());
    }

    /* ==================== 辅助 ==================== */

    /** 加载真实的题库资源文件（不走 Spring） */
    private QuestionBankService bank() {
        QuestionBankService service = new QuestionBankService(new ObjectMapper());
        service.load();
        return service;
    }

    private ReplyContext contextWithResume(String resumeText, AgentTraceRecorder trace) {
        return new ReplyContext(
                "sid-test",
                "interview:history:sid-test",
                "deepseek",
                java.util.List.of(),
                InterviewStage.PROJECT,
                3,
                1L,
                resumeText,
                "{}");
    }

    /** 避免真的去连 pgvector / Ollama */
    private static class NoopRag implements RagService {
        @Override
        public void loadKnowledgeBase(String filePath) {
        }

        @Override
        public String ask(String query) {
            return "";
        }

        @Override
        public String retrieve(String query) {
            return "";
        }
    }
}
