package com.lingfeng.interviewer.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class InterviewSession {

    private Long id;
    private String sessionId;
    private String status;
    private LocalDateTime createdTime;
    private Long resumeId;
    private Long userId;
    private String provider;
}
