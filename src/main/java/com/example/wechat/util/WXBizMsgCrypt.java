package com.example.wechat.util;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

/**
 * 企业微信消息加解密工具
 * 兼容企业微信官方 SDK
 */
public class WXBizMsgCrypt {
    
    private static final String AES_MODE = "AES/CBC/NoPadding";
    private final String token;
    private final String encodingAesKey;
    private final String corpId;
    
    public WXBizMsgCrypt(String token, String encodingAesKey, String corpId) {
        this.token = token;
        this.encodingAesKey = encodingAesKey;
        this.corpId = corpId;
    }
    
    /**
     * 解密消息
     */
    public String decryptMsg(String msgSignature, String timeStamp, String nonce, 
                            String encryptMsg) throws Exception {
        // 验证签名
        String signature = SHA1.getSHA1(token, timeStamp, nonce, encryptMsg);
        if (!signature.equals(msgSignature)) {
            throw new Exception("签名验证失败");
        }
        
        // 解密
        String decrypted = decrypt(encryptMsg);
        return decrypted;
    }
    
    /**
     * 加密消息
     */
    public String encryptMsg(String replyMsg, String timeStamp, String nonce) throws Exception {
        // 加密
        String encrypt = encrypt(replyMsg);
        
        // 生成签名
        String signature = SHA1.getSHA1(token, timeStamp, nonce, encrypt);
        
        // 生成 XML
        return XMLParse.generate(encrypt, signature, timeStamp, nonce);
    }
    
    /**
     * AES 解密
     */
    private String decrypt(String encryptMsg) throws Exception {
        byte[] aesKey = Base64.decodeBase64(encodingAesKey + "=");
        byte[] encrypted = Base64.decodeBase64(encryptMsg);
        
        Cipher cipher = Cipher.getInstance(AES_MODE);
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        
        byte[] decrypted = cipher.doFinal(encrypted);
        
        // 去除填充
        decrypted = PKCS7Encoder.decode(decrypted);
        
        // 解析格式: random(16) + msgLen(4) + msg + corpId
        int msgLen = bytesToInt(decrypted, 16);
        String msg = new String(decrypted, 20, msgLen, StandardCharsets.UTF_8);
        
        return msg;
    }
    
    /**
     * AES 加密
     */
    private String encrypt(String msg) throws Exception {
        byte[] aesKey = Base64.decodeBase64(encodingAesKey + "=");
        
        // 构造明文: random(16) + msgLen(4) + msg + corpId
        byte[] msgBytes = msg.getBytes(StandardCharsets.UTF_8);
        byte[] corpIdBytes = corpId.getBytes(StandardCharsets.UTF_8);
        
        int msgLen = msgBytes.length;
        int totalLen = 16 + 4 + msgLen + corpIdBytes.length;
        
        // PKCS7 填充
        int blockSize = 32;
        int padLen = blockSize - (totalLen % blockSize);
        totalLen += padLen;
        
        byte[] fullMsg = new byte[totalLen];
        
        // 随机 16 字节
        new Random().nextBytes(fullMsg);
        
        // 消息长度（4字节，大端序）
        fullMsg[16] = (byte) ((msgLen >> 24) & 0xFF);
        fullMsg[17] = (byte) ((msgLen >> 16) & 0xFF);
        fullMsg[18] = (byte) ((msgLen >> 8) & 0xFF);
        fullMsg[19] = (byte) (msgLen & 0xFF);
        
        // 消息内容
        System.arraycopy(msgBytes, 0, fullMsg, 20, msgLen);
        
        // CorpID
        System.arraycopy(corpIdBytes, 0, fullMsg, 20 + msgLen, corpIdBytes.length);
        
        // 填充
        for (int i = 20 + msgLen + corpIdBytes.length; i < totalLen; i++) {
            fullMsg[i] = (byte) padLen;
        }
        
        // AES 加密
        Cipher cipher = Cipher.getInstance(AES_MODE);
        SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        
        byte[] encrypted = cipher.doFinal(fullMsg);
        
        return Base64.encodeBase64String(encrypted);
    }
    
    private int bytesToInt(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 24) |
               ((bytes[offset + 1] & 0xFF) << 16) |
               ((bytes[offset + 2] & 0xFF) << 8) |
               (bytes[offset + 3] & 0xFF);
    }
}
