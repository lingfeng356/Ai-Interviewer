package com.lingfeng.interviewer.common;

public class RedisKeyConstants {

    private RedisKeyConstants(){
    }

    public static final String INTERVIEW_HISTORY = "interview:history:";
    public static final String INTERVIEW_START = "interview:start:";
    public static final String INTERVIEW_REQUEST = "interview:request";

    public static String interviewHistory(String sessionId){
        return INTERVIEW_HISTORY + sessionId;
    }

    public static String interviewStart(String requestId){
        return INTERVIEW_START + requestId;
    }

    public static String interviewRequest(String requestId){
        return INTERVIEW_REQUEST + requestId;
    }
}
