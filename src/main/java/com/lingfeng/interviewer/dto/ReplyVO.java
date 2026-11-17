package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReplyVO {
    private String sessionId;
    private String message;
    private String aiAudio;   // base64 编码的音频

    /** 当前面试环节的枚举名，如 SELF_INTRO / PROJECT */
    private String stage;

    /** 当前面试环节的中文名，如"项目经历"，前端直接展示 */
    private String stageName;

    /**
     * 本轮 Agent 的执行摘要（工具调用轨迹）。
     * 经典模式下为 null；前端拿不到就只管展示 message，属于纯增量字段。
     */
    private AgentReplyVO agent;
}
