package com.quwan.im.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quwan.im.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
    List<MessageEntity> selectUnreadMessages(@Param("userId") String userId);
    int updateStatus(@Param("messageId") String messageId, @Param("status") String status);

    int batchUpdateStatus(@Param("messageId") List<String> messageIds,  @Param("status")String status);
}
