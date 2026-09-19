package com.lingfeng.interviewer.service;

import com.lingfeng.interviewer.dto.ResumeUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {
    ResumeUploadVO upload(MultipartFile file);
}
