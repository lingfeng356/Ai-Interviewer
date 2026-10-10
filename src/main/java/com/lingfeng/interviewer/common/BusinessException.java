package com.lingfeng.interviewer.common;

/**
 * 业务异常。
 *
 * 和直接抛 RuntimeException 的区别：它自带错误码，
 * 全局异常处理器能按码返回（400 参数/业务错误、403 无权访问、429 限流），
 * 前端也就能按错误码做不同处理，而不是所有错误都长一个样。
 */
public class BusinessException extends RuntimeException {

    /** 通用业务错误（参数不对、状态不对…） */
    public static final int BAD_REQUEST = 400;

    /** 无权访问别人的数据 */
    public static final int FORBIDDEN = 403;

    /** 请求太频繁，被限流了 */
    public static final int TOO_MANY_REQUESTS = 429;

    private final int code;

    /** 不传错误码时默认按 400 处理 */
    public BusinessException(String message) {
        this(BAD_REQUEST, message);
    }

    public BusinessException(int code, String message) {
        // message 交给父类，e.getMessage() 就能拿到
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 快捷构造：403 无权访问 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(FORBIDDEN, message);
    }

    /** 快捷构造：429 限流 */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(TOO_MANY_REQUESTS, message);
    }
}
