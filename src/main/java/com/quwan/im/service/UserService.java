package com.quwan.im.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.quwan.im.entity.UserEntity;

/**
 * 用户服务接口
 * 处理用户注册、登录、信息管理等业务
 */
public interface UserService extends IService<UserEntity> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户实体
     */
    UserEntity getUserByUsername(String username);

    /**
     * 更新用户在线状态
     * @param userId 用户ID
     * @param status 状态：ONLINE/OFFLINE
     * @return 是否更新成功
     */
    boolean updateUserStatus(String userId, String status);

    /**
     * 检查用户是否存在
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean userExists(String userId);

    /**
     * 用户登录验证
     * @param username 用户名
     * @param password 密码
     * @return 验证通过返回用户ID，否则返回null
     */
    String login(String username, String password);

    /**
     * 用户注册
     * @param user 用户信息
     * @return 注册成功返回用户ID，否则返回null
     */
    String register(UserEntity user);
}
