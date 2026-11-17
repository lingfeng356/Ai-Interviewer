package com.lingfeng.interviewer.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 的一次工具调用记录（返回给前端渲染时间线，也用于问题排查）。
 *
 * 注意：这里记录的是「可观测的工具调用链」——
 * 工具名、参数、结果摘要、耗时、成功与否。
 * 不是模型的隐式思维链（DeepSeek 的 chat 模型不返回 CoT），
 * 所以在任何文档 / 演示里都不要把它说成“思考过程”。
 */
@Data
@NoArgsConstructor
public class AgentStepVO {

    /** 本轮第几步（从 1 开始） */
    private int step;

    /** 工具名，如 searchResume */
    private String tool;

    /** 入参（已截断） */
    private String args;

    /** 结果摘要（已截断） */
    private String result;

    /** 耗时（毫秒） */
    private long latencyMs;

    /** 是否成功 */
    private boolean success;
}
