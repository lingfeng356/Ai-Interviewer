package com.lingfeng.interviewer.service;

import com.lingfeng.interviewer.dto.*;

import java.util.List;

public interface InterviewService {
    StartInterviewVO start(Long resumeId, String provider,String requestId);

    ReplyVO reply(ReplyRequest request);

    InterviewReportVO finish(String sessionId);

    InterviewReportVO report(String sessionId);

    List<InterviewSessionVO> list();
}
