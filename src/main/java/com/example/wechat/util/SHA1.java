package com.example.wechat.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * SHA1 签名工具
 */
public class SHA1 {
    
    /**
     * 计算 SHA1 签名
     */
    public static String getSHA1(String token, String timestamp, String nonce, String encrypt) 
            throws NoSuchAlgorithmException {
        String[] array = new String[]{token, timestamp, nonce, encrypt};
        Arrays.sort(array);
        
        StringBuilder content = new StringBuilder();
        for (String str : array) {
            content.append(str);
        }
        
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] digest = md.digest(content.toString().getBytes());
        
        StringBuilder hexStr = new StringBuilder();
        for (byte b : digest) {
            hexStr.append(String.format("%02x", b));
        }
        
        return hexStr.toString();
    }
}
