package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.common.InterviewStage;
import com.lingfeng.interviewer.common.RedisKeyConstants;
import com.lingfeng.interviewer.questionbank.QuestionBankService;
import com.lingfeng.interviewer.questionbank.QuestionItem;
import com.lingfeng.interviewer.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 面试官 Agent 的工具集（第一版 3 个）。
 *
 * 设计约定（改这个类之前先读）：
 * 1. 工具方法内部绝不抛异常 —— 失败也要返回一段可读文本，让模型自己决定重试还是换策略。
 *    抛异常会直接打断整轮 Agent，用户体验就是“AI 报错了”。
 * 2. 每个工具方法都要记 trace，否则“可观测”就是空话。
 * 3. 工具只读或只写“安全的小数据”（笔记），不执行任何自由写库 / 读任意路径的操作。
 * 4. 这个对象是每次 reply 新建的，可以安全地持有本轮上下文。
 */
@Slf4j
public class InterviewTools {

    /** 一次检索最多返回几段简历原文 */
    private static final int MAX_RESUME_HITS = 3;
    /** 单段简历原文的截断长度 */
    private static final int SNIPPET_LEN = 260;
    /** 单场面试的笔记条数上限 */
    private static final int MAX_MEMO = 10;
    /** 一次历史检索最多返回几轮问答 */
    private static final int MAX_HISTORY_HITS = 3;

    private final AgentContext ctx;
    private final RagService ragService;
    private final QuestionBankService questionBankService;
    private final StringRedisTemplate stringRedisTemplate;
    private final AgentTraceRecorder trace;

    public InterviewTools(AgentContext ctx,
                          RagService ragService,
                          QuestionBankService questionBankService,
                          StringRedisTemplate stringRedisTemplate,
                          AgentTraceRecorder trace) {
        this.ctx = ctx;
        this.ragService = ragService;
        this.questionBankService = questionBankService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.trace = trace;
    }

    /**
     * 简历检索。
     * 为什么需要它：简历动辄几千字，每轮都塞进 prompt 又长又费 token；
     * 让模型按需检索，才是真正的 tool use（而不是写死的 pipeline）。
     */
    @Tool(description = """
            在候选人简历中按关键词检索原文片段。
            当你想确认候选人是否真的做过某项技术、或需要引用简历里的项目细节时调用。
            不要用它查技术知识（那是 searchKnowledge 的职责）；也不要为了“显得认真”每轮都调用。
            """)
    public String searchResume(
            @ToolParam(description = "检索关键词，尽量用一个技术名词或项目名，如 Redis、订单系统、实习") String keyword) {

        long start = System.currentTimeMillis();
        String kw = keyword == null ? "" : keyword.trim();

        // 预算守卫：超了就返回提示，不执行
        if (trace.exhausted()) {
            return stopMessage("searchResume");
        }
        if (kw.isEmpty()) {
            record("searchResume", kw, "关键词为空", start, false);
            return "关键词不能为空，请换一个技术名词或项目名再试。";
        }
        if (kw.length() > 30) {
            kw = kw.substring(0, 30);
        }

        String resume = ctx.resumeText();
        if (resume.isBlank()) {
            record("searchResume", kw, "简历正文为空", start, false);
            return "候选人的简历正文为空（可能是 PDF 解析失败），不要引用简历细节，改为围绕他刚才的回答提问。";
        }

        String hit = findSnippets(resume, kw);
        record("searchResume", kw, hit, start, true);
        return hit;
    }

    /**
     * 知识库检索：给面试官一份“标准答案要点”，用来判断候选人答得对不对。
     * 底层复用已有的 RagService，不新增检索实现。
     */
    @Tool(description = """
            检索技术知识库，获取某个知识点的标准答案要点。
            当你需要判断候选人的回答是否准确、或想追问更深入的技术细节时调用。
            命中率不高时不要反复换关键词硬查，直接基于你的经验判断。
            """)
    public String searchKnowledge(
            @ToolParam(description = "要查的技术问题，如 Redis 持久化方式、MySQL 索引失效场景") String question) {

        long start = System.currentTimeMillis();
        String q = question == null ? "" : question.trim();

        if (trace.exhausted()) {
            return stopMessage("searchKnowledge");
        }
        if (q.isEmpty()) {
            record("searchKnowledge", q, "问题为空", start, false);
            return "问题不能为空。";
        }

        try {
            String context = ragService.retrieve(q);
            if (context == null || context.isBlank()) {
                record("searchKnowledge", q, "未命中", start, true);
                return "知识库中没有检索到相关内容，请基于面试官的经验自行判断，不要编造标准答案。";
            }
            String trimmed = context.length() > 500 ? context.substring(0, 500) : context;
            record("searchKnowledge", q, trimmed, start, true);
            return trimmed;
        } catch (Exception e) {
            // 检索失败不是致命错误：告诉模型“没查到参考”，让它继续
            log.warn("知识库检索失败, question={}", q, e);
            record("searchKnowledge", q, "检索异常: " + e.getMessage(), start, false);
            return "知识库暂时不可用，请基于你自己的技术经验继续提问，但不要断言候选人的答案一定错误。";
        }
    }

