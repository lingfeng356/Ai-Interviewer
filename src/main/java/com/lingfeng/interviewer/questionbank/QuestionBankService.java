package com.lingfeng.interviewer.questionbank;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 题库服务：启动时把 resources/question-bank/java-backend.json 读进内存，提供关键词检索。
 *
 * 为什么用资源文件而不是建表：
 * 1. 题库是静态数据，改题目不需要动数据库，也不逼着现有库做迁移；
 * 2. 放在 git 里，题目改动可 review、可回滚；
 * 3. 56 道题的规模，内存线性扫描完全够用（毫秒级），没必要上检索引擎。
 * 等题库上千道、要按标签组合查询时，再换成表 + 索引也不迟。
 *
 * 检索是「关键词包含 + 简单加权」，没有引入分词器：
 * 中文分词的错误率反而会让「HashMap」这类专有名词查不到，
 * 而面试提问的关键词基本都是技术名词，直接包含匹配更稳。
 */
@Slf4j
@Component
public class QuestionBankService {

    private static final String RESOURCE = "question-bank/java-backend.json";

    private final ObjectMapper objectMapper;

    /** 题库是只读的，加载完成后整份替换引用，读侧无需加锁 */
    private volatile List<QuestionItem> questions = List.of();

    public QuestionBankService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            QuestionBank bank = objectMapper.readValue(in, QuestionBank.class);
            List<QuestionItem> list = bank.getQuestions() == null ? List.of() : bank.getQuestions();
            this.questions = List.copyOf(list);
            log.info("题库加载完成: {} 道题, {} 个分类", questions.size(), categories().size());
        } catch (Exception e) {
            // 题库加载失败不能让应用起不来：工具会返回「题库不可用」，面试继续
            log.warn("题库加载失败，题库工具将不可用", e);
            this.questions = List.of();
        }
    }

    public int size() {
        return questions.size();
    }

    /** 分类列表（保持文件里的顺序，去重） */
    public List<String> categories() {
        Set<String> set = new LinkedHashSet<>();
        for (QuestionItem q : questions) {
            if (q.getCategory() != null && !q.getCategory().isBlank()) {
                set.add(q.getCategory());
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 按关键词检索题目。
     *
     * @param keyword    知识点关键词，如 HashMap、索引失效
     * @param difficulty 期望难度（可空），命中后按难度接近程度做次级排序
     * @param limit      最多返回几道
     */
    public List<QuestionItem> search(String keyword, Integer difficulty, int limit) {
        if (questions.isEmpty() || limit <= 0) {
            return List.of();
        }
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return List.of();
        }

        List<Scored> candidates = new ArrayList<>();
        for (QuestionItem q : questions) {
            int score = score(q, kw);
            if (score > 0) {
                candidates.add(new Scored(q, score));
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        candidates.sort(Comparator
                .comparingInt(Scored::score).reversed()
                .thenComparingInt(s -> difficultyDistance(s.item(), difficulty))
                .thenComparing(s -> s.item().getId()));

        List<QuestionItem> result = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < limit; i++) {
            result.add(candidates.get(i).item());
        }
        return result;
    }

    /* ==================== 内部实现 ==================== */

    private record Scored(QuestionItem item, int score) {
    }

    /**
     * 打分规则（故意简单，便于解释和调参）：
     * - 命中知识点 +5，命中分类 +3，命中题干 +2，命中要点 +1
     * - 再把查询拆成词，逐词在知识点/题干/分类里加分
     * 这样「HashMap」会命中知识点而排第一，「缓存」会命中多个分类但按分类聚拢。
     */
    private int score(QuestionItem q, String keyword) {
        String kw = lower(keyword);
        int score = 0;

        if (lower(q.getTopic()).contains(kw)) {
            score += 5;
        }
        if (lower(q.getCategory()).contains(kw)) {
            score += 3;
        }
        if (lower(q.getQuestion()).contains(kw)) {
            score += 2;
        }
        for (String point : safe(q.getPoints())) {
            if (lower(point).contains(kw)) {
                score += 1;
            }
        }

        for (String token : tokens(keyword)) {
            if (token.length() < 2) {
                continue;
            }
            String t = lower(token);
            if (lower(q.getTopic()).contains(t)) {
                score += 2;
            }
            if (lower(q.getQuestion()).contains(t)) {
                score += 1;
            }
            if (lower(q.getCategory()).contains(t)) {
                score += 1;
            }
        }
        return score;
    }

    private int difficultyDistance(QuestionItem q, Integer difficulty) {
        return difficulty == null ? 0 : Math.abs(q.getDifficulty() - difficulty);
    }

    /** 拆分查询：按空白和常见中英文标点切开，过滤空串 */
    private List<String> tokens(String keyword) {
        List<String> result = new ArrayList<>();
        for (String t : keyword.split("[\\s,，。.;；:：、/|()（）\\[\\]【】]+")) {
            if (!t.isBlank()) {
                result.add(t);
            }
        }
        return result;
    }

    private List<String> safe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
