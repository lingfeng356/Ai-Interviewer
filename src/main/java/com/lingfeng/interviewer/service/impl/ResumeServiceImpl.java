package com.lingfeng.interviewer.service.impl;

import com.lingfeng.interviewer.dto.ResumeUploadVO;
import com.lingfeng.interviewer.entity.Resume;
import com.lingfeng.interviewer.mapper.ResumeMapper;
import com.lingfeng.interviewer.service.ResumeService;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ResumeServiceImpl implements ResumeService {

    //文件存储根目录
    private static final String UPLOAD_DIR = "uploads/";

    @Autowired
    private ResumeMapper resumeMapper;

    //加载简历
    @Override
    public ResumeUploadVO upload(MultipartFile file) {
        //1.校验文件
        if(file == null || file.isEmpty()){
            throw new RuntimeException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if(originalFileName == null || !originalFileName.toLowerCase().endsWith(".pdf")){
            throw new RuntimeException("只支持PDF格式的简历");
        }

        //2.生成存储路径：uploads/2026/09/17/xxx.pdf
        String dataPath = LocalDate.now().toString().replace("-","/");
        String dirPath = UPLOAD_DIR + dataPath + "/";
        File dir = new File(dirPath);
        if(!dir.exists()){
            dir.mkdirs();
        }

        //3.生成唯一文件名，避免重名覆盖
        String newFileName = UUID.randomUUID() + ".pdf";
        String filePath = dirPath + newFileName;

        //4.保存pdf文件到本地磁盘
        try{
            file.transferTo(new File(filePath).getAbsoluteFile());
        }catch (IOException e){
            throw new RuntimeException("文件保存失败:" + e.getMessage());
        }

        String content;

        //用tika同步解析pdf
        try {
            Tika tika = new Tika();
            content = tika.parseToString(new File(filePath).getAbsoluteFile());
        }catch (Exception e){
            throw new RuntimeException("PDF解析失败:" + e.getMessage());
        }

        //5.存mysql
        Resume resume = new Resume();
        resume.setFileName(originalFileName);
        resume.setFilePath(filePath);
        resume.setStatus("PARSED");
        resume.setContent(content);
        resumeMapper.insert(resume);

        //6.组装VO返回
        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setResumeId(resume.getId());
        vo.setFileName(originalFileName);
        vo.setFilePath(filePath);

        return vo;
    }
}