    /**
     * 题库检索：拿一道「经典题 + 答题要点」，用来对齐标准考点。
     *
     * 和 searchKnowledge 的分工：
     * - searchKnowledge 查的是「这个知识点的标准答案」；
     * - searchQuestionBank 查的是「这个考点通常怎么问、要答到哪几点」。
     * 前者用来判断对错，后者用来出题和自查覆盖度。
     */
    @Tool(description = """
            从题库中检索某个知识点的经典面试题和答题要点。
            当你想考察一个标准考点（如 HashMap 扩容、MySQL 索引失效、缓存穿透），
            或者想确认自己的追问有没有覆盖关键答题点时调用。
            不要用它替代顺着候选人回答的追问；一次最多取 2 道题。
            """)
    public String searchQuestionBank(
            @ToolParam(description = "知识点关键词，如 HashMap、线程池、索引失效、缓存穿透") String topic,
            @ToolParam(description = "难度：1 基础 / 2 进阶 / 3 深入；不确定就传 2") String difficulty) {

        long start = System.currentTimeMillis();
        String kw = topic == null ? "" : topic.trim();

        if (trace.exhausted()) {
            return stopMessage("searchQuestionBank");
        }
        if (kw.isEmpty()) {
            record("searchQuestionBank", kw, "关键词为空", start, false);
            return "关键词不能为空，请换一个知识点再试。";
        }

        try {
            Integer level = parseDifficulty(difficulty);
            List<QuestionItem> hits = questionBankService.search(kw, level, 2);
            if (hits.isEmpty()) {
                String categories = String.join("、", questionBankService.categories());
                String msg = "题库里没有命中「" + kw + "」。可用分类：" + categories
                        + "。请基于你自己的技术经验提问，不要编造题库内容。";
                record("searchQuestionBank", kw, msg, start, true);
                return msg;
            }

            StringBuilder sb = new StringBuilder("题库命中 ").append(hits.size()).append(" 道：\n");
            int index = 1;
            for (QuestionItem q : hits) {
                sb.append(index++).append(". 【").append(q.getCategory()).append(" · ")
                        .append(q.getTopic()).append("｜难度 ").append(q.getDifficulty()).append("】")
                        .append(q.getQuestion()).append('\n');
                if (q.getPoints() != null && !q.getPoints().isEmpty()) {
                    sb.append("   答题要点：").append(String.join("；", q.getPoints())).append('\n');
                }
                if (q.getFollowUps() != null && !q.getFollowUps().isEmpty()) {
                    sb.append("   可追问：").append(String.join("；", q.getFollowUps())).append('\n');
                }
            }
            String result = sb.toString();
            record("searchQuestionBank", kw, result, start, true);
            return result;
        } catch (Exception e) {
            log.warn("题库检索失败, topic={}", kw, e);
            record("searchQuestionBank", kw, "检索异常: " + e.getMessage(), start, false);
            return "题库暂时不可用，请基于你自己的技术经验提问。";
        }
    }

