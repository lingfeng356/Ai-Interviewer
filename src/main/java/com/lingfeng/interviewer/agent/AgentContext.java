package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.common.InterviewStage;

/**
 * 一轮 Agent 执行过程中，工具可以访问的只读上下文。
 *
 * 注意：这个对象是「每次 reply 新建一个」，跟着 InterviewTools 一起传递，
 * 所以不存在多线程共享问题（同一场面试的回答本身就被 Redisson 锁串行化了）。
 */
public class AgentContext {

    private final ReplyContext reply;

    public AgentContext(ReplyContext reply) {
        this.reply = reply;
    }

    public String sessionId() {
        return reply.sessionId();
    }

    public Long resumeId() {
        return reply.resumeId();
    }

    public String resumeText() {
        return reply.resumeText() == null ? "" : reply.resumeText();
    }

    public String resumeProfile() {
        return reply.resumeProfile() == null ? "" : reply.resumeProfile();
    }

    /** 本回合开始时，候选人已经回答了几轮（工具用它标注笔记的轮次） */
    public int answeredRounds() {
        return reply.answeredRounds();
    }

    public InterviewStage stage() {
        return reply.stage();
    }
}
