package com.lingfeng.interviewer.service;

import com.lingfeng.interviewer.dto.LoginRequest;
import com.lingfeng.interviewer.dto.RegisterRequest;

public interface UserService {
    void register(RegisterRequest request);

    String login(LoginRequest request);
}
