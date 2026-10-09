package com.lingfeng.interviewer.common;

public class RedisKeyConstants {

    private RedisKeyConstants(){
    }

    public static final String INTERVIEW_HISTORY = "interview:history:";
    public static final String INTERVIEW_START = "interview:start:";
    public static final String INTERVIEW_REQUEST = "interview:request";
    /** 评估进度缓存：SSE 断线/刷新页面后可以恢复进度显示 */
    public static final String INTERVIEW_PROGRESS = "interview:progress:";

    public static String interviewHistory(String sessionId){
        return INTERVIEW_HISTORY + sessionId;
    }

    public static String interviewStart(String requestId){
        return INTERVIEW_START + requestId;
    }

    public static String interviewRequest(String requestId){
        return INTERVIEW_REQUEST + requestId;
    }

    public static String interviewProgress(String sessionId){
        return INTERVIEW_PROGRESS + sessionId;
    }
}
