package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户Mapper接口
 * 提供用户表的数据库操作方法
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 用户实体
     */
    UserEntity selectByUsername(@Param("username") String username);

    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 状态（ONLINE/OFFLINE）
     * @return 影响行数
     */
    int updateStatus(@Param("userId") String userId, @Param("status") String status);

    /**
     * 检查用户是否存在
     * @param userId 用户ID
     * @return 是否存在（true/false）
     */
    boolean existsById(@Param("userId") String userId);

    /**
     * 根据用户ID查询用户
     * @param userId 用户ID
     * @return 用户实体
     */
    UserEntity selectByUserId(@Param("userId") String userId);
}
