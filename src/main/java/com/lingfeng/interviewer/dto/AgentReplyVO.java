package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 一轮回答里 Agent 的执行摘要，挂在 ReplyVO.agent 上返回给前端。
 *
 * mode 有三种取值：
 * - AGENT    ：正常走工具调用
 * - FALLBACK ：Agent 执行出错（模型不支持工具 / 返回格式异常），本轮已降级
 * - CLASSIC  ：本场面试本来就是经典模式，不经过 Agent
 */
@Data
@NoArgsConstructor
public class AgentReplyVO {

    private String mode;

    /** 本轮工具调用次数 */
    private int steps;

    /** 本轮总耗时（含大模型时间） */
    private long elapsedMs;

    private List<AgentStepVO> toolCalls = new ArrayList<>();
}
