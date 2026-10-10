package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class StartInterviewVO {

    private String sessionId;
    private String message;

    /** 当前面试环节的枚举名，如 SELF_INTRO */
    private String stage;

    /** 当前面试环节的中文名，如"自我介绍" */
    private String stageName;
}
