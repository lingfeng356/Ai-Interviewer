package com.lingfeng.interviewer.common;

/**
 * 面试的 4 个阶段（结构化面试）。
 *
 * 每个阶段规定了「从第几轮开始、到第几轮结束」，
 * 系统根据候选人当前已经回答了几轮，自动算出该进入哪个阶段，
 * 再把对应阶段的指令拼进 system prompt，让 AI 的表现跟着阶段走。
 *
 * 这样就不需要额外调用一次大模型来判断阶段，纯本地计算，简单又可控。
 */
public enum InterviewStage {

    /** 自我介绍：开场白 + 1 次追问，共 2 题 */
    SELF_INTRO("自我介绍", 0, 2,
            "请让候选人介绍教育背景、技术栈和做过的项目。"
                    + "先问整体情况，再问他自己最擅长、最想聊的方向。"),

    /** 项目经历：4 题 */
    PROJECT("项目经历", 2, 6,
            "请围绕候选人简历里写的项目深入追问："
                    + "他具体负责哪一部分、为什么这么设计、遇到过什么难点、最后怎么解决的。"
                    + "如果候选人说得含糊，就继续追问细节。"),

    /** 技术基础：6 题 */
    TECH("技术基础", 6, 12,
            "请结合候选人的技术栈考察基础知识："
                    + "Java 集合与并发、Spring 原理、MySQL 索引与事务、Redis 使用场景等。"
                    + "由浅入深，候选人答对了就继续往深处问。"),

    /** 反问环节：2 题 */
    REVERSE("反问环节", 12, 14,
            "请先告诉候选人面试已经接近尾声，"
                    + "然后问他有没有想了解的、关于岗位或团队的问题。");

    /** 展示给前端看的环节名 */
    private final String label;

    /** 该阶段从第几轮开始（包含） */
    private final int fromRound;

    /** 该阶段到第几轮结束（不包含） */
    private final int toRound;

    /** 拼进 system prompt 的该阶段指令 */
    private final String instruction;

    InterviewStage(String label, int fromRound, int toRound, String instruction) {
        this.label = label;
        this.fromRound = fromRound;
        this.toRound = toRound;
        this.instruction = instruction;
    }

    public String getLabel() {
        return label;
    }

    public String getInstruction() {
        return instruction;
    }

    /**
     * 根据「候选人已经回答了几轮」算出当前所处的阶段。
     *
     * @param answeredRounds 已经回答的轮数；开场白不算，
     *                       候选人第一次回答后为 1，一次都没答过为 0
     */
    public static InterviewStage of(int answeredRounds) {
        for (InterviewStage stage : values()) {
            if (answeredRounds >= stage.fromRound && answeredRounds < stage.toRound) {
                return stage;
            }
        }
        // 聊得比最后一个区间还久，就一直停在反问环节
        return REVERSE;
    }
}
