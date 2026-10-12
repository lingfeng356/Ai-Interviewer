package com.lingfeng.interviewer.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingfeng.interviewer.common.BusinessException;
import com.lingfeng.interviewer.common.StructuredOutputInvoker;
import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.dto.ResumeProfile;
import com.lingfeng.interviewer.dto.ResumeUploadVO;
import com.lingfeng.interviewer.entity.Resume;
import com.lingfeng.interviewer.mapper.ResumeMapper;
import com.lingfeng.interviewer.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
public class ResumeServiceImpl implements ResumeService {

    //文件存储根目录
    private static final String UPLOAD_DIR = "uploads/";

    /**
     * 喂给大模型的简历文本上限。
     * 简历动辄上万字，全丢进去既费 token 也容易超上下文，所以先截断一段。
     * 一般前 6000 字已经能把基本信息、技能栈、主要项目覆盖到了。
     */
    private static final int MAX_RESUME_CHARS = 6000;

    /**
     * 简历抽取的提示词。
     * 重点在"不要编造"和"字段约束"——大模型很容易把没写的东西脑补出来。
     */
    private static final String EXTRACT_PROMPT = """
            你是一个简历信息抽取助手。请从简历文本中提取结构化信息。
            要求：
            1. 只提取简历里明确写了、或能直接推断出来的内容；没有的字段一律留空，绝对不要编造。
            2. skills 只放技术名词（如 Java、Spring Boot、MySQL、Redis），不要放整句话，最多 20 个。
            3. projects 最多取 2 个，每个项目的 description 用一两句话概括候选人具体做了什么。
            4. summary 用一句话概括这位候选人的定位，例如"Java 后端方向的在校生，有两段实习经历"。
            5. 如果这段文本看起来根本不是简历，所有字段都留空。
            """;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    /** 结构化输出调用器：负责让模型吐出能反序列化成 ResumeProfile 的 JSON */
    @Autowired
    private StructuredOutputInvoker structuredOutputInvoker;

    @Autowired
    private ObjectMapper objectMapper;


    //TODO:添加OCR图片识别pdf
    //加载简历
    @Override
    public ResumeUploadVO upload(MultipartFile file) {
        //1.校验文件
        if(file == null || file.isEmpty()){
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if(originalFileName == null || !originalFileName.toLowerCase().endsWith(".pdf")){
            throw new BusinessException("只支持PDF格式的简历");
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
            throw new BusinessException("文件保存失败:" + e.getMessage());
        }

        String content;

        //5.用tika同步解析pdf，拿到纯文本
        try {
            Tika tika = new Tika();
            content = tika.parseToString(new File(filePath).getAbsoluteFile());
        }catch (Exception e){
            throw new BusinessException("PDF解析失败:" + e.getMessage());
        }

        //6.再把纯文本交给大模型，抽成结构化画像（技能栈 / 项目 / 学历…）
        ResumeProfile profile = extractProfile(content);

        //7.存mysql
        Resume resume = new Resume();
        resume.setFileName(originalFileName);
        resume.setFilePath(filePath);
        resume.setStatus("PARSED");
        resume.setContent(content);
        resume.setProfile(toJson(profile));
        resumeMapper.insert(resume);

        //8.组装VO返回
        ResumeUploadVO vo = new ResumeUploadVO();
        vo.setResumeId(resume.getId());
        vo.setFileName(originalFileName);
        vo.setFilePath(filePath);
        vo.setProfile(profile);

        return vo;
    }

    /**
     * 把简历纯文本交给大模型抽取成结构化画像。
     *
     * 关键点：解析失败<b>不能</b>让上传失败。
     * 结构化画像只是"锦上添花"——拿不到就退化成只有纯文本，面试照样能进行。
     * 所以这里把异常全吃掉，只记一条日志。
     */
    private ResumeProfile extractProfile(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }

        // 简历太长先截断，避免 token 爆掉
        String text = content.length() > MAX_RESUME_CHARS
                ? content.substring(0, MAX_RESUME_CHARS)
                : content;

        try {
            // StructuredOutputInvoker 内部已经做了：格式约束 + 3 次重试 + 防提示词注入
            return structuredOutputInvoker.invoke(
                    llmProviderRegistry.getDefault(),
                    EXTRACT_PROMPT,
                    text,
                    ResumeProfile.class
            );
        } catch (Exception e) {
            log.warn("简历结构化解析失败，本次上传降级为纯文本模式, 文本长度={}", text.length(), e);
            return null;
        }
    }

    /** 把画像对象序列化成 JSON 存库；为空就存 null */
    private String toJson(ResumeProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (Exception e) {
            log.warn("简历画像序列化失败", e);
            return null;
        }
    }
}
