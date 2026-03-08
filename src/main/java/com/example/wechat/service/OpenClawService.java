package com.example.wechat.service;

import com.example.wechat.config.OpenClawConfig;
import com.example.wechat.model.ArchiveMessage;
import com.example.wechat.model.OpenClawRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OpenClaw 服务
 * 
 * 支持使用会话存档上下文进行对话
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawService {
    
    private final OpenClawConfig config;
    private final MsgAuditService msgAuditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 简单的会话缓存（生产环境建议用 Redis）
    private final Map<String, List<OpenClawRequest.ContextMessage>> sessionCache = 
        new ConcurrentHashMap<>();
    
    /**
     * 普通对话（无上下文）
     */
    public String chat(String userId, String message) {
        return chat(userId, message, null, null);
    }
    
    /**
     * 带群聊上下文的对话
     * 
     * @param userId 用户ID
     * @param message 当前消息
     * @param roomId 群聊ID（可选）
     * @param msgId 当前消息ID（可选，用于获取上下文）
     */
    public String chat(String userId, String message, String roomId, String msgId) {
        String sessionKey = "wechat:" + userId;
        
        try {
            // 构建请求
            OpenClawRequest request = buildRequest(sessionKey, message, roomId, msgId);
            
            // 调用 OpenClaw HTTP API
            String response = callOpenClaw(request);
            
            // 保存上下文
            saveContext(sessionKey, message, response);
            
            return response;
            
        } catch (Exception e) {
            log.error("调用 OpenClaw 失败", e);
            return "抱歉，我暂时无法处理您的请求，请稍后再试。";
        }
    }
    
    /**
     * 构建 OpenClaw 请求
     */
    private OpenClawRequest buildRequest(String sessionKey, String message, 
                                        String roomId, String msgId) {
        List<OpenClawRequest.ContextMessage> context = new ArrayList<>();
        
        // 1. 如果有群聊上下文，优先使用会话存档的上下文
        if (roomId != null && msgId != null) {
            List<ArchiveMessage> archiveContext = msgAuditService.getMessageContext(
                roomId, msgId, 10  // 前后各10条
            );
            
            if (!archiveContext.isEmpty()) {
                log.info("使用会话存档上下文: {} 条消息", archiveContext.size());
                
                // 转换存档消息为 OpenClaw 上下文
                for (ArchiveMessage msg : archiveContext) {
                    String role = isBotUser(msg.getFromUserId()) ? "assistant" : "user";
                    String content = formatArchiveMessage(msg);
                    
                    context.add(OpenClawRequest.ContextMessage.builder()
                        .role(role)
                        .content(content)
                        .build());
                }
            }
        }
        
        // 2. 如果没有存档上下文，使用本地会话缓存
        if (context.isEmpty()) {
            context = sessionCache.getOrDefault(sessionKey, new ArrayList<>());
        }
        
        return OpenClawRequest.builder()
            .message(message)
            .sessionKey(sessionKey)
            .agentId(config.getAgentId())
            .timeoutSeconds(120)
            .context(context)
            .build();
    }
    
    /**
     * 判断是否为机器人账号
     */
    private boolean isBotUser(String userId) {
        // 根据实际情况配置机器人UserID
        return "ZhangSan".equals(userId); // 替换为实际配置
    }
    
    /**
     * 格式化存档消息
     */
    private String formatArchiveMessage(ArchiveMessage msg) {
        String sender = msg.getFromUserName() != null 
            ? msg.getFromUserName() 
            : msg.getFromUserId();
        
        return String.format("[%s]: %s", sender, msg.getContent());
    }
    
    /**
     * 调用 OpenClaw Gateway
     */
    private String callOpenClaw(OpenClawRequest request) throws Exception {
        String url = config.getGatewayUrl() + "/v1/chat/completions";
        String requestBody = buildRequestBody(request);
        
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            
            if (config.getAuthToken() != null && !config.getAuthToken().isEmpty()) {
                httpPost.setHeader("Authorization", "Bearer " + config.getAuthToken());
            }
            
            httpPost.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getCode();
                String responseBody = new String(
                    response.getEntity().getContent().readAllBytes()
                );
                
                if (statusCode == 200) {
                    return extractContent(responseBody);
                } else {
                    log.error("OpenClaw 返回错误: {} - {}", statusCode, responseBody);
                    throw new RuntimeException("OpenClaw API 错误: " + statusCode);
                }
            }
        }
    }
    
    /**
     * 构建兼容 OpenAI 格式的请求体
     */
    private String buildRequestBody(OpenClawRequest request) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        
        // 添加系统提示
        messages.add(Map.of(
            "role", "system",
            "content", "你是企业微信群聊助手。用户正在查看群聊上下文，请基于上下文回答问题。"
        ));
        
        // 添加上下文历史
        if (request.getContext() != null) {
            for (OpenClawRequest.ContextMessage ctx : request.getContext()) {
                messages.add(Map.of(
                    "role", ctx.getRole(),
                    "content", ctx.getContent()
                ));
            }
        }
        
        // 添加当前消息
        messages.add(Map.of("role", "user", "content", request.getMessage()));
        
        Map<String, Object> body = Map.of(
            "model", "kimi-coding/k2p5",
            "messages", messages,
            "temperature", 0.7,
            "max_tokens", 2000
        );
        
        return objectMapper.writeValueAsString(body);
    }
    
    /**
     * 从 OpenAI 格式响应中提取内容
     */
    private String extractContent(String responseBody) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
        
        return "抱歉，没有收到有效回复。";
    }
    
    /**
     * 保存会话上下文
     */
    private void saveContext(String sessionKey, String userMsg, String assistantMsg) {
        List<OpenClawRequest.ContextMessage> context = 
            sessionCache.computeIfAbsent(sessionKey, k -> new ArrayList<>());
        
        context.add(OpenClawRequest.ContextMessage.builder()
            .role("user")
            .content(userMsg)
            .build());
        
        context.add(OpenClawRequest.ContextMessage.builder()
            .role("assistant")
            .content(assistantMsg)
            .build());
        
        // 限制上下文长度
        if (context.size() > 20) {
            sessionCache.put(sessionKey, new ArrayList<>(
                context.subList(context.size() - 20, context.size())
            ));
        }
    }
}
