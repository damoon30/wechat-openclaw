package com.example.wechat.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OpenClawRequest {
    private String message;
    private String sessionKey;
    private String agentId;
    private Integer timeoutSeconds;
    private List<ContextMessage> context;
    
    @Data
    @Builder
    public static class ContextMessage {
        private String role;
        private String content;
    }
}
