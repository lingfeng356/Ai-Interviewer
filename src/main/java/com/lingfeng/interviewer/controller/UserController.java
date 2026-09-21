package com.lingfeng.interviewer.controller;

import com.lingfeng.interviewer.common.RateLimit;
import com.lingfeng.interviewer.common.Result;
import com.lingfeng.interviewer.dto.LoginRequest;
import com.lingfeng.interviewer.dto.RegisterRequest;
import com.lingfeng.interviewer.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    /*
    * 注册
    * */
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterRequest request){
        userService.register(request);
        return Result.success(null);
    }

    /*
    * 登录
    * */
    @RateLimit(key = "login", window = 60, limit = 5)   // 1分钟最多5次
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginRequest request){
        return Result.success(userService.login(request));
    }
}
