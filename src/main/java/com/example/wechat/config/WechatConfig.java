package com.example.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {
    private String corpId;
    private Integer agentId;
    private String secret;
    private String token;
    private String encodingAesKey;
    private String botUserId;
}
