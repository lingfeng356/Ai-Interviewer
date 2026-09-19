package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.ResumeUploadVO;
import com.lingfeng.interviewer.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/resume")
public class ResumeController {

    @Autowired
    private ResumeService resumeService;

    @PostMapping("/upload")
    public Result<ResumeUploadVO> upload(@RequestParam("file")MultipartFile file){
        return Result.success(resumeService.upload(file));
    }
}
