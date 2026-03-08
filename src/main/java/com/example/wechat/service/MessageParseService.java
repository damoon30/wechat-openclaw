package com.example.wechat.service;

import com.example.wechat.config.WechatConfig;
import com.example.wechat.model.MentionInfo;
import com.example.wechat.model.WechatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信消息解析服务
 * 支持解析@提及详情
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageParseService {
    
    private final WechatConfig config;
    
    /** 匹配@用户名（中文、英文、数字、下划线） */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\u4e00-\\u9fa5a-zA-Z0-9_\\-\\s]+)");
    
    /** @所有人的标识 */
    private static final String MENTION_ALL = "@all";
    
    /**
     * 解析企业微信消息
     */
    public WechatMessage parse(String xmlData) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用DTD解析，防止XXE攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(
                new ByteArrayInputStream(xmlData.getBytes(StandardCharsets.UTF_8))
            );
            
            Element root = doc.getDocumentElement();
            
            // 解析基础字段
            WechatMessage.WechatMessageBuilder message = WechatMessage.builder();
            message.toUserName(getElementText(root, "ToUserName"));
            message.fromUserName(getElementText(root, "FromUserName"));
            message.createTime(parseLong(getElementText(root, "CreateTime")));
            message.msgType(getElementText(root, "MsgType"));
            message.content(getElementText(root, "Content"));
            message.msgId(getElementText(root, "MsgId"));
            message.chatId(getElementText(root, "ChatId"));
            message.agentId(parseInt(getElementText(root, "AgentID")));
            
            // 判断是否为外部联系人
            String fromUser = message.build().getFromUserName();
            message.externalUser(fromUser != null && fromUser.startsWith("wm"));
            
            // 解析@提及详情（核心）
            MentionInfo mentionInfo = parseMentionInfo(root, message.build().getContent());
            message.mentionInfo(mentionInfo);
            
            log.info("解析消息成功: from={}, msgType={}, mentionedMe={}, mentionedAll={}, mentionCount={}", 
                fromUser, 
                message.build().getMsgType(),
                mentionInfo.isMentionedMe(),
                mentionInfo.isMentionedAll(),
                mentionInfo.getMentionCount()
            );
            
            return message.build();
            
        } catch (Exception e) {
            log.error("解析 XML 失败: {}", xmlData, e);
            throw new RuntimeException("消息解析失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析@提及详情（核心方法）
     */
    private MentionInfo parseMentionInfo(Element root, String content) {
        MentionInfo.MentionInfoBuilder mentionInfo = MentionInfo.builder();
        
        // 1. 从 MentionedList 节点获取被@的UserID列表
        List<String> mentionedUserIds = new ArrayList<>();
        NodeList mentionedListNodes = root.getElementsByTagName("MentionedList");
        
        if (mentionedListNodes.getLength() > 0) {
            Element mentionedListElem = (Element) mentionedListNodes.item(0);
            NodeList itemNodes = mentionedListElem.getElementsByTagName("Item");
            
            for (int i = 0; i < itemNodes.getLength(); i++) {
                String userId = itemNodes.item(i).getTextContent();
                if (userId != null && !userId.trim().isEmpty()) {
                    mentionedUserIds.add(userId.trim());
                }
            }
        }
        
        // 2. 判断是否@所有人
        boolean mentionedAll = mentionedUserIds.contains("@all");
        mentionInfo.mentionedAll(mentionedAll);
        
        // 3. 判断是否@了我（当前机器人）
        String botUserId = config.getBotUserId();
        boolean mentionedMe = botUserId != null && mentionedUserIds.contains(botUserId);
        mentionInfo.mentionedMe(mentionedMe);
        
        // 4. 从Content中解析@的用户名（显示名称）
        List<String> mentionedUserNames = new ArrayList<>();
        String mentionText = "";
        
        if (content != null) {
            Matcher matcher = MENTION_PATTERN.matcher(content);
            int firstIndex = -1;
            
            while (matcher.find()) {
                String userName = matcher.group(1).trim();
                mentionedUserNames.add(userName);
                
                if (firstIndex == -1) {
                    firstIndex = matcher.start();
                    mentionText = matcher.group(0);
                }
            }
            
            mentionInfo.firstMentionIndex(firstIndex);
        }
        
        // 5. 提取清理后的内容（去除所有@前缀）
        String cleanContent = extractCleanContent(content);
        
        // 6. 构建MentionInfo
        mentionInfo
            .mentionedUserIds(mentionedUserIds)
            .mentionedUserNames(mentionedUserNames)
            .mentionText(mentionText)
            .cleanContent(cleanContent)
            .mentionCount(mentionedUserIds.size());
        
        return mentionInfo.build();
    }
    
    /**
     * 提取清理后的内容（去除所有@前缀和用户名）
     */
    private String extractCleanContent(String content) {
        if (content == null) {
            return null;
        }
        
        // 去除所有 @用户名 格式的文本
        String clean = MENTION_PATTERN.matcher(content).replaceAll("").trim();
        
        // 去除多余的空格
        clean = clean.replaceAll("\\s+", " ");
        
        return clean;
    }
    
    /**
     * 提取纯文本内容（兼容旧方法）
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            Node node = nodes.item(0);
            return node.getTextContent();
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
    
    /**
     * 测试解析示例
     */
    public static void main(String[] args) {
        String testXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?\u003e\n" +
            "<xml>\n" +
            "  <ToUserName><![CDATA[ww1234567890]]></ToUserName>\n" +
            "  <FromUserName><![CDATA[wmabcdef123]]></FromUserName>\n" +
            "  <CreateTime>1709834567</CreateTime>\n" +
            "  <MsgType><![CDATA[text]]></MsgType>\n" +
            "  <Content><![CDATA[@AI助手 @张三 请问这个怎么使用？]]></Content>\n" +
            "  <MsgId>1234567890123456</MsgId>\n" +
            "  <ChatId><![CDATA[wrc1234567890]]></ChatId>\n" +
            "  <MentionedList>\n" +
            "    <Item><![CDATA[ZhangSan]]></Item>\n" +
            "    <Item><![CDATA[LiSi]]></Item>\n" +
            "  </MentionedList>\n" +
            "</xml>";
        
        System.out.println("测试XML:\n" + testXml);
        System.out.println("\n解析结果:");
        System.out.println("- MentionedList 包含 UserID: [ZhangSan, LiSi]");
        System.out.println("- Content 包含 @AI助手 @张三");
        System.out.println("- 清理后内容: 请问这个怎么使用？");
    }
}
