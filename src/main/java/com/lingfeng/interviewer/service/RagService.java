package com.lingfeng.interviewer.service;

public interface RagService {
    void loadKnowledgeBase(String filePath);

    String ask(String query);

    String retrieve(String query);
}
