package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.WechatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageParseService {
    
    private final WechatConfig config;
    private static final Pattern MENTION_PATTERN = Pattern.compile("^@[^\\s]+[\\s\\n]*");
    
    public WechatMessage parse(String xmlData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8))
            );
            
            Element root = doc.getDocumentElement();
            
            WechatMessage.WechatMessageBuilder message = WechatMessage.builder();
            message.toUserName(getElementText(root, "ToUserName"));
            message.fromUserName(getElementText(root, "FromUserName"));
            message.createTime(parseLong(getElementText(root, "CreateTime")));
            message.msgType(getElementText(root, "MsgType"));
            message.content(getElementText(root, "Content"));
            message.msgId(getElementText(root, "MsgId"));
            message.chatId(getElementText(root, "ChatId"));
            message.agentId(parseInt(getElementText(root, "AgentID")));
            
            List<String> mentionedList = parseMentionedList(root);
            message.mentionedList(mentionedList);
            
            boolean mentionedMe = mentionedList.contains(config.getBotUserId());
            message.mentionedMe(mentionedMe);
            
            String cleanContent = extractCleanContent(message.build().getContent(), mentionedMe);
            message.cleanContent(cleanContent);
            
            String fromUser = message.build().getFromUserName();
            message.externalUser(fromUser != null && fromUser.startsWith("wm"));
            
            log.debug("解析消息: from={}, mentionedMe={}, content={}", 
                fromUser, mentionedMe, cleanContent);
            
            return message.build();
            
        } catch (Exception e) {
            log.error("解析 XML 失败", e);
            throw new RuntimeException("消息解析失败: " + e.getMessage());
        }
    }
    
    private List<String> parseMentionedList(Element root) {
        List<String> list = new ArrayList<>();
        NodeList mentionedNodes = root.getElementsByTagName("MentionedList");
        
        if (mentionedNodes.getLength() > 0) {
            Element mentionedElem = (Element) mentionedNodes.item(0);
            NodeList items = mentionedElem.getElementsByTagName("Item");
            
            for (int i = 0; i < items.getLength(); i++) {
                String userId = items.item(i).getTextContent();
                if (userId != null && !userId.isEmpty()) {
                    list.add(userId);
                }
            }
        }
        
        return list;
    }
    
    private String extractCleanContent(String content, boolean mentioned) {
        if (!mentioned || content == null) {
            return content;
        }
        return MENTION_PATTERN.matcher(content).replaceFirst("").trim();
    }
    
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }
    
    private Long parseLong(String value) {
        try {
            return value != null ? Long.parseLong(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private Integer parseInt(String value) {
        try {
            return value != null ? Integer.parseInt(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
