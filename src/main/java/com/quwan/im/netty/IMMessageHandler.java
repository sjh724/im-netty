package com.quwan.im.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quwan.im.entity.GroupEntity;
import com.quwan.im.entity.GroupMemberEntity;
import com.quwan.im.entity.MessageEntity;
import com.quwan.im.entity.UserEntity;
import com.quwan.im.model.IMMessage;
import com.quwan.im.model.MessageType;
import com.quwan.im.model.ProtocolMessage;
import com.quwan.im.service.FriendService;
import com.quwan.im.service.GroupService;
import com.quwan.im.service.MessageService;
import com.quwan.im.service.UserService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 与MessageType枚举严格对应的消息处理器
 * 每个消息类型都有专门的处理方法，命名和功能完全匹配
 */
@Component
@ChannelHandler.Sharable
public class IMMessageHandler extends SimpleChannelInboundHandler<ProtocolMessage> {

    private static final Logger       logger       = LoggerFactory.getLogger(IMMessageHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 用户ID与Channel的映射关系
    private static final Map<String, Channel> userChannelMap = new ConcurrentHashMap<>();

    // 存储用户ID的属性键
    public static final AttributeKey<String> USER_ID_ATTRIBUTE = AttributeKey.newInstance("userId");

    @Autowired
    private UserService userService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private FriendService friendService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private Executor messageTaskExecutor;

    @Autowired
    private Executor dbTaskExecutor;

    /**
     * 核心消息分发方法，与MessageType枚举一一对应
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProtocolMessage protocolMessage) throws Exception {

        try {
            String userId = getUserIdFromChannel(ctx.channel());
            String data = protocolMessage.getData();

            // 根据消息编码获取对应的MessageType
            MessageType messageType = MessageType.fromCode(protocolMessage.getType());
            logger.info("用户[{}]接收消息 - 类型: {}({}), 内容: {}", userId, messageType.name(), messageType.getCode(), data);

            // 严格按照MessageType枚举进行分发
            switch (messageType) {
                case LOGIN:
                    logger.info("login request,data:{}", data);
                    handleLogin(ctx, data);
                    break;
                case LOGOUT:
                    handleLogout(ctx, userId);
                    break;
                case PING:
                    handlePing(ctx);
                    break;

                // 单聊消息处理
                case SINGLE_CHAT:
                    handleSingleChat(userId, data);
                    break;
                case SINGLE_CHAT_ACK:
                    handleSingleChatAck(data);
                    break;
                case SINGLE_CHAT_READ:
                    handleSingleChatRead(data);
                    break;
                case SINGLE_CHAT_RECALL:
                    handleSingleChatRecall(userId, data);
                    break;

                // 群聊消息处理
                case GROUP_CHAT:
                    handleGroupChat(userId, data);
                    break;
                case GROUP_CHAT_ACK:
                    handleGroupChatAck(data);
                    break;
                case GROUP_CHAT_READ:
                    handleGroupChatRead(data);
                    break;
                case GROUP_CHAT_RECALL:
                    handleGroupChatRecall(userId, data);
                    break;

                // 好友关系处理
                case FRIEND_REQUEST_SEND:
                    handleFriendRequestSend(userId, data);
                    break;
                case FRIEND_REQUEST_RESPONSE:
                    handleFriendRequestResponse(userId, data);
                    break;
                case FRIEND_LIST_QUERY:
                    handleFriendListQuery(userId, ctx);
                    break;
                case FRIEND_DELETE:
                    handleFriendDelete(userId, data);
                    break;

                // 群组管理处理
                case GROUP_CREATE:
                    handleGroupCreate(userId, data, ctx);
                    break;
                case GROUP_JOIN:
                    handleGroupJoin(userId, data, ctx);
                    break;
                case GROUP_QUIT:
                    handleGroupQuit(userId, data, ctx);
                    break;
                case GROUP_MEMBER_QUERY:
                    handleGroupMemberQuery(data, ctx);
                    break;
                case GROUP_LIST_QUERY:
                    handleGroupListQuery(userId, ctx);
                    break;

                default:
                    logger.warn("未实现的消息类型: {}", messageType);
                    sendErrorResponse(ctx, "未实现的消息类型: " + messageType.name());
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("message channel read0 error;{}", e.getMessage());
        }
    }

    // ------------------------------ 系统基础消息处理 ------------------------------

    /**
     * 处理登录请求 (对应MessageType.LOGIN)
     */
    private void handleLogin(ChannelHandlerContext ctx, String data) throws Exception {

        Map<String, String> loginData = objectMapper.readValue(data, Map.class);
        String username = loginData.get("username");
        String password = loginData.get("password");

        String userId = userService.login(username, password);
        if (userId == null) {
            sendResponse(ctx, MessageType.LOGIN_RESPONSE, "fail", "用户名或密码错误");
            return;
        }

        // 处理重复登录
        if (userChannelMap.containsKey(userId)) {
            Channel oldChannel = userChannelMap.get(userId);
            sendSystemNotify(oldChannel, "您的账号在其他设备登录");
            oldChannel.close();
        }

        // 绑定用户与Channel
        ctx.channel().attr(USER_ID_ATTRIBUTE).set(userId);
        userChannelMap.put(userId, ctx.channel());

        // 缓存用户在线状态
        redisTemplate.opsForValue().set("user:online:" + userId, "1", java.time.Duration.ofMinutes(30));

        logger.info("用户[{}]登录成功", userId);
        sendResponse(ctx, MessageType.LOGIN_RESPONSE, "success", userId);

        // 异步推送未读消息
        messageTaskExecutor.execute(() -> {
            try {
                pushUnreadMessages(userId);
            } catch (Exception e) {
                logger.error("推送未读消息失败", e);
            }
        });
    }

    /**
     * 处理登出请求 (对应MessageType.LOGOUT)
     */
    private void handleLogout(ChannelHandlerContext ctx, String userId) throws Exception {

        if (userId != null) {
            userChannelMap.remove(userId);
            // 异步更新用户状态
            dbTaskExecutor.execute(() -> {
                try {
                    userService.updateUserStatus(userId, "OFFLINE");
                } catch (Exception e) {
                    logger.error("更新用户状态失败", e);
                }
            });
            
            // 清除缓存
            redisTemplate.delete("user:online:" + userId);
            
            logger.info("用户[{}]主动登出", userId);
            sendResponse(ctx, MessageType.LOGOUT_RESPONSE, "success", "已成功登出");
        }
        ctx.channel().close();
    }

    /**
     * 处理心跳请求 (对应MessageType.PING)
     */
    private void handlePing(ChannelHandlerContext ctx) throws Exception {

        logger.info("handle ping msg, send pong");
        sendResponse(ctx, MessageType.PONG, "success", "pong");
    }


    // ------------------------------ 单聊消息处理 ------------------------------

    /**
     * 处理单聊消息 (对应MessageType.SINGLE_CHAT)
     */
    private void handleSingleChat(String senderId, String data) throws Exception {

        IMMessage message = objectMapper.readValue(data, IMMessage.class);
        String receiverId = message.getTo();

        // 验证接收方
        if (!userService.userExists(receiverId)) {
            sendErrorToUser(senderId, "接收用户不存在");
            return;
        }

        // 验证好友关系
        if (!friendService.isFriend(senderId, receiverId)) {
            sendErrorToUser(senderId, "请先添加对方为好友");
            return;
        }

        // 完善消息信息
        message.setId(UUID.randomUUID().toString());
        message.setFrom(senderId);
        message.setType(MessageType.SINGLE_CHAT);

        // 异步保存消息
        dbTaskExecutor.execute(() -> {
            try {
                messageService.saveMessage(message, "SENT");
            } catch (Exception e) {
                logger.error("保存消息失败", e);
            }
        });

        // 转发给接收方
        Channel receiverChannel = userChannelMap.get(receiverId);
        if (receiverChannel != null && receiverChannel.isActive()) {
            ProtocolMessage protocolMessage = new ProtocolMessage(MessageType.SINGLE_CHAT.getCode(), objectMapper.writeValueAsString(message));
            receiverChannel.writeAndFlush(protocolMessage);
            
            // 异步更新消息状态
            dbTaskExecutor.execute(() -> {
                try {
                    messageService.updateMessageStatus(message.getId(), "DELIVERED");
                } catch (Exception e) {
                    logger.error("更新消息状态失败", e);
                }
            });
        }

        // 响应发送方
        sendResponseToUser(senderId, MessageType.SINGLE_CHAT_ACK, "success", message.getId());
    }

    /**
     * 处理单聊消息确认 (对应MessageType.SINGLE_CHAT_ACK)
     */
    private void handleSingleChatAck(String data) throws Exception {

        Map<String, String> ackData = objectMapper.readValue(data, Map.class);
        String messageId = ackData.get("messageId");
        
        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "DELIVERED");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });
    }

    /**
     * 处理单聊消息已读回执 (对应MessageType.SINGLE_CHAT_READ)
     */
    private void handleSingleChatRead(String data) throws Exception {

        Map<String, String> readData = objectMapper.readValue(data, Map.class);
        String messageId = readData.get("messageId");
        
        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "READ");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });

        // 通知发送方消息已读
        String senderId = readData.get("senderId");
        sendResponseToUser(senderId, MessageType.SINGLE_CHAT_READ, "success", messageId);
    }

    /**
     * 处理单聊消息撤回 (对应MessageType.SINGLE_CHAT_RECALL)
     */
    private void handleSingleChatRecall(String operatorId, String data) throws Exception {

        Map<String, String> recallData = objectMapper.readValue(data, Map.class);
        String messageId = recallData.get("messageId");
        String receiverId = recallData.get("receiverId");

        // 验证消息所有权
        MessageEntity message = messageService.getById(messageId);
        if (message == null || !message.getFromUser().equals(operatorId)) {
            sendErrorToUser(operatorId, "无权撤回该消息");
            return;
        }

        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "RECALLED");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });

        // 通知接收方消息已撤回
        IMMessage recallNotify = new IMMessage();
        recallNotify.setId(UUID.randomUUID().toString());
        recallNotify.setType(MessageType.SINGLE_CHAT_RECALL);
        recallNotify.setFrom(operatorId);
        recallNotify.setTo(receiverId);
        recallNotify.setContent(messageId);

        sendToUser(receiverId, MessageType.SINGLE_CHAT_RECALL, objectMapper.writeValueAsString(recallNotify));
        sendResponseToUser(operatorId, MessageType.SYSTEM_NOTIFY, "success", "消息已撤回");
    }


    // ------------------------------ 群聊消息处理 ------------------------------

    /**
     * 处理群聊消息 (对应MessageType.GROUP_CHAT)
     */
    private void handleGroupChat(String senderId, String data) throws Exception {

        IMMessage message = objectMapper.readValue(data, IMMessage.class);
        String groupId = message.getGroupId();

        // 验证群成员身份
        if (!groupService.isGroupMember(groupId, senderId)) {
            sendErrorToUser(senderId, "您不是该群成员");
            return;
        }

        // 完善消息信息
        message.setId(UUID.randomUUID().toString());
        message.setFrom(senderId);
        message.setType(MessageType.GROUP_CHAT);

        // 异步保存消息
        dbTaskExecutor.execute(() -> {
            try {
                messageService.saveMessage(message, "SENT");
            } catch (Exception e) {
                logger.error("保存消息失败", e);
            }
        });

        // 异步转发给群成员
        messageTaskExecutor.execute(() -> {
            try {
                List<GroupMemberEntity> members = groupService.getGroupMembers(groupId);
                for (GroupMemberEntity member : members) {
                    String memberId = member.getUserId();
                    if (!memberId.equals(senderId)) { // 跳过发送者
                        Channel memberChannel = userChannelMap.get(memberId);
                        if (memberChannel != null && memberChannel.isActive()) {
                            ProtocolMessage protocolMessage = new ProtocolMessage(MessageType.GROUP_CHAT.getCode(), objectMapper.writeValueAsString(message));
                            memberChannel.writeAndFlush(protocolMessage);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("转发群聊消息失败", e);
            }
        });

        // 响应发送方
        sendResponseToUser(senderId, MessageType.GROUP_CHAT_ACK, "success", message.getId());
    }

    /**
     * 处理群聊消息确认 (对应MessageType.GROUP_CHAT_ACK)
     */
    private void handleGroupChatAck(String data) throws Exception {

        Map<String, String> ackData = objectMapper.readValue(data, Map.class);
        String messageId = ackData.get("messageId");
        
        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "DELIVERED");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });
    }

    /**
     * 处理群聊消息已读回执 (对应MessageType.GROUP_CHAT_READ)
     */
    private void handleGroupChatRead(String data) throws Exception {

        Map<String, String> readData = objectMapper.readValue(data, Map.class);
        String messageId = readData.get("messageId");
        
        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "READ");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });
    }

    /**
     * 处理群聊消息撤回 (对应MessageType.GROUP_CHAT_RECALL)
     */
    private void handleGroupChatRecall(String operatorId, String data) throws Exception {

        Map<String, String> recallData = objectMapper.readValue(data, Map.class);
        String messageId = recallData.get("messageId");
        String groupId = recallData.get("groupId");

        // 验证权限（群主、管理员或消息发送者）
        MessageEntity message = messageService.getById(messageId);
        if (message == null || !message.getGroupId().equals(groupId)) {
            sendErrorToUser(operatorId, "消息不存在");
            return;
        }

        boolean canRecall = message.getFromUser().equals(operatorId) || groupService.isGroupOwner(groupId, operatorId);
        if (!canRecall) {
            sendErrorToUser(operatorId, "无权撤回该消息");
            return;
        }

        // 异步更新消息状态
        dbTaskExecutor.execute(() -> {
            try {
                messageService.updateMessageStatus(messageId, "RECALLED");
            } catch (Exception e) {
                logger.error("更新消息状态失败", e);
            }
        });

        // 异步通知群成员消息已撤回
        messageTaskExecutor.execute(() -> {
            try {
                IMMessage recallNotify = new IMMessage();
                recallNotify.setId(UUID.randomUUID().toString());
                recallNotify.setType(MessageType.GROUP_CHAT_RECALL);
                recallNotify.setFrom(operatorId);
                recallNotify.setGroupId(groupId);
                recallNotify.setContent(messageId);

                String notifyData = objectMapper.writeValueAsString(recallNotify);
                List<GroupMemberEntity> members = groupService.getGroupMembers(groupId);

                for (GroupMemberEntity member : members) {
                    Channel channel = userChannelMap.get(member.getUserId());
                    if (channel != null && channel.isActive()) {
                        channel.writeAndFlush(new ProtocolMessage(MessageType.GROUP_CHAT_RECALL.getCode(), notifyData));
                    }
                }
            } catch (Exception e) {
                logger.error("通知群成员消息撤回失败", e);
            }
        });

        sendResponseToUser(operatorId, MessageType.SYSTEM_NOTIFY, "success", "消息已撤回");
    }


    // ------------------------------ 好友关系处理 ------------------------------

    /**
     * 处理发送好友请求 (对应MessageType.FRIEND_REQUEST_SEND)
     */
    private void handleFriendRequestSend(String senderId, String data) throws Exception {

        Map<String, String> requestData = objectMapper.readValue(data, Map.class);
        String targetUserId = requestData.get("targetUserId");
        String remark = requestData.getOrDefault("remark", "");

        // 发送请求
        boolean success = friendService.addFriendRequest(senderId, targetUserId, remark);
        if (success) {
            // 通知目标用户
            UserEntity sender = userService.getById(senderId);
            IMMessage notifyMsg = new IMMessage();
            notifyMsg.setId(UUID.randomUUID().toString());
            notifyMsg.setType(MessageType.FRIEND_REQUEST_RECV);
            notifyMsg.setFrom(senderId);
            notifyMsg.setTo(targetUserId);
            notifyMsg.setContent(sender.getUsername() + "请求添加您为好友：" + remark);

            sendToUser(targetUserId, MessageType.FRIEND_REQUEST_RECV, objectMapper.writeValueAsString(notifyMsg));

            sendResponseToUser(senderId, MessageType.SYSTEM_NOTIFY, "success", "好友请求已发送");
        } else {
            sendErrorToUser(senderId, "发送失败，可能已发送过请求或已是好友");
        }
    }

    /**
     * 处理好友请求响应 (对应MessageType.FRIEND_REQUEST_RESPONSE)
     */
    private void handleFriendRequestResponse(String userId, String data) throws Exception {

        Map<String, Object> responseData = objectMapper.readValue(data, Map.class);
        String requesterId = (String) responseData.get("requesterId");
        boolean accepted = (boolean) responseData.get("accepted");

        // 处理请求
        String status = accepted ? "ACCEPTED" : "REJECTED";
        boolean success = friendService.handleFriendRequest(requesterId, userId, status);

        if (success) {
            // 通知请求发起者
            String resultMsg = accepted ? userId + "已接受您的好友请求" : userId + "已拒绝您的好友请求";

            sendToUser(requesterId, MessageType.FRIEND_REQUEST_RESULT, resultMsg);
            sendResponseToUser(userId, MessageType.SYSTEM_NOTIFY, "success", accepted ? "已接受好友请求" : "已拒绝好友请求");
        } else {
            sendErrorToUser(userId, "处理好友请求失败");
        }
    }

    /**
     * 处理好友列表查询 (对应MessageType.FRIEND_LIST_QUERY)
     */
    private void handleFriendListQuery(String userId, ChannelHandlerContext ctx) throws Exception {

        List<String> friendIds = friendService.getUserFriends(userId);
        // 获取好友详细信息
        List<UserEntity> friends = friendIds.stream().map(id -> userService.getById(id)).collect(Collectors.toList());

        sendResponse(ctx, MessageType.FRIEND_LIST_RESPONSE, "success", objectMapper.writeValueAsString(friends));
    }

    /**
     * 处理删除好友 (对应MessageType.FRIEND_DELETE)
     */
    private void handleFriendDelete(String userId, String data) throws Exception {

        Map<String, String> deleteData = objectMapper.readValue(data, Map.class);
        String friendId = deleteData.get("friendId");

        // 执行删除（双向删除）
        boolean success = friendService.removeUser(userId, friendId);

        if (success) {
            friendService.removeUser(friendId, userId);

            // 通知对方
            sendToUser(friendId, MessageType.SYSTEM_NOTIFY, userId + "已将您从好友列表中删除");
            sendResponseToUser(userId, MessageType.FRIEND_DELETE_RESPONSE, "success", "已删除好友");
        } else {
            sendErrorToUser(userId, "删除好友失败");
        }
    }


    // ------------------------------ 群组管理处理 ------------------------------

    /**
     * 处理创建群组 (对应MessageType.GROUP_CREATE)
     */
    private void handleGroupCreate(String userId, String data, ChannelHandlerContext ctx) throws Exception {

        Map<String, String> groupData = objectMapper.readValue(data, Map.class);
        String groupName = groupData.get("groupName");
        String description = groupData.getOrDefault("description", "");

        String groupId = groupService.createGroup(groupName, userId, description);
        if (groupId != null) {
            sendResponse(ctx, MessageType.GROUP_CREATE_RESPONSE, "success", groupId);
        } else {
            sendErrorResponse(ctx, "创建群组失败");
        }
    }

    /**
     * 处理加入群组 (对应MessageType.GROUP_JOIN)
     */
    private void handleGroupJoin(String userId, String data, ChannelHandlerContext ctx) throws Exception {

        Map<String, String> joinData = objectMapper.readValue(data, Map.class);
        String groupId = joinData.get("groupId");
        String nickname = joinData.getOrDefault("nickname", userService.getById(userId).getUsername());

        boolean success = groupService.addGroupMember(groupId, userId, nickname);
        if (success) {
            sendResponse(ctx, MessageType.GROUP_JOIN_RESPONSE, "success", "加入群组成功");
            sendGroupSystemNotify(groupId, userId + "已加入群组");
        } else {
            sendErrorResponse(ctx, "加入群组失败");
        }
    }

    /**
     * 处理退出群组 (对应MessageType.GROUP_QUIT)
     */
    private void handleGroupQuit(String userId, String data, ChannelHandlerContext ctx) throws Exception {

        Map<String, String> quitData = objectMapper.readValue(data, Map.class);
        String groupId = quitData.get("groupId");

        boolean success = groupService.quitGroup(groupId, userId);
        if (success) {
            sendResponse(ctx, MessageType.GROUP_QUIT_RESPONSE, "success", "已退出群组");
            sendGroupSystemNotify(groupId, userId + "已退出群组");
        } else {
            sendErrorResponse(ctx, "退出群组失败");
        }
    }

    /**
     * 处理群成员查询 (对应MessageType.GROUP_MEMBER_QUERY)
     */
    private void handleGroupMemberQuery(String data, ChannelHandlerContext ctx) throws Exception {

        Map<String, String> queryData = objectMapper.readValue(data, Map.class);
        String groupId = queryData.get("groupId");

        List<GroupMemberEntity> members = groupService.getGroupMembers(groupId);
        sendResponse(ctx, MessageType.GROUP_MEMBER_RESPONSE, "success", objectMapper.writeValueAsString(members));
    }

    /**
     * 处理群组列表查询 (对应MessageType.GROUP_LIST_QUERY)
     */
    private void handleGroupListQuery(String userId, ChannelHandlerContext ctx) throws Exception {

        List<GroupEntity> groups = groupService.getUserGroups(userId);
        sendResponse(ctx, MessageType.GROUP_LIST_RESPONSE, "success", objectMapper.writeValueAsString(groups));
    }


    // ------------------------------ 连接管理与辅助方法 ------------------------------
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {

        String userId = getUserIdFromChannel(ctx.channel());
        if (userId != null) {
            userChannelMap.remove(userId);
            // 异步更新用户状态
            dbTaskExecutor.execute(() -> {
                try {
                    userService.updateUserStatus(userId, "OFFLINE");
                } catch (Exception e) {
                    logger.error("更新用户状态失败", e);
                }
            });
            
            // 清除缓存
            redisTemplate.delete("user:online:" + userId);
            
            logger.info("用户[{}]连接断开", userId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {

        String userId = getUserIdFromChannel(ctx.channel());
        logger.error("用户[{}]消息处理异常", userId, cause);
        try {
            sendErrorResponse(ctx, "服务器处理消息异常: " + cause.getMessage());
        } catch (Exception e) {
            logger.error("发送错误响应失败", e);
        }
        ctx.close();
    }

    /**
     * 推送未读消息给用户
     */
    private void pushUnreadMessages(String userId) throws Exception {

        List<MessageEntity> unreadMessages = messageService.getUnreadMessages(userId);
        if (!unreadMessages.isEmpty()) {
            Channel channel = userChannelMap.get(userId);
            if (channel != null && channel.isActive()) {
                for (MessageEntity msg : unreadMessages) {
                    IMMessage imMsg = convertToIMMessage(msg);
                    channel.writeAndFlush(new ProtocolMessage(imMsg.getType().getCode(), objectMapper.writeValueAsString(imMsg)));
                }
                // 批量更新为已读
                List<String> msgIds = unreadMessages.stream().map(MessageEntity::getMessageId).collect(Collectors.toList());
                messageService.batchUpdateMessageStatus(msgIds, "READ");
            }
        }
    }

    /**
     * 转换实体类为消息对象
     */
    private IMMessage convertToIMMessage(MessageEntity entity) {

        IMMessage message = new IMMessage();
        message.setId(entity.getMessageId());
        message.setFrom(entity.getFromUser());
        message.setTo(entity.getToUser());
        message.setGroupId(entity.getGroupId());
        message.setContent(entity.getContent());
        message.setType(MessageType.fromCode(Byte.parseByte(entity.getType())));
        return message;
    }

    /**
     * 发送响应消息
     */
    private void sendResponse(ChannelHandlerContext ctx, MessageType type, String status, String content) throws Exception {

        IMMessage response = new IMMessage();
        response.setId(UUID.randomUUID().toString());
        response.setType(type);
        response.setFrom("system");
        response.setExtra(status);
        response.setContent(content);
        logger.info("send response:{}", response);
        ctx.writeAndFlush(new ProtocolMessage(type.getCode(), objectMapper.writeValueAsString(response)));
    }

    /**
     * 向指定用户发送响应
     */
    private void sendResponseToUser(String userId, MessageType type, String status, String content) throws Exception {

        Channel channel = userChannelMap.get(userId);
        if (channel != null && channel.isActive()) {
            IMMessage response = new IMMessage();
            response.setId(UUID.randomUUID().toString());
            response.setType(type);
            response.setFrom("system");
            response.setExtra(status);
            response.setContent(content);

            channel.writeAndFlush(new ProtocolMessage(type.getCode(), objectMapper.writeValueAsString(response)));
        }
    }

    /**
     * 向指定用户发送消息
     */
    private void sendToUser(String userId, MessageType type, String content) throws Exception {

        Channel channel = userChannelMap.get(userId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new ProtocolMessage(type.getCode(), content));
        }
    }

    /**
     * 发送系统通知
     */
    private void sendSystemNotify(Channel channel, String content) throws Exception {

        IMMessage notify = new IMMessage();
        notify.setId(UUID.randomUUID().toString());
        notify.setType(MessageType.SYSTEM_NOTIFY);
        notify.setFrom("system");
        notify.setContent(content);

        channel.writeAndFlush(new ProtocolMessage(MessageType.SYSTEM_NOTIFY.getCode(), objectMapper.writeValueAsString(notify)));
    }

    /**
     * 向群组发送系统通知
     */
    private void sendGroupSystemNotify(String groupId, String content) throws Exception {

        IMMessage notify = new IMMessage();
        notify.setId(UUID.randomUUID().toString());
        notify.setType(MessageType.SYSTEM_NOTIFY);
        notify.setFrom("system");
        notify.setGroupId(groupId);
        notify.setContent(content);

        String notifyData = objectMapper.writeValueAsString(notify);
        List<GroupMemberEntity> members = groupService.getGroupMembers(groupId);

        for (GroupMemberEntity member : members) {
            Channel channel = userChannelMap.get(member.getUserId());
            if (channel != null && channel.isActive()) {
                channel.writeAndFlush(new ProtocolMessage(MessageType.SYSTEM_NOTIFY.getCode(), notifyData));
            }
        }
    }

    /**
     * 发送错误响应
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, String errorMsg) throws Exception {

        sendResponse(ctx, MessageType.ERROR_RESPONSE, "error", errorMsg);
    }

    /**
     * 向指定用户发送错误消息
     */
    private void sendErrorToUser(String userId, String errorMsg) throws Exception {

        sendResponseToUser(userId, MessageType.ERROR_RESPONSE, "error", errorMsg);
    }

    /**
     * 从Channel获取用户ID
     */
    public static String getUserIdFromChannel(Channel channel) {

        return channel.attr(USER_ID_ATTRIBUTE).get();
    }

    /**
     * 获取在线用户列表
     */
    public static List<String> getOnlineUsers() {

        return userChannelMap.keySet().stream().collect(Collectors.toList());
    }
}
