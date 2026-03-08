package com.example.wechat.controller;

import com.example.wechat.model.WechatMessage;
import com.example.wechat.service.MessageDecryptService;
import com.example.wechat.service.MessageParseService;
import com.example.wechat.service.WechatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/wechat")
@RequiredArgsConstructor
public class WechatCallbackController {
    
    private final MessageDecryptService decryptService;
    private final MessageParseService parseService;
    private final WechatMessageService messageService;
    
    /**
     * 企业微信验证 URL 有效性（GET 请求）
     */
    @GetMapping("/callback")
    public String verify(
            @RequestParam("msg_signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        
        log.info("收到验证请求: signature={}, timestamp={}, nonce={}", 
            signature, timestamp, nonce);
        
        return echostr;
    }
    
    /**
     * 接收企业微信消息（POST 请求）
     */
    @PostMapping("/callback")
    public String receiveMessage(
            @RequestParam("msg_signature") String msgSignature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestBody String encryptData) {
        
        log.debug("收到加密消息: {}", encryptData);
        
        try {
            // 1. 解密消息
            String xmlData = decryptService.decryptMsg(
                msgSignature, timestamp, nonce, encryptData
            );
            
            // 2. 解析消息
            WechatMessage message = parseService.parse(xmlData);
            
            // 3. 异步处理消息
            new Thread(() -> messageService.handleMessage(message)).start();
            
            // 4. 返回成功
            return "success";
            
        } catch (Exception e) {
            log.error("处理消息失败", e);
            return "success";
        }
    }
}
