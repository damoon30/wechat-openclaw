package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.util.WXBizMsgCrypt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDecryptService {
    
    private final WechatConfig config;
    
    public String decryptMsg(String msgSignature, String timestamp, 
                           String nonce, String encryptMsg) {
        try {
            WXBizMsgCrypt crypt = new WXBizMsgCrypt(
                config.getToken(),
                config.getEncodingAesKey(),
                config.getCorpId()
            );
            
            String result = crypt.decryptMsg(
                msgSignature,
                timestamp,
                nonce,
                encryptMsg
            );
            
            log.debug("解密成功: {}", result);
            return result;
            
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new RuntimeException("消息解密失败: " + e.getMessage());
        }
    }
    
    public String encryptMsg(String replyMsg, String timestamp, String nonce) {
        try {
            WXBizMsgCrypt crypt = new WXBizMsgCrypt(
                config.getToken(),
                config.getEncodingAesKey(),
                config.getCorpId()
            );
            
            return crypt.encryptMsg(replyMsg, timestamp, nonce);
            
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("消息加密失败: " + e.getMessage());
        }
    }
}
