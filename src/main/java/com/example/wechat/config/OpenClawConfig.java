package com.example.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "openclaw")
public class OpenClawConfig {
    private String gatewayUrl;
    private String authToken;
    private String agentId;
    private Long sessionTimeout;
    
    /**
     * 是否启用会话存档上下文
     * 需要开通企业微信会话内容存档功能
     */
    private boolean enableArchiveContext = false;
    
    /**
     * 获取上下文的消息数量（前后各N条）
     */
    private int contextSize = 10;
}
