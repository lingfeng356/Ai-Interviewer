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
    private String content;
    private String status;
    private LocalDateTime createdTime;
}
