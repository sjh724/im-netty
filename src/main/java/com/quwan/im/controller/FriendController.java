package com.quwan.im.controller;


import com.quwan.im.entity.FriendRequestEntity;
import com.quwan.im.model.Result;
import com.quwan.im.model.ResultCode;
import com.quwan.im.service.FriendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 好友相关接口（版本：v1）
 * 提供：好友列表、发送/处理好友请求、删除好友 等能力
 * 路由前缀：/im/v1/friends
 */
@RestController
@RequestMapping("/im/v1/friends")
public class FriendController {

    @Autowired
    private FriendService friendService;

    /**
     * 获取用户的好友ID列表
     * @param userId 用户ID
     * @return 好友ID集合
     */
    @GetMapping("/{userId}")
    public Result<List<String>> listFriends(@PathVariable String userId) {
        return Result.success(friendService.getUserFriends(userId));
    }

    /**
     * 发送好友请求
     * @param fromUser 发起者用户ID
     * @param toUser 接收者用户ID
     * @param remark 备注信息（可选）
     */
    @PostMapping("/request")
    public Result<Boolean> sendRequest(@RequestParam String fromUser,
                                       @RequestParam String toUser,
                                       @RequestParam(required = false, defaultValue = "") String remark) {
        boolean ok = friendService.addFriendRequest(fromUser, toUser, remark);
        if (!ok) {
            return Result.fail(ResultCode.FRIEND_REQUEST_EXISTS);
        }
        return Result.success(true);
    }

    /**
     * 处理好友请求（接受/拒绝）
     * @param fromUser 发起者用户ID
     * @param toUser 接收者用户ID
     * @param status 处理状态：ACCEPTED 或 REJECTED
     */
    @PostMapping("/request/handle")
    public Result<Boolean> handleRequest(@RequestParam String fromUser,
                                         @RequestParam String toUser,
                                         @RequestParam String status) {
        boolean ok = friendService.handleFriendRequest(fromUser, toUser, status);
        return ok ? Result.success(true) : Result.fail(ResultCode.PARAM_ERROR);
    }

    /**
     * 查询用户发出的好友请求
     */
    @GetMapping("/requests/sent/{userId}")
    public Result<List<FriendRequestEntity>> sentRequests(@PathVariable String userId) {
        return Result.success(friendService.getSentRequests(userId));
    }

    /**
     * 查询用户收到的好友请求
     */
    @GetMapping("/requests/received/{userId}")
    public Result<List<FriendRequestEntity>> receivedRequests(@PathVariable String userId) {
        return Result.success(friendService.getReceivedRequests(userId));
    }

    /**
     * 删除好友（双向删除关系）
     */
    @DeleteMapping
    public Result<Boolean> deleteFriend(@RequestParam String userId, @RequestParam String friendId) {
        boolean ok = friendService.removeUser(userId, friendId);
        return ok ? Result.success(true) : Result.fail(ResultCode.PARAM_ERROR);
    }
}


