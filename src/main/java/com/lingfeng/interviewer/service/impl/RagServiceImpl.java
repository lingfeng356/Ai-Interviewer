package com.lingfeng.interviewer.service.impl;

import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class RagServiceImpl implements RagService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    @Override
    public void loadKnowledgeBase(String filePath) {
        //1.读文件
        TextReader reader = new TextReader(new FileSystemResource(filePath));
        List<Document> documents = reader.get();

        //2.分块
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        //3.入库
        vectorStore.add(chunks);
    }

    @Override
    public String ask(String query) {
        System.out.println(">>> RAG 检索, query=" + query);
        // 根据问题长度动态调整
        int topK = query.length() > 20 ? 8 : 3;
        double threshold = query.length() > 20 ? 0.6 : 0.7;

        // 1. 查询改写
        ChatClient chatClient = llmProviderRegistry.getDefault();
        String rewritten = chatClient.prompt()
                .user("请把下面的问题改写成适合检索的关键词，只输出关键词，不要输出其他内容：\n" + query)
                .call()
                .content();

        //2.检索
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(rewritten)
                        .topK(topK)
                        .similarityThreshold(threshold)   // 相似度低于 0.7 不返回
                        .build()
        );
        System.out.println(">>> 检索到 " + docs.size() + " 条");
        if(docs.isEmpty()){
            return "知识库中没有相关信息";
        }
        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 3. 调 AI
        return chatClient.prompt()
                .system("你是一个知识库助手。请严格根据以下资料回答问题，不要编造。\n\n资料：\n" + context)
                .user(query)
                .call()
                .content();
    }

    @Override
    public String retrieve(String query) {
        System.out.println(">>> RAG 检索, query=" + query);
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .similarityThreshold(0.5)
                        .build()
        );
        System.out.println(">>> 检索到 " + docs.size() + " 条");
        if (docs.isEmpty()) {
            return "";
        }
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }
}
