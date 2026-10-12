package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResumeUploadVO {

    //简历ID
    private Long resumeId;

    //原始文件名
    private String fileName;

    //文件存储路径
    private String filePath;

    /**
     * 结构化解析结果（技能栈 / 项目经历 / 学历…）。
     * 解析失败时为 null —— 上传本身仍然算成功，只是退化成纯文本模式。
     */
    private ResumeProfile profile;
}
