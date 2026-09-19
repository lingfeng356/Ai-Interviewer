package com.lingfeng.interviewer.common;

public class RedisKeyConstants {

    private RedisKeyConstants(){
    }

    public static final String INTERVIEW_HISTORY = "interview:history:";

    public static String interviewHistory(String sessionId){
        return INTERVIEW_HISTORY + sessionId;
    }
}
