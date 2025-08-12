package com.quwan.im.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.quwan.im.entity.FriendEntity;
import com.quwan.im.entity.FriendRequestEntity;

import java.util.List;

/**
 * 好友服务接口
 * 处理好友关系、好友请求相关业务
 */
public interface FriendService extends IService<FriendEntity> {

    /**
     * 发送好友请求
     * @param fromUser 发起者ID
     * @param toUser 接收者ID
     * @param remark 请求备注
     * @return 是否发送成功
     */
    boolean addFriendRequest(String fromUser, String toUser, String remark);

    /**
     * 处理好友请求（接受或拒绝）
     * @param fromUser 发起者ID
     * @param toUser 接收者ID（当前用户）
     * @param status 处理结果：ACCEPTED/REJECTED
     * @return 是否处理成功
     */
    boolean handleFriendRequest(String fromUser, String toUser, String status);

    /**
     * 查询用户发送的好友请求
     * @param userId 用户ID
     * @return 好友请求列表
     */
    List<FriendRequestEntity> getSentRequests(String userId);

    /**
     * 查询用户收到的好友请求
     * @param userId 用户ID
     * @return 好友请求列表
     */
    List<FriendRequestEntity> getReceivedRequests(String userId);

    /**
     * 查询用户的好友列表
     * @param userId 用户ID
     * @return 好友ID列表
     */
    List<String> getUserFriends(String userId);

    /**
     * 检查是否为好友
     * @param userId 用户ID
     * @param friendId 好友ID
     * @return 是否为好友
     */
    boolean isFriend(String userId, String friendId);

    boolean removeUser(String userId, String friendId);


    //    List<FriendEntity> getPendingRequests(String userId);


}
