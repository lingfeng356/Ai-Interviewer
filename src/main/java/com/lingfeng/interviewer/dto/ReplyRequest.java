package com.lingfeng.interviewer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplyRequest {

    @NotBlank(message = "sessionId不能为空")
    private String sessionId;
    @NotBlank(message = "回答不能为空")
    private String answer;
}
