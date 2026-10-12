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
    private LocalDateTime createdTime;
}
