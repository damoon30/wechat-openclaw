package com.example.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {
    
    /** 企业ID */
    private String corpId;
    
    /** 应用ID */
    private Integer agentId;
    
    /** 应用Secret */
    private String secret;
    
    /** 消息回调Token */
    private String token;
    
    /** 消息加密密钥 */
    private String encodingAesKey;
    
    /** 机器人UserID（用于识别@） */
    private String botUserId;
    
    /** 客服账号ID（可选，用于发送客服消息） */
    private String kfId;
}
