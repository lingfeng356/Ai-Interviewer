package com.lingfeng.interviewer.common;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：按异常自带的错误码返回。
     *
     * Spring 会挑"最具体"的 handler 来匹配，所以 BusinessException
     * 不会被下面的 RuntimeException 兜底抢走。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 未登录。
     *
     * NotLoginException 本身也是 RuntimeException，如果放到下面兜底处理，
     * 就会变成 code=500 + HTTP 200，前端只能靠关键词猜"是不是登录失效了"。
     * 这里单独接住，返回真正的 HTTP 401，前端看状态码就够了。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, "登录状态已失效，请重新登录"));
    }

    /**
     * 参数校验失败（@Validated / @Valid 没通过）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        return Result.error(BusinessException.BAD_REQUEST, msg);
    }

    /**
     * 兜底：没被上面接住的运行时异常。
     * 走到这里说明是没预料到的错误（空指针、数据库挂了…），统一按 500 返回。
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handle(RuntimeException e) {
        return Result.error(e.getMessage());
    }
}
