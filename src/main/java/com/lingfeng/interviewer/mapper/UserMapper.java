package com.lingfeng.interviewer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingfeng.interviewer.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
