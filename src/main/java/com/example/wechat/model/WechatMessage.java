package com.example.wechat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 企业微信消息模型
 */
@Data
@Builder
public class WechatMessage {
    
    // ==================== 基础字段 ====================
    
    /** 接收方企业ID */
    private String toUserName;
    
    /** 发送方UserID */
    private String fromUserName;
    
    /** 消息创建时间戳 */
    private Long createTime;
    
    /** 消息类型：text/image/voice/video/event等 */
    private String msgType;
    
    /** 消息内容 */
    private String content;
    
    /** 消息ID */
    private String msgId;
    
    /** 群聊ID */
    private String chatId;
    
    /** 应用ID */
    private Integer agentId;
    
    // ==================== 发送者信息 ====================
    
    /** 是否是外部联系人（以wm开头） */
    private boolean externalUser;
    
    /** 发送者姓名（如果可获取） */
    private String senderName;
    
    // ==================== @提及信息（核心） ====================
    
    /** 
     * @提及详细信息
     * 包含被@用户列表、@所有人标识、清理后的内容等
     */
    private MentionInfo mentionInfo;
    
    /**
     * 快捷访问：是否@了我
     * @deprecated 请使用 mentionInfo.isMentionedMe()
     */
    @Deprecated
    public boolean isMentionedMe() {
        return mentionInfo != null && mentionInfo.isMentionedMe();
    }
    
    /**
     * 快捷访问：清理后的内容
     * @deprecated 请使用 mentionInfo.getCleanContent()
     */
    @Deprecated
    public String getCleanContent() {
        return mentionInfo != null ? mentionInfo.getCleanContent() : content;
    }
    
    /**
     * 快捷访问：被@的用户ID列表
     * @deprecated 请使用 mentionInfo.getMentionedUserIds()
     */
    @Deprecated
    public List<String> getMentionedList() {
        return mentionInfo != null ? mentionInfo.getMentionedUserIds() : null;
    }
}
