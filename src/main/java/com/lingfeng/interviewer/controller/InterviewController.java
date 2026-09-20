package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.*;
import com.lingfeng.interviewer.mapper.InterviewSessionMapper;
import com.lingfeng.interviewer.service.InterviewService;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/interview")
public class InterviewController {

    @Autowired
    private InterviewService interviewService;

    /*
    * 开启一场新面试
    * */
    @PostMapping("/start")
    public Result<StartInterviewVO> start(@NotNull(message = "resumeId不能为空") @RequestParam Long resumeId,
                                          @RequestParam(required = false) String provider){
        return Result.success(interviewService.start(resumeId,provider));
    }

    /*
    * 候选人回答，ai继续提问
    * */
    @PostMapping("/reply")
    public Result<ReplyVO> reply(@Validated @RequestBody ReplyRequest request,
                                 @RequestParam(required = false) String provider){
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

    /*
    * 查看面试列表记录
    * */
    @GetMapping("/list")
    public Result<List<InterviewSessionVO>> list(){
        return Result.success(interviewService.list());
    }
}
