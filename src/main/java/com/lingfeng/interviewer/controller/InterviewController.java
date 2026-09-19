package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.InterviewReportVO;
import com.lingfeng.interviewer.dto.ReplyRequest;
import com.lingfeng.interviewer.dto.ReplyVO;
import com.lingfeng.interviewer.dto.StartInterviewVO;
import com.lingfeng.interviewer.mapper.InterviewSessionMapper;
import com.lingfeng.interviewer.service.InterviewService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interview")
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    /*
    * 开启一场新面试
    * */
    @PostMapping("/start")
    public Result<StartInterviewVO> start(@RequestParam Long resumeId){
        return Result.success(interviewService.start(resumeId));
    }

    /*
    * 候选人回答，ai继续提问
    * */
    @PostMapping("/reply")
    public Result<ReplyVO> reply(@RequestBody ReplyRequest request){
        return Result.success(interviewService.reply(request));
    }

    /*
    完成面试，生成报告
    */
    @PostMapping("/finish")
    public Result<InterviewReportVO> finish(@RequestParam String sessionId){
        return Result.success(interviewService.finish(sessionId));
    }

    //查看本场面试报告
    @GetMapping("/report")
    public Result<InterviewReportVO> report(@RequestParam String sessionId){
        return Result.success(interviewService.report(sessionId));
    }
}
