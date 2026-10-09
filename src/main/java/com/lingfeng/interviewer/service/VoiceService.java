package com.lingfeng.interviewer.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VoiceService {
    String speechToText(MultipartFile audio);
    byte[] textToSpeech(String text);
}
