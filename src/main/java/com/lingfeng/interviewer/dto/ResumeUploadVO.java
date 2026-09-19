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
}
