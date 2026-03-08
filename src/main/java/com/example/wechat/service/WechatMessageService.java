package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.MentionInfo;
import com.example.wechat.model.WechatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 微信消息处理服务
 * 
 * 支持内部群聊和外部群聊的@自动回复
 * 
 * 外部群聊限制：
 * 1. 需要开通企业微信"会话内容存档"功能
 * 2. 消息接收有1-5分钟延迟
 * 3. 外部客户48小时内未互动，无法主动发送消息
 * 4. 机器人必须在群里才能发送消息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMessageService {
    
    private final WechatConfig config;
    private final OpenClawService openClawService;
    private final WechatApiService wechatApiService;
    
    /**
     * 处理接收到的消息
     */
    public void handleMessage(WechatMessage message) {
        // 只处理文本消息
        if (!"text".equals(message.getMsgType())) {
            log.debug("非文本消息，忽略: {}", message.getMsgType());
            return;
        }
        
        MentionInfo mentionInfo = message.getMentionInfo();
        if (mentionInfo == null) {
            log.debug("无法获取@信息，忽略");
            return;
        }
        
        // 只响应 @ 了我的消息
        if (!mentionInfo.isMentionedMe()) {
            log.debug("消息未@我，被@用户: {}, 是否@所有人: {}", 
                mentionInfo.getMentionedUserIds(), 
                mentionInfo.isMentionedAll()
            );
            return;
        }
        
        String userId = message.getFromUserName();
        String question = mentionInfo.getCleanContent();
        
        if (question == null || question.trim().isEmpty()) {
            log.warn("@我但内容为空，可能是纯@提醒");
            question = "你好，有什么可以帮您的？";
        }
        
        // 判断是否为外部群聊
        boolean isExternalChat = message.isExternalUser() || isExternalChatId(message.getChatId());
        
        log.info("收到@消息:\n" +
                "  群类型: {}\n" +
                "  发送者: {}\n" +
                "  是否外部联系人: {}\n" +
                "  是否@所有人: {}\n" +
                "  被@用户: {}\n" +
                "  @文本: {}\n" +
                "  问题: {}",
            isExternalChat ? "外部群聊" : "内部群聊",
            userId,
            message.isExternalUser(),
            mentionInfo.isMentionedAll(),
            mentionInfo.getMentionedUserIds(),
            mentionInfo.getMentionText(),
            question
        );
        
        // 外部群聊的特别提示
        if (isExternalChat) {
            log.info("注意：外部群聊消息可能存在1-5分钟延迟，且需要开通会话内容存档");
        }
        
        // 调用 OpenClaw 获取回复
        String reply = openClawService.chat(userId, question);
        
        // 发送回复到群聊
        if (message.getChatId() != null) {
            sendGroupReply(message, reply, userId, mentionInfo, isExternalChat);
        }
    }
    
    /**
     * 判断是否为外部群聊
     * 外部群聊ID通常以 wrc 开头（room chat）
     */
    private boolean isExternalChatId(String chatId) {
        if (chatId == null) return false;
        // 外部群聊ID特征：通常包含外部联系人标识
        // 这里可以根据实际情况调整判断逻辑
        return chatId.startsWith("wrc") || chatId.contains("external");
    }
    
    /**
     * 发送群聊回复
     */
    private void sendGroupReply(WechatMessage message, String reply, 
                               String atUser, MentionInfo mentionInfo, 
                               boolean isExternalChat) {
        try {
            String chatId = message.getChatId();
            String content;
            
            // 如果同时@了多人，可以都@回去
            if (mentionInfo.getMentionedUserIds().size() > 1) {
                StringBuilder sb = new StringBuilder();
                for (String userId : mentionInfo.getMentionedUserIds()) {
                    if (!"@all".equals(userId)) {
                        sb.append("<@").append(userId).append("> ");
                    }
                }
                content = sb.toString() + reply;
            } else {
                // 只@提问者
                content = String.format("<@%s> %s", atUser, reply);
            }
            
            // 发送消息
            boolean success = wechatApiService.sendGroupMessage(chatId, content, isExternalChat);
            
            if (success) {
                log.info("回复已发送:\n" +
                        "  群类型: {}\n" +
                        "  群ID: {}\n" +
                        "  内容: {}",
                    isExternalChat ? "外部群聊" : "内部群聊",
                    chatId, 
                    content
                );
            } else {
                log.error("发送消息失败，可能是外部客户超过48小时未互动");
            }
            
        } catch (Exception e) {
            log.error("发送回复失败: chatId={}, isExternal={}", message.getChatId(), isExternalChat, e);
            
            // 外部群聊的特殊错误处理
            if (isExternalChat && e.getMessage() != null) {
                if (e.getMessage().contains("48002") || e.getMessage().contains("api forbidden")) {
                    log.error("外部群聊发送失败：可能需要开通会话内容存档，或客户超过48小时未互动");
                }
            }
        }
    }
}
