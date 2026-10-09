package com.lingfeng.interviewer.service.impl;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesizer;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisParam;
import com.alibaba.dashscope.audio.tts.SpeechSynthesisResult;
import com.alibaba.dashscope.utils.Constants;
import com.lingfeng.interviewer.service.VoiceService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.ByteBuffer;


//TODO：先看懂
@Service
public class VoiceServiceImpl implements VoiceService {

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        // 直接强制覆盖 DashScope 的全局 Key
        Constants.apiKey = apiKey;
        // 顺便打印出来确认 Spring 确实读到了
        System.out.println(">>> DashScope Global apiKey = " + Constants.apiKey);
    }

    //asr语音转文本
    @Override
    public String speechToText(MultipartFile audio) {

        try {
            // 把 MultipartFile 写到临时文件
            File tempFile = File.createTempFile("asr-", ".wav");
            audio.transferTo(tempFile);

            RecognitionParam param = RecognitionParam.builder()
                    .model("paraformer-v2")
                    .apiKey(apiKey)
                    .build();

            Recognition recognizer = new Recognition();
            String result = recognizer.call(param, tempFile);
            tempFile.delete();
            return result;
        } catch (Exception e) {
            throw new RuntimeException("语音识别失败: " + e.getMessage());
        }
    }

    //tts文本转语音
    @Override
    public byte[] textToSpeech(String text) {
        SpeechSynthesisParam param = SpeechSynthesisParam.builder()
                .model("cosyvoice-v1")
                .text(text)
                .parameter("voice", "longxiaochun")
                .parameter("format", "mp3")
                .build();

        SpeechSynthesizer synthesizer = new SpeechSynthesizer();
        ByteBuffer audio = synthesizer.call(param);   // 直接返回 ByteBuffer
        byte[] bytes = new byte[audio.remaining()];
        audio.get(bytes);
        return bytes;
    }
}