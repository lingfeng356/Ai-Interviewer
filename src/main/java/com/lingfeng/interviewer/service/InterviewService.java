package com.lingfeng.interviewer.service;

import com.lingfeng.interviewer.dto.*;

import java.util.List;

public interface InterviewService {
    StartInterviewVO start(Long resumeId, String provider,String requestId);

    /**
     * 回答 + AI 追问
     * @param overrideProvider L2 请求级覆盖：不为空时本回合临时用这个模型；
     *                         为空则用会话当前配置的模型（L1）
     */
    ReplyVO reply(ReplyRequest request, String overrideProvider);

    /**
     * 兼容旧调用（语音面试等），等价于不覆盖模型
     */
    default ReplyVO reply(ReplyRequest request) {
        return reply(request, null);
    }

    /**
     * L1 会话级热切换模型：更新会话的 provider，后续所有回合生效
     */
    void switchProvider(String sessionId, String provider);

    InterviewReportVO finish(String sessionId);

    InterviewReportVO report(String sessionId);

    List<InterviewSessionVO> list();
}
