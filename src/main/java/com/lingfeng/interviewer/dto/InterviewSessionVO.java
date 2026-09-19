package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class InterviewSessionVO {

    private String sessionId;
    private String status;
    private Integer totalScore;
    private LocalDateTime createdTime;
}
