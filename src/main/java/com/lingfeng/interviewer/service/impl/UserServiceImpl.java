package com.lingfeng.interviewer.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingfeng.interviewer.dto.LoginRequest;
import com.lingfeng.interviewer.dto.RegisterRequest;
import com.lingfeng.interviewer.entity.User;
import com.lingfeng.interviewer.mapper.UserMapper;
import com.lingfeng.interviewer.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void register(RegisterRequest request) {
        //1.检查用户名是否存在
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if(exist != null){
            throw new RuntimeException("用户名已存在");
        }

        //2.插入新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(encoder.encode(request.getPassword()));  // BCrypt 加密
        user.setNickname(request.getNickname());
        userMapper.insert(user);
    }

    @Override
    public String login(LoginRequest request) {
        //1.查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (user == null || !encoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        //2.sa-token登录，返回token
        StpUtil.login(user.getId());
        return StpUtil.getTokenValue();
    }
}
