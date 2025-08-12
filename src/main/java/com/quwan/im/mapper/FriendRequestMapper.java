package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.FriendRequestEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequestEntity> {
    int insertRequest(@Param("fromUser") String fromUser,
                      @Param("toUser") String toUser,
                      @Param("remark") String remark);

    int updateRequestStatus(@Param("fromUser") String fromUser,
                            @Param("toUser") String toUser,
                            @Param("status") String status);

    List<FriendRequestEntity> selectSentRequests(@Param("userId") String userId);

    List<FriendRequestEntity> selectReceivedRequests(@Param("userId") String userId);

    FriendRequestEntity selectRequest(@Param("fromUser") String fromUser,
                                      @Param("toUser") String toUser);
}
