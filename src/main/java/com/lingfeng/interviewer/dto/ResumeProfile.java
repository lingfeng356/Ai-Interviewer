package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 简历的结构化解析结果（候选人画像）。
 *
 * 名字里带"Profile"是因为它不是简历原文，而是从原文里抽取出来的要点。
 * 面试时把这段要点喂给面试官模型，比塞几千字原文更聚焦、也更省 token。
 */
@Data
@NoArgsConstructor
public class ResumeProfile {

    /** 姓名 */
    private String name;

    /** 学历，如"本科""硕士" */
    private String education;

    /** 学校 */
    private String school;

    /** 专业 */
    private String major;

    /** 毕业年份，如"2027 届"（用字符串，兼容各种写法） */
    private String graduateYear;

    /** 实习/工作年限（简历里没写就是 null） */
    private Integer yearsOfExperience;

    /** 技能栈，如 ["Java", "Spring Boot", "MySQL", "Redis"] */
    private List<String> skills;

    /** 项目经历，最多几条由解析时的 prompt 限制 */
    private List<ProjectItem> projects;

    /** 一句话概括候选人的定位 */
    private String summary;
}
