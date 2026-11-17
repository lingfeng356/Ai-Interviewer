package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.dto.AgentStepVO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一轮回答内的工具调用记录器，同时承担「本轮工具预算」的守卫。
 *
 * 为什么预算放在这里而不是放在大模型侧：
 * 模型并不知道自己调了几次工具（每步都是独立的推理），
 * 所以必须由工具层自己计数 —— 超了就不再执行，直接返回一句提示，
 * 让模型回到“给最终问题”的轨道上。
 *
 * 两个上限：
 * - maxTools  ：本轮最多执行多少次工具
 * - budgetMs  ：本轮从开始到现在的总时长上限（防止某一步工具特别慢拖垮整轮）
 */
public class AgentTraceRecorder {

    private final int maxTools;
    private final long deadlineAt;

    private final List<AgentStepVO> steps = Collections.synchronizedList(new ArrayList<>());

    public AgentTraceRecorder(int maxTools, long budgetMs) {
        this.maxTools = maxTools;
        this.deadlineAt = System.currentTimeMillis() + budgetMs;
    }

    /** 是否已经用尽预算（次数或时间） */
    public boolean exhausted() {
        return steps.size() >= maxTools || System.currentTimeMillis() > deadlineAt;
    }

    /** 还剩多少次工具额度（用于拼进 system prompt 提示模型） */
    public int remaining() {
        return Math.max(0, maxTools - steps.size());
    }

    public int count() {
        return steps.size();
    }

    public List<AgentStepVO> steps() {
        synchronized (steps) {
            return new ArrayList<>(steps);
        }
    }

    /**
     * 记录一次工具调用。
     * 注意：这里只负责记录，不负责决定是否执行 —— 执行前的预算判断由工具自己调用 exhausted()。
     */
    public void record(String tool, String args, String result, long latencyMs, boolean success) {
        AgentStepVO step = new AgentStepVO();
        step.setStep(steps.size() + 1);
        step.setTool(tool);
        step.setArgs(truncate(args, 120));
        step.setResult(truncate(result, 200));
        step.setLatencyMs(latencyMs);
        step.setSuccess(success);
        steps.add(step);
    }

    /** 统一的截断工具：日志 / 返回给前端的内容都不该无限长 */
    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…";
    }
}
