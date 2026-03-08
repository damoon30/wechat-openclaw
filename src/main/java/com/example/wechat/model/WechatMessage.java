package com.example.wechat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WechatMessage {
    // 原始字段
    private String toUserName;
    private String fromUserName;
    private Long createTime;
    private String msgType;
    private String content;
    private String msgId;
    private String chatId;
    private Integer agentId;
    
    // @相关
    private List<String> mentionedList;
    private boolean mentionedMe;
    
    // 处理后的内容
    private String cleanContent;
    
    // 是否是外部联系人
    private boolean externalUser;
}
