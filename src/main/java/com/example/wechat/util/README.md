# 企业微信加解密 SDK

本目录需要放置企业微信官方提供的 Java 加解密库文件。

## 下载地址

请从企业微信官方文档下载：
https://developer.work.weixin.qq.com/document/path/90320

## 需要的文件

下载后，将以下文件放入此目录：

- `WXBizMsgCrypt.java` - 主加解密类
- `ByteGroup.java` - 辅助类
- `PKCS7Encoder.java` - 辅助类
- `SHA1.java` - 辅助类
- `XMLParse.java` - XML 解析类

## 注意事项

1. 这些文件由腾讯企业微信官方提供
2. 请确保使用最新版本
3. 不要修改这些文件的代码

## 替代方案

如果无法获取官方 SDK，可以使用开源实现（自行评估风险）：
https://github.com/wechat-sdk/wechat-enterprise-sdk
