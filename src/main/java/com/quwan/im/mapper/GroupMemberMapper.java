package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.GroupMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GroupMemberMapper extends BaseMapper<GroupMemberEntity> {
    List<GroupMemberEntity> selectMembersByGroupId(@Param("groupId") String groupId);
    List<GroupMemberEntity> selectGroupsByUserId(@Param("userId") String userId);
   boolean isGroupMember(@Param("groupId")String groupId,@Param("userId")String userId);
}
