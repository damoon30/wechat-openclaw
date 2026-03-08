package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatApiService {
    
    private final WechatConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private String accessToken;
    private long tokenExpireTime;
    
    public synchronized String getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        
        try {
            String url = String.format(
                "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s",
                config.getCorpId(),
                config.getSecret()
            );
            
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpGet httpGet = new HttpGet(url);
                
                try (CloseableHttpResponse response = client.execute(httpGet)) {
                    String body = new String(
                        response.getEntity().getContent().readAllBytes()
                    );
                    
                    Map<String, Object> result = objectMapper.readValue(body, Map.class);
                    
                    if (result.get("errcode") == null || (Integer) result.get("errcode") == 0) {
                        accessToken = (String) result.get("access_token");
                        int expiresIn = (Integer) result.get("expires_in");
                        tokenExpireTime = System.currentTimeMillis() + 
                            TimeUnit.SECONDS.toMillis(expiresIn - 300);
                        
                        log.debug("获取 AccessToken 成功");
                        return accessToken;
                    } else {
                        throw new RuntimeException("获取 Token 失败: " + result.get("errmsg"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取 AccessToken 失败", e);
            throw new RuntimeException("获取 AccessToken 失败: " + e.getMessage());
        }
    }
    
    public void sendGroupMessage(String chatId, String content) throws Exception {
        String token = getAccessToken();
        String url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
        
        Map<String, Object> body = Map.of(
            "chatid", chatId,
            "msgtype", "text",
            "agentid", config.getAgentId(),
            "text", Map.of("content", content),
            "safe", 0
        );
        
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(
                objectMapper.writeValueAsString(body),
                ContentType.APPLICATION_JSON
            ));
            
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String result = new String(
                    response.getEntity().getContent().readAllBytes()
                );
                
                Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
                Integer errcode = (Integer) resultMap.get("errcode");
                
                if (errcode == null || errcode == 0) {
                    log.debug("消息发送成功");
                } else {
                    log.error("发送消息失败: {} - {}", errcode, resultMap.get("errmsg"));
                    throw new RuntimeException("发送失败: " + resultMap.get("errmsg"));
                }
            }
        }
    }
}
