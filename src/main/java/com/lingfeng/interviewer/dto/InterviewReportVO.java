package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class InterviewReportVO {
    private String sessionId;
    private Integer totalScore;
    private String summary;
    private List<String> strengths;      // 改成 List
    private List<String> weaknesses;     // 改成 List
    private String suggestion;
    private LocalDateTime createdTime;
}