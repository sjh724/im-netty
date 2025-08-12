package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.GroupEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMapper extends BaseMapper<GroupEntity> {
    /**
     * 根据群主ID查询群组
     * @param ownerId 群主ID
     * @return 群组列表
     */
    List<GroupEntity> selectByOwnerId(@Param("ownerId") String ownerId);

    /**
     * 查询群组详情（包含群主信息）
     * @param groupId 群组ID
     * @return 群组实体（包含群主信息）
     */
    GroupEntity selectGroupDetail(@Param("groupId") String groupId);

    /**
     * 查询用户加入的群组
     * @param userId 用户ID
     * @return 群组列表
     */
    List<GroupEntity> selectUserGroups(@Param("userId") String userId);

    /**
     * 批量查询群组信息
     * @param groupIds 群组ID列表
     * @return 群组列表
     */
    List<GroupEntity> selectBatchByGroupIds(@Param("groupIds") List<String> groupIds);
}
