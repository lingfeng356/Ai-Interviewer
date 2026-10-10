package com.lingfeng.interviewer.service.impl;

import com.lingfeng.interviewer.config.LlmProviderRegistry;
import com.lingfeng.interviewer.service.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@Service
public class RagServiceImpl implements RagService {

    /**
     * 每批入库的块数。
     * 太大：一次要把几百个块送去算 embedding，容易触发接口限流/超时；
     * 太小：每批都要一次网络往返，整体变慢。
     * 50 是"一次请求撑得住"和"总耗时还能接受"之间的折中。
     */
    private static final int BATCH_SIZE = 50;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private LlmProviderRegistry llmProviderRegistry;

    /**
     * 把知识库文件读进来 → 切成小块 → 分批写进向量库。
     *
     * 为什么要分批：vectorStore.add() 内部会为每一个块调用一次 embedding 接口，
     * 一次性丢上千个块进去会把接口打爆（限流、超时、内存飙升），
     * 所以这里按 BATCH_SIZE 切开，一批一批地入库。
     */
    @Override
    public void loadKnowledgeBase(String filePath) {
        // 1. 读文件（TextReader 只吃纯文本，所以知识库文件得是 .txt 这类）
        TextReader reader = new TextReader(new FileSystemResource(filePath));
        List<Document> documents = reader.get();

        // 2. 分块：长文按 token 切成小块，之后检索时粒度更细、命中更准
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.apply(documents);

        if (chunks.isEmpty()) {
            log.warn("知识库文件没有解析出内容, filePath={}", filePath);
            return;
        }

        // 3. 分批入库
        int total = chunks.size();
        log.info("知识库开始入库: 共 {} 个块, 每批 {} 个", total, BATCH_SIZE);

        for (int start = 0; start < total; start += BATCH_SIZE) {
            // 左闭右开：第 start 个到第 end-1 个
            int end = Math.min(start + BATCH_SIZE, total);

            // subList 返回的是原列表的"视图"，不会复制数据，所以不额外占内存
            List<Document> batch = chunks.subList(start, end);

            // 真正写进向量库（这一步会调 embedding 接口）
            vectorStore.add(batch);

            log.info("知识库入库进度: {}/{}", end, total);
        }

        log.info("知识库入库完成: 共 {} 个块", total);
    }

    @Override
    public String ask(String query) {
        log.info("RAG 问答, query={}", query);

        // 根据问题长度动态调整检索参数：问题越长越具体，可以多召回一点
        int topK = query.length() > 20 ? 8 : 3;
        double threshold = query.length() > 20 ? 0.6 : 0.7;

        // 1. 查询改写：把口语化的问题压成关键词，检索命中率更高
        ChatClient chatClient = llmProviderRegistry.getDefault();
        String rewritten = chatClient.prompt()
                .user("请把下面的问题改写成适合检索的关键词，只输出关键词，不要输出其他内容：\n" + query)
                .call()
                .content();

        // 2. 检索：相似度低于阈值的一律不要，避免拿无关内容去瞎编
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(rewritten)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build()
        );
        log.info("RAG 检索到 {} 条", docs.size());

        if (docs.isEmpty()) {
            return "知识库中没有相关信息";
        }

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 3. 把检索到的资料塞进 system prompt，明确要求"只能依据资料回答"
        return chatClient.prompt()
                .system("你是一个知识库助手。请严格根据以下资料回答问题，不要编造。\n\n资料：\n" + context)
                .user(query)
                .call()
                .content();
    }

    @Override
    public String retrieve(String query) {
        log.info("RAG 检索(给面试官当参考), query={}", query);

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .similarityThreshold(0.5)
                        .build()
        );
        log.info("RAG 检索到 {} 条", docs.size());

        if (docs.isEmpty()) {
            return "";
        }

        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));
    }
}
