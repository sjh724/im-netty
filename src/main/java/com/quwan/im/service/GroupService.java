package com.quwan.im.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.quwan.im.entity.GroupEntity;
import com.quwan.im.entity.GroupMemberEntity;

import java.util.List;

/**
 * 群组服务接口
 * 处理群组创建、管理、成员维护等业务
 */
public interface GroupService extends IService<GroupEntity> {

    /**
     * 创建群组
     * @param groupName 群组名称
     * @param ownerId 群主ID
     * @param description 群组描述
     * @return 群组ID
     */
    String createGroup(String groupName, String ownerId, String description);

    /**
     * 添加群成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @param nickname 群内昵称
     * @return 是否添加成功
     */
    boolean addGroupMember(String groupId, String userId, String nickname);

    /**
     * 移除群成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @param operatorId 操作人ID（必须是群主或管理员）
     * @return 是否移除成功
     */
    boolean removeGroupMember(String groupId, String userId, String operatorId);

    /**
     * 用户退出群组
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否退出成功
     */
    boolean quitGroup(String groupId, String userId);

    /**
     * 获取群成员列表
     * @param groupId 群组ID
     * @return 群成员列表
     */
    List<GroupMemberEntity> getGroupMembers(String groupId);

    /**
     * 获取用户加入的群组
     * @param userId 用户ID
     * @return 用户加入的群组列表
     */
    List<GroupEntity> getUserGroups(String userId);

    /**
     * 检查是否为群成员
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为群成员
     */
    boolean isGroupMember(String groupId, String userId);

    /**
     * 检查是否为群主
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否为群主
     */
    boolean isGroupOwner(String groupId, String userId);
}
