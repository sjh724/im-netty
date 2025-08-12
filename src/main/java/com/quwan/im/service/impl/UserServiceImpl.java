package com.quwan.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.quwan.im.entity.UserEntity;
import com.quwan.im.mapper.UserMapper;
import com.quwan.im.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户服务实现类
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名查询用户
     */
    @Override
    public UserEntity getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /**
     * 更新用户在线状态
     */
    @Override
    public boolean updateUserStatus(String userId, String status) {
        return userMapper.updateStatus(userId, status) > 0;
    }

    /**
     * 检查用户是否存在
     */
    @Override
    public boolean userExists(String userId) {
        return userMapper.existsById(userId);
    }

    /**
     * 用户登录验证
     * 密码采用MD5加密存储
     */
    @Override
    public String login(String username, String password) {
        UserEntity user = getUserByUsername(username);
        if (user == null) {
            return null;
        }

        // 密码加密验证
        String encryptedPassword = DigestUtils.md5DigestAsHex(
                password.getBytes(StandardCharsets.UTF_8));
        if (encryptedPassword.equals(user.getPassword())) {
            // 登录成功，更新状态为在线
            updateUserStatus(user.getUserId(), "ONLINE");
            return user.getUserId();
        }

        return null;
    }

    /**
     * 用户注册
     * 生成唯一用户ID，密码MD5加密存储
     */
    @Override
    public String register(UserEntity user) {
        // 检查用户名是否已存在
        if (getUserByUsername(user.getUsername()) != null) {
            return null;
        }

        // 生成用户ID
        String userId = "user_" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
        user.setUserId(userId);

        // 密码加密
        String encryptedPassword = DigestUtils.md5DigestAsHex(
                user.getPassword().getBytes(StandardCharsets.UTF_8));
        user.setPassword(encryptedPassword);

        // 设置默认值
        user.setStatus("OFFLINE");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        // 保存用户
        if (save(user)) {
            return userId;
        }

        return null;
    }
}