    /**
     * 历史检索：解决"AI 面试官重复提问"。
     *
     * 模型只看到最近若干轮上下文，聊久了就忘了自己问过什么，
     * 于是换个说法再问一遍——用户体感极差。把这个能力做成工具，
     * 让它在"想问但不确定"的时候自己查一下。
     */
    @Tool(description = """
            在已经聊过的对话里按关键词查“我问过什么、候选人怎么答的”。
            当你想确认某个问题是不是已经问过、避免重复提问时调用。
            查不到说明确实没问过，可以放心问。
            """)
    public String searchHistory(
            @ToolParam(description = "关键词，如 缓存、索引、项目、并发") String keyword) {

        long start = System.currentTimeMillis();
        String kw = keyword == null ? "" : keyword.trim();

        if (trace.exhausted()) {
            return stopMessage("searchHistory");
        }
        if (kw.isEmpty()) {
            record("searchHistory", kw, "关键词为空", start, false);
            return "关键词不能为空，请换一个技术名词再试。";
        }

        try {
            Long size = stringRedisTemplate.opsForList().size(ctx.redisKey());
            int count = size == null ? 0 : size.intValue();
            if (count == 0) {
                record("searchHistory", kw, "历史为空", start, true);
                return "还没有对话历史，这是第一轮提问。";
            }

            List<String> raw = stringRedisTemplate.opsForList()
                    .range(ctx.redisKey(), 0, count - 1);
            List<ChatHistoryMatcher.QaPair> hits =
                    ChatHistoryMatcher.match(raw, kw, MAX_HISTORY_HITS);

            if (hits.isEmpty()) {
                String msg = "历史里没有问到过「" + kw + "」，可以放心问。";
                record("searchHistory", kw, msg, start, true);
                return msg;
            }

            StringBuilder sb = new StringBuilder("历史里命中 ").append(hits.size()).append(" 轮问答：\n");
            for (ChatHistoryMatcher.QaPair pair : hits) {
                sb.append("· 第 ").append(pair.questionNo()).append(" 问 面试官：")
                        .append(AgentTraceRecorder.truncate(pair.question(), 80)).append('\n')
                        .append("  候选人：")
                        .append(AgentTraceRecorder.truncate(pair.answer(), 120)).append('\n');
            }
            sb.append("注意：这些角度已经问过，不要重复；要追问就换个更深的点。");

            String result = sb.toString();
            record("searchHistory", kw, result, start, true);
            return result;
        } catch (Exception e) {
            // 查不到历史不影响面试，但要让模型知道别硬查
            log.warn("查询对话历史失败, sessionId={}", ctx.sessionId(), e);
            record("searchHistory", kw, "查询异常", start, false);
            return "暂时查不到历史，请基于你看到的对话继续提问，并尽量避免重复。";
        }
    }

    /**
     * 进度查询：让模型知道自己还剩多少空间。
     * 只读，不改变任何状态；调用会计入本轮工具预算，所以 prompt 里提示它别每轮都查。
     */
    @Tool(description = """
            查看当前面试进度：在哪个环节、整场第几问、本环节还剩几题。
            当你想判断是不是该收尾、或者想对候选人说“时间不多了”时调用；只读，不改状态。
            """)
    public String getInterviewProgress() {

        long start = System.currentTimeMillis();
        if (trace.exhausted()) {
            return stopMessage("getInterviewProgress");
        }

        InterviewStage stage = ctx.stage();
        int answered = ctx.answeredRounds();
        int stageStart = stage.getFromRound();
        int stageLength = stage.getToRound() - stageStart;
        int asked = Math.max(0, answered - stageStart);
        int remaining = Math.max(0, stageLength - asked);

        StringBuilder sb = new StringBuilder()
                .append("当前环节：").append(stage.getLabel())
                .append("；这是整场第 ").append(answered + 1).append(" 问")
                .append("；本环节已问 ").append(asked).append(" 题，约剩 ").append(remaining).append(" 题");
        if (stage == InterviewStage.REVERSE) {
            sb.append("；这是最后一个环节，问完就可以结束面试");
        } else {
            sb.append("；该环节结束后进入：").append(nextStageLabel(stage));
        }

        String result = sb.toString();
        record("getInterviewProgress", "-", result, start, true);
        return result;
    }

    /**
     * 工作记忆：把对候选人的观察沉淀下来。
     * 这份笔记会在后续轮次注入 system prompt，并在最终评分时一起交给评估模型，
     * 解决“面试官每轮都重新认识候选人”的问题。
     */
    @Tool(description = """
            记录一条对候选人的观察，供最终评分和后续追问使用。
            例如：回答了关键点（STRENGTH）、明显含糊或说错（WEAKNESS）、提到但没展开需要追问（DOUBT）。
            只在确有信息量时记录，不要每轮都写。
            """)
    public String saveMemo(
            @ToolParam(description = "类型，只能是 STRENGTH / WEAKNESS / DOUBT 三选一") String type,
            @ToolParam(description = "一句话观察，不超过 80 字，如：说用过 Redis 但没提持久化方案") String note) {

        long start = System.currentTimeMillis();
        if (trace.exhausted()) {
            return stopMessage("saveMemo");
        }

        String t = normalizeType(type);
        String text = note == null ? "" : note.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            record("saveMemo", t, "内容为空", start, false);
            return "笔记内容不能为空。";
        }
        if (text.length() > 80) {
            text = text.substring(0, 80);
        }

