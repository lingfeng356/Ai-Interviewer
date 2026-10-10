package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.ReplyRequest;
import com.lingfeng.interviewer.dto.ReplyVO;
import com.lingfeng.interviewer.service.InterviewService;
import com.lingfeng.interviewer.service.VoiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.Map;

@RestController
@RequestMapping("/interview")
public class VoiceController {

    @Autowired
    private VoiceService voiceService;

    @Autowired
    private InterviewService interviewService;

    //在controller类编排防止循环依赖
    @PostMapping("/voice")
    public Result<Map<String, Object>> voiceInterview(
            @RequestParam String sessionId,
            @RequestParam MultipartFile audio) {

        // 1. ASR
        String userText = voiceService.speechToText(audio);

        // 2. 走面试流程
        ReplyRequest request = new ReplyRequest();
        request.setSessionId(sessionId);
        request.setAnswer(userText);
        ReplyVO reply = interviewService.reply(request);

        // 3. TTS
        byte[] aiAudio = voiceService.textToSpeech(reply.getMessage());

        // 4. 返回（stage / stageName 透传，前端要用它显示"当前在哪个环节"）
        return Result.success(Map.of(
                "userText", userText,
                "aiText", reply.getMessage(),
                "aiAudio", Base64.getEncoder().encodeToString(aiAudio),
                "stage", reply.getStage() == null ? "" : reply.getStage(),
                "stageName", reply.getStageName() == null ? "" : reply.getStageName()
        ));
    }
}