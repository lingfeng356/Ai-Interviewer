package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简历里的一段项目经历（结构化解析结果的一部分）。
 *
 * 字段都保持成 String，是因为 AI 抽取出来的内容格式很自由
 * （比如"3 个月""2027 届"），用强类型反而容易解析失败。
 */
@Data
@NoArgsConstructor
public class ProjectItem {

    /** 项目名称 */
    private String name;

    /** 候选人承担的角色，如"后端开发" */
    private String role;

    /** 用到的技术栈，如"Spring Boot、MySQL、Redis" */
    private String techStack;

    /** 主要做了什么事（一两句话） */
    private String description;
}