        String key = RedisKeyConstants.agentMemo(ctx.sessionId());
        Long size = stringRedisTemplate.opsForList().size(key);
        if (size != null && size >= MAX_MEMO) {
            record("saveMemo", t, "笔记已达上限", start, false);
            return "笔记已满（最多 " + MAX_MEMO + " 条），请基于已有信息继续面试。";
        }

        try {
            // 存储格式：“类型|轮次|内容”，读取时按第一个 | 切分，避免 JSON 序列化的额外依赖
            stringRedisTemplate.opsForList()
                    .rightPush(key, t + "|" + ctx.answeredRounds() + "|" + text);
            stringRedisTemplate.expire(key, 7, TimeUnit.DAYS);
            record("saveMemo", t + ": " + text, "已记录", start, true);
            return "已记录。";
        } catch (Exception e) {
            log.warn("保存面试笔记失败, sessionId={}", ctx.sessionId(), e);
            record("saveMemo", t, "保存失败", start, false);
            return "笔记保存失败，不影响继续面试。";
        }
    }

    /* ==================== 内部工具方法 ==================== */

    /** 在简历原文里找 3 段命中上下文，合并成一段可读文本 */
    private String findSnippets(String resume, String keyword) {
        List<String> snippets = new ArrayList<>();
        int from = 0;
        while (snippets.size() < MAX_RESUME_HITS) {
            int idx = resume.indexOf(keyword, from);
            if (idx < 0) {
                break;
            }
            int begin = Math.max(0, idx - 60);
            int end = Math.min(resume.length(), idx + keyword.length() + SNIPPET_LEN);
            String snippet = resume.substring(begin, end).replaceAll("\\s+", " ").trim();
            snippets.add((begin > 0 ? "…" : "") + snippet + (end < resume.length() ? "…" : ""));
            from = end;
        }

        if (snippets.isEmpty()) {
            return "简历中没有找到与「" + keyword + "」相关的内容，说明候选人可能没写过这条，谨慎追问。";
        }
        StringBuilder sb = new StringBuilder("简历中与「").append(keyword).append("」相关的片段：\n");
        for (int i = 0; i < snippets.size(); i++) {
            sb.append(i + 1).append(". ").append(snippets.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** 预算用尽时统一返回的提示：明确告诉模型“别再调了，去给最终问题” */
    private String stopMessage(String tool) {
        record(tool, "-", "预算用尽，已拦截", System.currentTimeMillis(), false);
        return "【系统提示】本轮工具调用次数或时间已达上限，请不要继续调用工具，直接根据已有信息给出你接下来要问候选人的问题。";
    }

    private void record(String tool, String args, String result, long start, boolean success) {
        trace.record(tool, args, result, System.currentTimeMillis() - start, success);
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "DOUBT";
        }
        String t = type.trim().toUpperCase();
        return switch (t) {
            case "STRENGTH", "WEAKNESS" -> t;
            default -> "DOUBT";
        };
    }

    /** 下一个环节的名字（只用于给模型展示进度，不改变流程） */
    private String nextStageLabel(InterviewStage stage) {
        return switch (stage) {
            case SELF_INTRO -> InterviewStage.PROJECT.getLabel();
            case PROJECT -> InterviewStage.TECH.getLabel();
            case TECH -> InterviewStage.REVERSE.getLabel();
            case REVERSE -> "无（已是最后环节）";
        };
    }

    /** 难度参数容错：模型可能传 "2"、"进阶"、"中等"，统一成 1~3 */
    private Integer parseDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return 2;
        }
        String d = difficulty.trim();
        if (d.contains("1") || d.contains("基础") || d.contains("简单")) {
            return 1;
        }
        if (d.contains("3") || d.contains("深入") || d.contains("困难")) {
            return 3;
        }
        if (d.contains("2") || d.contains("进阶") || d.contains("中等")) {
            return 2;
        }
        return 2;
    }
}
