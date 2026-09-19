package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplyRequest {

    private String sessionId;
    private String answer;
}
