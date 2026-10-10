package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.RateLimit;
import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.*;
import com.lingfeng.interviewer.mapper.InterviewSessionMapper;
import com.lingfeng.interviewer.service.InterviewService;
import com.lingfeng.interviewer.sse.SseManager;
import jakarta.validation.constraints.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.awt.*;
import java.util.List;

@Validated
@RestController
@RequestMapping("/interview")
public class InterviewController {

    @Autowired
    private SseManager sseManager;

    @Autowired
    private InterviewService interviewService;

    /*
    * 开启一场新面试
    * */
    @PostMapping("/start")
    public Result<StartInterviewVO> start(@NotNull(message = "resumeId不能为空") @RequestParam Long resumeId,
                                          @RequestParam(required = false) String provider,
                                          @RequestParam(required = false) String requestId){
        return Result.success(interviewService.start(resumeId,provider,requestId));
    }

    /*
    * 候选人回答，ai继续提问
    * */
    @RateLimit(key = "interview_reply", window = 60, limit = 10)  // 1分钟最多10轮
    @PostMapping("/reply")
    public Result<ReplyVO> reply(@Validated @RequestBody ReplyRequest request,
                                 @RequestParam(required = false) String provider){
        // provider 可选：仅本回合临时指定模型(L2)；不传则用会话当前模型(L1)
        return Result.success(interviewService.reply(request, provider));
    }

    /*
    * 运行时热切换模型（会话级 L1）：切换后后续所有回合都用新模型
    * */
    @PostMapping("/provider")
    public Result<Void> switchProvider(@RequestParam String sessionId,
                                       @RequestParam String provider){
        interviewService.switchProvider(sessionId, provider);
        return Result.success(null);
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

    //Sse：报告生成进度
    // 第二个参数让 SseManager 在连接建立时补发一次 Redis 里缓存的进度（刷新页面不丢进度）
    @GetMapping(value = "/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@RequestParam String sessionId){
        return sseManager.create(sessionId, sessionId);
    }
}
