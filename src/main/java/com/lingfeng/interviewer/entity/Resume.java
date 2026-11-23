package com.lingfeng.interviewer.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class Resume {
    private Long id;
    private String fileName;
    private String filePath;

    /** Tika 解析出来的简历纯文本 */
    private String content;

    /** 大模型抽取出来的结构化画像（ResumeProfile 的 JSON 字符串），解析失败时为 null */
    private String profile;

    private String status;

    /**
     * 简历所属用户。
     * 早期表里没有这个字段，所以历史数据可能为 null；
     * 上传时写入，开始面试时用它做越权校验。
     */
    private Long userId;

    private LocalDateTime createdTime;
}
