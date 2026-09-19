package com.lingfeng.interviewer.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class InterviewReport {
    private Long id;
    private String sessionId;
    private int totalScore;
    private String summary;
    private String strengths;
    private String weaknesses;
    private String suggestion;
    private LocalDateTime createdTime;
}
