package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplyVO {
    private String sessionId;
    private String message;
    private String aiAudio;   // base64 编码的音频
}
