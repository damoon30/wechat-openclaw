package com.example.wechat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 会话存档消息
 * 对应企业微信会话内容存档接口返回的消息结构
 */
@Data
@Builder
public class ArchiveMessage {
    
    /** 消息唯一标识 */
    private String msgId;
    
    /** 消息发送方ID */
    private String fromUserId;
    
    /** 发送方姓名（如果可获取） */
    private String fromUserName;
    
    /** 是否为外部联系人 */
    private boolean fromExternalUser;
    
    /** 消息类型：text/image/voice/video/file/link/weapp... */
    private String msgType;
    
    /** 消息内容（文本消息） */
    private String content;
    
    /** 消息发送时间戳 */
    private Long msgTime;
    
    /** 房间ID（群聊） */
    private String roomId;
    
    /** 接收方列表（单聊） */
    private List<String> toUserList;
    
    /** 是否为@消息 */
    private boolean mentionMsg;
    
    /** 被@的用户列表 */
    private List<String> mentionedUserList;
    
    /** 序列号（用于分页拉取） */
    private Long seq;
    
    /** 是否已撤回 */
    private boolean revoked;
    
    /** 引用回复的消息ID */
    private String quoteMsgId;
}
