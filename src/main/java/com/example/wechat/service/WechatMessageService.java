package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.MentionInfo;
import com.example.wechat.model.WechatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 微信消息处理服务
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
        
        log.info("收到@消息:\n" +
                "  发送者: {}\n" +
                "  是否@所有人: {}\n" +
                "  被@用户: {}\n" +
                "  @文本: {}\n" +
                "  问题: {}",
            userId,
            mentionInfo.isMentionedAll(),
            mentionInfo.getMentionedUserIds(),
            mentionInfo.getMentionText(),
            question
        );
        
        // 调用 OpenClaw 获取回复
        String reply = openClawService.chat(userId, question);
        
        // 发送回复到群聊
        if (message.getChatId() != null) {
            sendGroupReply(message.getChatId(), reply, userId, mentionInfo);
        }
    }
    
    /**
     * 发送群聊回复
     */
    private void sendGroupReply(String chatId, String reply, String atUser, MentionInfo mentionInfo) {
        try {
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
            
            wechatApiService.sendGroupMessage(chatId, content);
            
            log.info("回复已发送:\n" +
                    "  群ID: {}\n" +
                    "  内容: {}",
                chatId, content
            );
            
        } catch (Exception e) {
            log.error("发送回复失败: chatId={}", chatId, e);
        }
    }
}
