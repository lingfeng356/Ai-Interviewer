package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.common.InterviewStage;

/**
 * 「换环节」的审批规则。
 *
 * 为什么把审批单独抽出来：
 * 1. 它是纯函数，可以直接单测，不需要 Redis / 大模型；
 * 2. 规则和副作用分开 —— 这里只决定批不批，写 Redis 的事交给工具；
 * 3. 以后要改“每个环节最少几题”，只动这一个文件。
 *
 * 设计原则：模型有申请权，系统保留审批权。
 * 不直接让模型改 stage 的原因：它看不见全局轮次，也无法保证面试按节奏走完。
 */
public final class StageAdvancePolicy {

    /** 一个环节至少问了几题，才允许提前进入下一环节 */
    public static final int MIN_QUESTIONS_IN_STAGE = 2;

    private StageAdvancePolicy() {
    }

    /**
     * @param approved 是否批准
     * @param next     批准后的下一环节；拒绝时为 null
     * @param message  回给模型的话（模型会照着它决定接下来怎么做）
     */
    public record Decision(boolean approved, InterviewStage next, String message) {
    }

    /**
     * @param current         当前环节
     * @param questionsInStage 本环节已经问了几题（不含接下来要问的这一题）
     */
    public static Decision decide(InterviewStage current, int questionsInStage) {
        if (current == InterviewStage.REVERSE) {
            return new Decision(false, null,
                    "REJECTED：反问是最后一个环节，没有下一环节。请继续回答候选人的问题，或等待面试结束。");
        }

        int asked = Math.max(0, questionsInStage);
        if (asked < MIN_QUESTIONS_IN_STAGE) {
            return new Decision(false, null,
                    "REJECTED：本环节才问了 " + asked + " 题，至少 " + MIN_QUESTIONS_IN_STAGE
                            + " 题才允许进入下一环节，请再问 " + (MIN_QUESTIONS_IN_STAGE - asked) + " 题。");
        }

        InterviewStage next = nextOf(current);
        return new Decision(true, next,
                "APPROVED：已批准进入【" + next.getLabel() + "】。"
                        + "注意：从下一轮开始生效，本轮请先把当前环节自然收尾。");
    }

    /** 环节顺序是固定的：自我介绍 → 项目 → 技术基础 → 反问 */
    public static InterviewStage nextOf(InterviewStage current) {
        return switch (current) {
            case SELF_INTRO -> InterviewStage.PROJECT;
            case PROJECT -> InterviewStage.TECH;
            case TECH -> InterviewStage.REVERSE;
            case REVERSE -> InterviewStage.REVERSE;
        };
    }
}
