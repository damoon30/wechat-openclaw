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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信 API 服务
 * 
 * 支持内部群聊和外部群聊消息发送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatApiService {
    
    private final WechatConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private String accessToken;
    private long tokenExpireTime;
    
    /**
     * 获取 AccessToken
     */
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
    
    /**
     * 发送群聊消息
     * 
     * @param chatId 群聊ID
     * @param content 消息内容
     * @param isExternalChat 是否为外部群聊
     * @return 是否发送成功
     */
    public boolean sendGroupMessage(String chatId, String content, boolean isExternalChat) throws Exception {
        String token = getAccessToken();
        
        // 外部群聊和内部群聊使用不同的接口
        String url;
        Map<String, Object> body = new HashMap<>();
        
        if (isExternalChat) {
            // 外部群聊 - 使用应用消息发送到群
            url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
            body.put("chatid", chatId);
            body.put("msgtype", "text");
            body.put("agentid", config.getAgentId());
            body.put("text", Map.of("content", content));
            body.put("safe", 0);
            
            log.debug("使用外部群聊接口发送消息");
        } else {
            // 内部群聊 - 同样使用应用消息接口
            url = "https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=" + token;
            body.put("chatid", chatId);
            body.put("msgtype", "text");
            body.put("agentid", config.getAgentId());
            body.put("text", Map.of("content", content));
            body.put("safe", 0);
        }
        
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
                String errmsg = (String) resultMap.get("errmsg");
                
                if (errcode == null || errcode == 0) {
                    log.debug("消息发送成功");
                    return true;
                } else {
                    log.error("发送消息失败: [{}] {}", errcode, errmsg);
                    
                    // 处理特定错误码
                    if (errcode == 48002) {
                        log.error("API 接口无权限调用，外部群聊需要：\n" +
                                "1. 开通会话内容存档功能\n" +
                                "2. 确保机器人在群里\n" +
                                "3. 客户48小时内有互动");
                    } else if (errcode == 60011) {
                        log.error("不在群聊中，无法发送消息");
                    } else if (errcode == 42001) {
                        log.error("AccessToken 已过期");
                    }
                    
                    throw new RuntimeException("发送失败: [" + errcode + "] " + errmsg);
                }
            }
        }
    }
    
    /**
     * 发送群聊消息（默认内部群聊）
     */
    public boolean sendGroupMessage(String chatId, String content) throws Exception {
        return sendGroupMessage(chatId, content, false);
    }
    
    /**
     * 发送客服消息给外部联系人（备用方案）
     * 当客户超过48小时未互动时，无法使用应用消息，只能用客服消息
     */
    public boolean sendKfMessage(String externalUserId, String content) throws Exception {
        String token = getAccessToken();
        String url = "https://qyapi.weixin.qq.com/cgi-bin/kf/send_msg?access_token=" + token;
        
        Map<String, Object> body = new HashMap<>();
        body.put("touser", externalUserId);
        body.put("open_kfid", config.getKfId()); // 需要配置客服账号ID
        body.put("msgtype", "text");
        body.put("text", Map.of("content", content));
        
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
                    log.debug("客服消息发送成功");
                    return true;
                } else {
                    log.error("客服消息发送失败: [{}] {}", errcode, resultMap.get("errmsg"));
                    return false;
                }
            }
        }
    }
}
