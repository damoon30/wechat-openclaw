package com.example.wechat.util;

import java.util.Arrays;

/**
 * PKCS7 填充工具
 */
public class PKCS7Encoder {
    
    private static final int BLOCK_SIZE = 32;
    
    /**
     * 解码（去除填充）
     */
    public static byte[] decode(byte[] decrypted) {
        int pad = decrypted[decrypted.length - 1];
        if (pad < 1 || pad > BLOCK_SIZE) {
            pad = 0;
        }
        return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
    }
    
    /**
     * 编码（添加填充）
     */
    public static byte[] encode(byte[] src) {
        int padLen = BLOCK_SIZE - (src.length % BLOCK_SIZE);
        byte[] padded = Arrays.copyOf(src, src.length + padLen);
        for (int i = src.length; i < padded.length; i++) {
            padded[i] = (byte) padLen;
        }
        return padded;
    }
}
