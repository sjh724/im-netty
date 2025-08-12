package com.quwan.im.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.quwan.im.entity.MessageEntity;
import com.quwan.im.model.IMMessage;

import java.util.List;

/**
 * 消息服务接口
 * 处理消息发送、存储、状态管理等业务
 */
public interface MessageService extends IService<MessageEntity> {

    /**
     * 保存消息
     * @param message 消息对象
     * @param status 消息状态：SENT/DELIVERED/READ
     */
    void saveMessage(IMMessage message, String status);

    /**
     * 更新消息状态
     * @param messageId 消息ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateMessageStatus(String messageId, String status);

    /**
     * 批量更新消息状态
     * @param messageIds 消息ID列表
     * @param status 新状态
     * @return 更新成功的数量
     */
    int batchUpdateMessageStatus(List<String> messageIds, String status);

    /**
     * 获取用户未读消息
     * @param userId 用户ID
     * @return 未读消息列表
     */
    List<MessageEntity> getUnreadMessages(String userId);

    /**
     * 获取用户与好友的历史消息
     * @param userId 用户ID
     * @param friendId 好友ID
     * @param page 页码
     * @param size 每页数量
     * @return 历史消息列表
     */
    List<MessageEntity> getHistoryMessages(String userId, String friendId, int page, int size);

    /**
     * 获取群组历史消息
     * @param groupId 群组ID
     * @param page 页码
     * @param size 每页数量
     * @return 历史消息列表
     */
    List<MessageEntity> getGroupHistoryMessages(String groupId, int page, int size);
}
