package com.example.wechat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @提及详情
 */
@Data
@Builder
public class MentionInfo {
    
    /**
     * 是否@了当前机器人
     */
    private boolean mentionedMe;
    
    /**
     * 是否@了所有人
     */
    private boolean mentionedAll;
    
    /**
     * 被@的用户ID列表（包含UserID和@all）
     */
    private List<String> mentionedUserIds;
    
    /**
     * 被@的用户姓名列表（从Content中解析）
     */
    private List<String> mentionedUserNames;
    
    /**
     * 原始消息中@部分的文本
     * 例如: "@AI助手 @张三"
     */
    private String mentionText;
    
    /**
     * 去除@部分后的纯消息内容
     */
    private String cleanContent;
    
    /**
     * 第一个被@的位置（用于高亮等）
     */
    private int firstMentionIndex;
    
    /**
     * @提及的数量
     */
    private int mentionCount;
}
