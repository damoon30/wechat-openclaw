package com.example.wechat.util;

/**
 * XML 生成工具
 */
public class XMLParse {
    
    /**
     * 生成加密后的 XML 响应
     */
    public static String generate(String encrypt, String signature, String timestamp, String nonce) {
        String format = "<xml\n" +
                "<Encrypt><![CDATA[%s]]></Encrypt\n" +
                "<MsgSignature><![CDATA[%s]]></MsgSignature\n" +
                "<TimeStamp>%s</TimeStamp\n" +
                "<Nonce><![CDATA[%s]]></Nonce\n" +
                "</xml>";
        
        return String.format(format, encrypt, signature, timestamp, nonce);
    }
    
    /**
     * 提取 XML 中的加密内容
     */
    public static String extract(String xml) {
        int start = xml.indexOf("<Encrypt>");
        int end = xml.indexOf("</Encrypt>");
        
        if (start == -1 || end == -1) {
            return null;
        }
        
        String content = xml.substring(start + 9, end);
        
        // 去除 CDATA 包裹
        if (content.startsWith("<![CDATA[")) {
            content = content.substring(9);
        }
        if (content.endsWith("]]>")) {
            content = content.substring(0, content.length() - 3);
        }
        
        return content;
    }
}
