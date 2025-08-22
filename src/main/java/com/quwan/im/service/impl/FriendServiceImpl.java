package com.quwan.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.quwan.im.entity.FriendEntity;
import com.quwan.im.entity.FriendRequestEntity;
import com.quwan.im.mapper.FriendMapper;
import com.quwan.im.mapper.FriendRequestMapper;
import com.quwan.im.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 好友服务实现类
 */
@Service
public class FriendServiceImpl extends ServiceImpl<FriendMapper, FriendEntity> implements FriendService {

    @Autowired
    private FriendRequestMapper friendRequestMapper;

    @Autowired
    private FriendMapper friendMapper;

    /**
     * 发送好友请求
     * 检查条件：不能添加自己、不是已添加好友、没有未处理的请求
     */
    @Override
    public boolean addFriendRequest(String fromUser, String toUser, String remark) {
        // 不能添加自己
        if (fromUser.equals(toUser)) {
            return false;
        }

        // 检查是否已是好友
        if (isFriend(fromUser, toUser)) {
            return false;
        }

        // 检查是否已有未处理的请求
        FriendRequestEntity existing = friendRequestMapper.selectRequest(fromUser, toUser);
        if (existing != null) {
            return false;
        }

        // 发送请求
        int rows = friendRequestMapper.insertRequest(fromUser, toUser, remark);
        return rows > 0;
    }

    /**
     * 处理好友请求
     * 若接受请求，需要在好友表中添加双向记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleFriendRequest(String fromUser, String toUser, String status) {
        // 更新请求状态
        int rows = friendRequestMapper.updateRequestStatus(fromUser, toUser, status);
        if (rows <= 0) {
            return false;
        }

        // 如果是接受请求，添加好友关系（双向）
        if ("ACCEPTED".equals(status)) {
            // 添加 fromUser -> toUser 的好友关系
            FriendEntity friend1 = new FriendEntity();
            friend1.setUserId(fromUser);
            friend1.setFriendId(toUser);
            friend1.setRemark("");
            friend1.setStatus("NORMAL");
            friend1.setCreateTime(LocalDateTime.now());
            friend1.setUpdateTime(LocalDateTime.now());
            friendMapper.insert(friend1);

            // 添加 toUser -> fromUser 的好友关系
            FriendEntity friend2 = new FriendEntity();
            friend2.setUserId(toUser);
            friend2.setFriendId(fromUser);
            friend2.setRemark("");
            friend2.setStatus("NORMAL");
            friend2.setCreateTime(LocalDateTime.now());
            friend2.setUpdateTime(LocalDateTime.now());
            friendMapper.insert(friend2);
        }

        return true;
    }

    /**
     * 查询用户发送的好友请求
     */
    @Override
    public List<FriendRequestEntity> getSentRequests(String userId) {
        return friendRequestMapper.selectSentRequests(userId);
    }

    /**
     * 查询用户收到的好友请求
     */
    @Override
    public List<FriendRequestEntity> getReceivedRequests(String userId) {
        return friendRequestMapper.selectReceivedRequests(userId);
    }

    /**
     * 查询用户的好友列表（仅返回好友ID）
     */
    @Override
    public List<String> getUserFriends(String userId) {
        QueryWrapper<FriendEntity> query = new QueryWrapper<>();
        query.eq("user_id", userId)
                .eq("status", "NORMAL");

        List<FriendEntity> friends = friendMapper.selectList(query);
        return friends.stream()
                .map(FriendEntity::getFriendId)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否为好友
     */
    @Override
    public boolean isFriend(String userId, String friendId) {
        return friendMapper.isFriend(userId, friendId);
    }

    @Override
    public boolean removeUser(String userId, String friendId) {
        // 双向删除好友关系：user->friend 与 friend->user
        QueryWrapper<FriendEntity> deleteBothDirections = new QueryWrapper<FriendEntity>()
                .and(wrapper -> wrapper
                        .eq("user_id", userId)
                        .eq("friend_id", friendId))
                .or(wrapper -> wrapper
                        .eq("user_id", friendId)
                        .eq("friend_id", userId));

        return friendMapper.delete(deleteBothDirections) > 0;
    }


}

