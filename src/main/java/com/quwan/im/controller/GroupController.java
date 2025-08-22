package com.quwan.im.controller;


import com.quwan.im.entity.GroupEntity;
import com.quwan.im.entity.GroupMemberEntity;
import com.quwan.im.model.Result;
import com.quwan.im.model.ResultCode;
import com.quwan.im.service.GroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 群组相关接口（版本：v1）
 * 提供：创建群、加群/邀请成员、移除成员、查询群成员、查询用户所属群 等能力
 * 路由前缀：/im/v1/groups
 */
@RestController
@RequestMapping("/im/v1/groups")
public class GroupController {

    @Autowired
    private GroupService groupService;

    /**
     * 创建群组
     * @param groupName 群名称
     * @param ownerId 群主用户ID
     * @param description 群介绍（可选）
     */
    @PostMapping
    public Result<String> createGroup(@RequestParam String groupName,
                                      @RequestParam String ownerId,
                                      @RequestParam(required = false, defaultValue = "") String description) {
        String groupId = groupService.createGroup(groupName, ownerId, description);
        return Result.success(groupId);
    }

    /**
     * 用户加入群组
     */
    @PostMapping("/join")
    public Result<Boolean> joinGroup(@RequestParam String groupId,
                                     @RequestParam String userId,
                                     @RequestParam(required = false, defaultValue = "") String nickname) {
        boolean ok = groupService.addGroupMember(groupId, userId, nickname);
        return ok ? Result.success(true) : Result.fail(ResultCode.PARAM_ERROR);
    }

    /**
     * 邀请成员进群
     */
    @PostMapping("/members")
    public Result<Boolean> addMember(@RequestParam String groupId,
                                     @RequestParam String userId,
                                     @RequestParam(required = false, defaultValue = "") String nickname) {
        boolean ok = groupService.addGroupMember(groupId, userId, nickname);
        return ok ? Result.success(true) : Result.fail(ResultCode.PARAM_ERROR);
    }

    /**
     * 移除成员（仅群主/管理员）
     */
    @DeleteMapping("/members")
    public Result<Boolean> removeMember(@RequestParam String groupId,
                                        @RequestParam String userId,
                                        @RequestParam String operatorId) {
        boolean ok = groupService.removeGroupMember(groupId, userId, operatorId);
        return ok ? Result.success(true) : Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 查询群成员列表
     */
    @GetMapping("/{groupId}/members")
    public Result<List<GroupMemberEntity>> listMembers(@PathVariable String groupId) {
        return Result.success(groupService.getGroupMembers(groupId));
    }

    /**
     * 查询用户所属群组
     */
    @GetMapping("/user/{userId}")
    public Result<List<GroupEntity>> listUserGroups(@PathVariable String userId) {
        return Result.success(groupService.getUserGroups(userId));
    }
}


