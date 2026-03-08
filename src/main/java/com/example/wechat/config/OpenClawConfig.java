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
}
