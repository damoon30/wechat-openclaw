package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.ArchiveMessage;
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

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 会话内容存档服务
 * 
 * 从企业微信会话内容存档接口拉取历史消息，获取上下文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MsgAuditService {
    
    private final WechatConfig config;
    private final WechatApiService wechatApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /** 会话存档密钥（需要单独申请，与消息回调密钥不同） */
    private String msgAuditSecret;
    
    /** 加密密钥（从 msgAuditSecret 派生） */
    private byte[] encryptionKey;
    
    /**
     * 初始化加密密钥
     */
    private void initEncryptionKey() {
        if (encryptionKey != null) return;
        
        try {
            // 会话存档密钥派生：PKCS5Padding(SHA256(msgAuditSecret))
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(msgAuditSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // 取前32字节作为AES密钥
            encryptionKey = new byte[32];
            System.arraycopy(hash, 0, encryptionKey, 0, 32);
            
        } catch (Exception e) {
            log.error("初始化加密密钥失败", e);
            throw new RuntimeException("初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 拉取群聊消息（带上下文）
     * 
     * @param roomId 群聊ID
     * @param startTime 开始时间戳
     * @param limit 拉取数量（最大1000）
     * @return 消息列表
     */
    public List<ArchiveMessage> fetchGroupChatMessages(String roomId, long startTime, int limit) {
        try {
            String token = wechatApiService.getAccessToken();
            String url = "https://qyapi.weixin.qq.com/cgi-bin/msgaudit/get_groupchat?access_token=" + token;
            
            Map<String, Object> body = Map.of(
                "roomid", roomId,
                "start_time", startTime,
                "limit", Math.min(limit, 1000)
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
                    
                    if (errcode != null && errcode != 0) {
                        log.error("拉取群聊消息失败: [{}] {}", errcode, resultMap.get("errmsg"));
                        return new ArrayList<>();
                    }
                    
                    // 解析加密的消息数据
                    String encryptedData = (String) resultMap.get("encrypt_chat_msg");
                    if (encryptedData == null) {
                        log.warn("没有获取到加密消息数据");
                        return new ArrayList<>();
                    }
                    
                    // 解密并解析消息
                    return decryptAndParseMessages(encryptedData);
                }
            }
            
        } catch (Exception e) {
            log.error("拉取群聊消息失败: roomId={}", roomId, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取消息上下文
     * 
     * @param roomId 群聊ID
     * @param targetMsgId 目标消息ID
     * @param contextSize 上下文消息数量（前后各N条）
     * @return 包含上下文的消息列表（按时间排序）
     */
    public List<ArchiveMessage> getMessageContext(String roomId, String targetMsgId, int contextSize) {
        // 1. 先拉取最近的一批消息
        long startTime = System.currentTimeMillis() / 1000 - 3600; // 最近1小时
        List<ArchiveMessage> messages = fetchGroupChatMessages(roomId, startTime, 1000);
        
        if (messages.isEmpty()) {
            return messages;
        }
        
        // 2. 找到目标消息的位置
        int targetIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (targetMsgId.equals(messages.get(i).getMsgId())) {
                targetIndex = i;
                break;
            }
        }
        
        if (targetIndex == -1) {
            log.warn("未找到目标消息: msgId={}", targetMsgId);
            return messages; // 返回全部，让AI自己找
        }
        
        // 3. 提取上下文
        int startIndex = Math.max(0, targetIndex - contextSize);
        int endIndex = Math.min(messages.size(), targetIndex + contextSize + 1);
        
        List<ArchiveMessage> context = messages.subList(startIndex, endIndex);
        
        log.info("获取消息上下文成功: targetIndex={}, contextSize={}", targetIndex, context.size());
        
        return context;
    }
    
    /**
     * 将存档消息转换为 OpenClaw 上下文格式
     */
    public List<Map<String, String>> convertToOpenClawContext(List<ArchiveMessage> messages, String botUserId) {
        List<Map<String, String>> context = new ArrayList<>();
        
        for (ArchiveMessage msg : messages) {
            String role = botUserId.equals(msg.getFromUserId()) ? "assistant" : "user";
            String content = formatMessageContent(msg);
            
            context.add(Map.of(
                "role", role,
                "content", content,
                "name", msg.getFromUserName() != null ? msg.getFromUserName() : msg.getFromUserId()
            ));
        }
        
        return context;
    }
    
    /**
     * 格式化消息内容
     */
    private String formatMessageContent(ArchiveMessage msg) {
        StringBuilder sb = new StringBuilder();
        
        // 添加发送者信息
        sb.append("[").append(msg.getFromUserName() != null ? msg.getFromUserName() : msg.getFromUserId()).append("]: ");
        
        // 根据消息类型处理内容
        switch (msg.getMsgType()) {
            case "text":
                sb.append(msg.getContent());
                break;
            case "image":
                sb.append("[图片]");
                break;
            case "voice":
                sb.append("[语音]");
                break;
            case "video":
                sb.append("[视频]");
                break;
            case "file":
                sb.append("[文件]");
                break;
            case "link":
                sb.append("[链接]");
                break;
            default:
                sb.append("[").append(msg.getMsgType()).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * 解密并解析消息列表
     */
    private List<ArchiveMessage> decryptAndParseMessages(String encryptedData) {
        List<ArchiveMessage> messages = new ArrayList<>();
        
        try {
            initEncryptionKey();
            
            // Base64解码
            byte[] encrypted = Base64.getDecoder().decode(encryptedData);
            
            // AES解密
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(encryptionKey, "AES");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            
            byte[] decrypted = cipher.doFinal(encrypted);
            String jsonStr = new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
            
            // 解析JSON
            List<Map<String, Object>> msgList = objectMapper.readValue(jsonStr, List.class);
            
            for (Map<String, Object> msgMap : msgList) {
                ArchiveMessage msg = parseArchiveMessage(msgMap);
                if (msg != null) {
                    messages.add(msg);
                }
            }
            
        } catch (Exception e) {
            log.error("解密消息失败", e);
        }
        
        return messages;
    }
    
    /**
     * 解析单条存档消息
     */
    private ArchiveMessage parseArchiveMessage(Map<String, Object> msgMap) {
        try {
            ArchiveMessage.ArchiveMessageBuilder msg = ArchiveMessage.builder();
            
            msg.msgId((String) msgMap.get("msgid"));
            msg.fromUserId((String) msgMap.get("from"));
            msg.msgType((String) msgMap.get("msgtype"));
            msg.msgTime(parseLong(msgMap.get("msgtime")));
            msg.roomId((String) msgMap.get("roomid"));
            msg.seq(parseLong(msgMap.get("seq")));
            
            // 判断是否为外部联系人
            String fromUserId = (String) msgMap.get("from");
            msg.fromExternalUser(fromUserId != null && fromUserId.startsWith("wm"));
            
            // 解析具体内容
            Map<String, Object> contentMap = (Map<String, Object>) msgMap.get("msg");
            if (contentMap != null) {
                parseMessageContent(msg, msg.getMsgType(), contentMap);
            }
            
            // 解析@信息
            List<String> mentionList = (List<String>) msgMap.get("mention_list");
            if (mentionList != null && !mentionList.isEmpty()) {
                msg.mentionMsg(true);
                msg.mentionedUserList(mentionList);
            }
            
            return msg.build();
            
        } catch (Exception e) {
            log.error("解析存档消息失败", e);
            return null;
        }
    }
    
    private void parseMessageContent(ArchiveMessage.ArchiveMessageBuilder msg, String msgType, Map<String, Object> content) {
        switch (msgType) {
            case "text":
                msg.content((String) content.get("content"));
                break;
            case "image":
                msg.content("[图片: " + content.get("sdkfileid") + "]");
                break;
            case "voice":
                msg.content("[语音: " + content.get("sdkfileid") + "]");
                break;
            case "revoke":
                msg.revoked(true);
                msg.content("[已撤回消息]");
                break;
            default:
                msg.content("[" + msgType + "]");
        }
    }
    
    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * 设置会话存档密钥（从配置或数据库获取）
     */
    public void setMsgAuditSecret(String secret) {
        this.msgAuditSecret = secret;
        this.encryptionKey = null; // 重置密钥
    }
}
