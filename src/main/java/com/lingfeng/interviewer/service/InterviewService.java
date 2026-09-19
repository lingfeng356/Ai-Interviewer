package com.lingfeng.interviewer.service;

import com.lingfeng.interviewer.dto.InterviewReportVO;
import com.lingfeng.interviewer.dto.ReplyRequest;
import com.lingfeng.interviewer.dto.ReplyVO;
import com.lingfeng.interviewer.dto.StartInterviewVO;

public interface InterviewService {
    StartInterviewVO start(Long resumeId);

    ReplyVO reply(ReplyRequest request);

    InterviewReportVO finish(String sessionId);

    InterviewReportVO report(String sessionId);
}
