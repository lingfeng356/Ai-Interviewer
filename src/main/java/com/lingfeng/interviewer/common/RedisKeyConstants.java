package com.lingfeng.interviewer.common;

public class RedisKeyConstants {

    private RedisKeyConstants(){
    }

    public static final String INTERVIEW_HISTORY = "interview:history:";
    public static final String INTERVIEW_START = "interview:start:";
    public static final String INTERVIEW_REQUEST = "interview:request";
    /** 评估进度缓存：SSE 断线/刷新页面后可以恢复进度显示 */
    public static final String INTERVIEW_PROGRESS = "interview:progress:";

    /** 回答问题时的互斥锁：同一场面试不允许并发提问，否则会交错写对话历史 */
    public static final String INTERVIEW_REPLY_LOCK = "interview:reply:lock:";

    /** 本场面试使用的模式（AGENT / CLASSIC），开面试时写入，7 天有效 */
    public static final String INTERVIEW_MODE = "interview:mode:";

    /** 面试官 Agent 的工作记忆（对候选人的观察笔记） */
    public static final String AGENT_MEMO = "agent:memo:";

    /* 模式取值：对外也用这些字符串，避免各处硬编码 */
    public static final String AGENT_MODE_AGENT = "AGENT";
    public static final String AGENT_MODE_CLASSIC = "CLASSIC";
    /** 只在响应里出现：本轮 Agent 失败、已降级为经典模式 */
    public static final String AGENT_MODE_FALLBACK = "FALLBACK";

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

    public static String interviewReplyLock(String sessionId){
        return INTERVIEW_REPLY_LOCK + sessionId;
    }

    public static String interviewMode(String sessionId){
        return INTERVIEW_MODE + sessionId;
    }

    public static String agentMemo(String sessionId){
        return AGENT_MEMO + sessionId;
    }
}
