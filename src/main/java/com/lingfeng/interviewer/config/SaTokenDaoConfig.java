package com.lingfeng.interviewer.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * 把 Sa-Token 的登录态从「内存」换成「Redis」。
 *
 * 为什么需要这一步：
 * Sa-Token 默认用 SaTokenDaoDefaultImpl，本质是一个 ConcurrentHashMap。
 * 后果有两个：
 * 1. 后端一重启，所有 token 失效，全员被踢回登录页；
 * 2. 部署多个实例时各自存各自的，请求打到没登录过的那台就 401。
 *
 * 换成 Redis 之后，登录态和对话历史一样带 TTL，重启不丢、多实例共享。
 *
 * 实现选择：
 * 用官方插件 sa-token-redis-template / sa-token-redis-jackson 提供的
 * SaTokenDaoForRedisTemplate，而不是自己拿 StringRedisTemplate 实现 SaTokenDao ——
 * 它要序列化 SaSession 这类复杂对象，序列化方式不对会读出半个 session。
 * 这里手动 init(RedisConnectionFactory)，是因为这个 DAO 是 new 出来的，
 * 不走 Spring 的自动注入。
 */
@Configuration
public class SaTokenDaoConfig {

    @Bean
    public SaTokenDao saTokenDao(RedisConnectionFactory connectionFactory) {
        SaTokenDaoForRedisTemplate dao = new SaTokenDaoForRedisTemplate();
        dao.init(connectionFactory);
        return dao;
    }
}
