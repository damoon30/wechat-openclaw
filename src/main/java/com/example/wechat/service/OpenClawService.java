package com.example.wechat.service;

import com.example.wechat.config.OpenClawConfig;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenClawService {
    
    private final OpenClawConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<OpenClawRequest.ContextMessage>> sessionCache = new ConcurrentHashMap<>();
    
    public String chat(String userId, String message) {
        String sessionKey = "wechat:" + userId;
        
        try {
            OpenClawRequest request = buildRequest(sessionKey, message);
            String response = callOpenClaw(request);
            saveContext(sessionKey, message, response);
            return response;
            
        } catch (Exception e) {
            log.error("调用 OpenClaw 失败", e);
            return "抱歉，我暂时无法处理您的请求，请稍后再试。";
        }
    }
    
    private OpenClawRequest buildRequest(String sessionKey, String message) {
        List<OpenClawRequest.ContextMessage> context = sessionCache.getOrDefault(
            sessionKey, new ArrayList<>()
        );
        
        return OpenClawRequest.builder()
            .message(message)
            .sessionKey(sessionKey)
            .agentId(config.getAgentId())
            .timeoutSeconds(120)
            .context(context)
            .build();
    }
    
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
    
    private String buildRequestBody(OpenClawRequest request) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        
        if (request.getContext() != null) {
            for (OpenClawRequest.ContextMessage ctx : request.getContext()) {
                messages.add(Map.of(
                    "role", ctx.getRole(),
                    "content", ctx.getContent()
                ));
            }
        }
        
        messages.add(Map.of("role", "user", "content", request.getMessage()));
        
        Map<String, Object> body = Map.of(
            "model", "kimi-coding/k2p5",
            "messages", messages,
            "temperature", 0.7,
            "max_tokens", 2000
        );
        
        return objectMapper.writeValueAsString(body);
    }
    
    private String extractContent(String responseBody) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            return (String) message.get("content");
        }
        
        return "抱歉，没有收到有效回复。";
    }
    
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
        
        if (context.size() > 20) {
            sessionCache.put(sessionKey, new ArrayList<>(
                context.subList(context.size() - 20, context.size())
            ));
        }
    }
}
