package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.FriendEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendMapper extends BaseMapper<FriendEntity> {
    List<FriendEntity> selectFriendsByUserId(@Param("userId") String userId);
    List<FriendEntity> selectPendingRequests(@Param("userId") String userId);
    boolean isFriend(@Param("userId") String userId, @Param("friendId") String friendId);
}
