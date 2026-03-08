package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.WechatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMessageService {
    
    private final WechatConfig config;
    private final OpenClawService openClawService;
    private final WechatApiService wechatApiService;
    
    public void handleMessage(WechatMessage message) {
        if (!"text".equals(message.getMsgType())) {
            log.debug("非文本消息，忽略: {}", message.getMsgType());
            return;
        }
        
        if (!message.isMentionedMe()) {
            log.debug("消息未@我，忽略");
            return;
        }
        
        String userId = message.getFromUserName();
        String question = message.getCleanContent();
        
        log.info("收到@消息: from={}, question={}", userId, question);
        
        String reply = openClawService.chat(userId, question);
        
        if (message.getChatId() != null) {
            sendGroupReply(message.getChatId(), reply, userId);
        }
    }
    
    private void sendGroupReply(String chatId, String reply, String atUser) {
        try {
            String content = String.format("<@%s> %s", atUser, reply);
            wechatApiService.sendGroupMessage(chatId, content);
            log.info("回复已发送: chatId={}", chatId);
        } catch (Exception e) {
            log.error("发送回复失败", e);
        }
    }
}
