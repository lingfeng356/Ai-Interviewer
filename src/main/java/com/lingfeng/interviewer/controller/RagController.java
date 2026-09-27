package com.lingfeng.interviewer.controller;


import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.service.RagService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/rag")
public class RagController {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RagService ragService;

    /*
    * 把知识库文档入库
    * */
    @PostMapping("/load")
    public Result<Void> load(@RequestParam String filePath){
        ragService.loadKnowledgeBase(filePath);
        return Result.success(null);
    }

    /*
    * 检索测试
     */
    @GetMapping("/search")
    public Result<List<String>> search(@RequestParam String query){
        List<Document> docs = vectorStore.similaritySearch(query);
        List<String> results = docs.stream()
                .map(Document::getText)
                .toList();
        return Result.success(results);
    }

    /*
    * 调用ai查询知识库并调用ai回答
    * */
    @GetMapping("/ask")
    public Result<String> ask(@RequestParam String query){
        return Result.success(ragService.ask(query));
    }

    /*
    * 查询知识库
    * */
    @GetMapping("retrieve")
    public Result<String> retrieve(@RequestParam String query){
        return Result.success(ragService.retrieve(query));
    }
}
