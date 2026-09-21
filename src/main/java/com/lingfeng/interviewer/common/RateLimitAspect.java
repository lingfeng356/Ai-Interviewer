package com.lingfeng.interviewer.common;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> limitScript;

    public RateLimitAspect() {
        limitScript = new DefaultRedisScript<>();
        limitScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/rate_limit.lua")));
        limitScript.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint,RateLimit rateLimit) throws Throwable{
        //1.拼key
        String methodName = joinPoint.getSignature().toShortString();
        String key = "rate_limit:" + rateLimit.key() + ":" + methodName;

        //2.根据维度拼后缀
        if(rateLimit.type() == LimitType.IP){
            key += ":" + getCLientIp();
        }else{
            key += ":global";
        }

        //3.执行lua脚本
        long now = System.currentTimeMillis();
        Long current = stringRedisTemplate.execute(
                limitScript,
                Collections.singletonList(key),
                String.valueOf(rateLimit.window() * 1000L),  // 秒转毫秒
                String.valueOf(rateLimit.limit()),
                String.valueOf(now)
        );

        //4.返回0表示被限流
        if(current == null || current == 0){
            throw new RuntimeException("请求过于频繁,请稍后再试");
        }

        //5.放行
        return joinPoint.proceed();
    }

    //获取客户端IP
    private String getCLientIp(){
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attrs == null){
            return "unknow";
        }

        HttpServletRequest request = attrs.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if(ip == null || ip.isEmpty()){
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
