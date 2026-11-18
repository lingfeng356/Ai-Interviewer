package com.lingfeng.interviewer.questionbank;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 题库里的一道题。
 *
 * points 是「答题要点」，给面试官 Agent 用来判断候选人答得全不全，
 * 不是给候选人看的答案；followUps 是可选的自然追问方向。
 */
@Data
@NoArgsConstructor
public class QuestionItem {

    private String id;

    /** 分类，如 Java基础 / 集合 / 并发 / MySQL / Redis */
    private String category;

    /** 具体知识点，如 HashMap 扩容 */
    private String topic;

    /** 难度 1 基础 / 2 进阶 / 3 深入 */
    private int difficulty;

    private String question;

    /** 答题要点（简要，2~4 条） */
    private List<String> points;

    /** 可选的追问方向 */
    private List<String> followUps;
}
