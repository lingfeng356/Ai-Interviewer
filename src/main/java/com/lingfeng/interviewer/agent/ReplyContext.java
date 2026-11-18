package com.lingfeng.interviewer.agent;

import com.lingfeng.interviewer.common.InterviewStage;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 一轮回答的上下文（从 InterviewServiceImpl#prepareReply 产出）。
 *
 * 原来它是 InterviewServiceImpl 里的私有 record，为了让 Agent 分支复用
 * 同一份“校验 + 组装 messages”的逻辑，这里把它提成 public record。
 * 字段和原来保持一致，只多了 Agent 工具需要用到的简历信息。
 *
 * @param sessionId      会话 id
 * @param redisKey       对话历史在 Redis 里的 key
 * @param provider       本回合使用的模型名
 * @param messages       已经拼好的 Spring AI 消息列表（画像 / 简历 / 知识库 / 历史）
 * @param stage          当前面试环节
 * @param stageStartRound 进入当前环节时的轮次（正常情况下等于环节起始轮数，
 *                        模型申请提前换环节后会被改写，用于算“本环节问到第几题”）
 * @param answeredRounds 候选人已回答的轮数（开场白不算）
 * @param resumeId       简历 id
 * @param resumeText     简历全文（供 searchResume 工具检索）
 * @param resumeProfile  简历结构化画像 JSON（可能为空串）
 */
public record ReplyContext(
        String sessionId,
        String redisKey,
        String provider,
        List<Message> messages,
        InterviewStage stage,
        int stageStartRound,
        int answeredRounds,
        Long resumeId,
        String resumeText,
        String resumeProfile) {
}
