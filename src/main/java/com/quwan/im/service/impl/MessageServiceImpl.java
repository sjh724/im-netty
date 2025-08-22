package com.quwan.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.quwan.im.entity.MessageEntity;
import com.quwan.im.mapper.MessageMapper;
import com.quwan.im.model.IMMessage;
import com.quwan.im.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息服务实现类
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, MessageEntity> implements MessageService {

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 保存消息
     */
    @Override
    public void saveMessage(IMMessage message, String status) {
        MessageEntity entity = new MessageEntity();
        entity.setMessageId(message.getId());
        entity.setFromUser(message.getFrom());
        entity.setToUser(message.getTo());
        entity.setContent(message.getContent());
        entity.setType(String.valueOf(message.getType()));
        entity.setGroupId(message.getGroupId());
        entity.setStatus(status);
        entity.setTimestamp(LocalDateTime.now());
        entity.setCreateTime(LocalDateTime.now());

        messageMapper.insert(entity);
    }

    /**
     * 更新消息状态
     */
    @Override
    public boolean updateMessageStatus(String messageId, String status) {
        return messageMapper.updateStatus(messageId, status) > 0;
    }

    /**
     * 批量更新消息状态
     */
    @Override
    public int batchUpdateMessageStatus(List<String> messageIds, String status) {
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        return messageMapper.batchUpdateStatus(messageIds, status);
    }

    /**
     * 获取用户未读消息
     */
    @Override
    public List<MessageEntity> getUnreadMessages(String userId) {
        return messageMapper.selectUnreadMessages(userId);
    }

    /**
     * 获取用户与好友的历史消息
     */
    @Override
    public List<MessageEntity> getHistoryMessages(String userId, String friendId, int page, int size) {
        Page<MessageEntity> pagination = new Page<>(page, size);

        QueryWrapper<MessageEntity> query = new QueryWrapper<>();
        query.and(wrapper -> wrapper
                        .eq("from_user", userId)
                        .eq("to_user", friendId)
                ).or(wrapper -> wrapper
                        .eq("from_user", friendId)
                        .eq("to_user", userId)
                )
                .orderByDesc("timestamp");

        IPage<MessageEntity> result = messageMapper.selectPage(pagination, query);
        return result.getRecords();
    }

    /**
     * 获取群组历史消息
     */
    @Override
    public List<MessageEntity> getGroupHistoryMessages(String groupId, int page, int size) {
        Page<MessageEntity> pagination = new Page<>(page, size);

        QueryWrapper<MessageEntity> query = new QueryWrapper<>();
        query.eq("group_id", groupId)
                .orderByDesc("timestamp");

        IPage<MessageEntity> result = messageMapper.selectPage(pagination, query);
        return result.getRecords();
    }
}
