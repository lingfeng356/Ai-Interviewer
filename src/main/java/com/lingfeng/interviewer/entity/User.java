package com.lingfeng.interviewer.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class User {
    private Long id;
    private String username;
    private String nickname;
    private String password;
    private LocalDateTime createdTime;
}
