# WeChat OpenClaw Connector

企业微信 / 微信机器人接入 OpenClaw 的 Java 实现。

## 功能特性

- ✅ 企业微信消息接收与解密
- ✅ **@机器人消息自动识别**（支持内部群聊和外部群聊）
- ✅ **详细的 @提及解析**（UserID、用户名、@所有人、清理后的内容）
- ✅ 转发到 OpenClaw AI 处理
- ✅ 会话上下文保持
- ✅ 群聊消息自动回复

## 技术栈

- Java 17
- Spring Boot 3.2
- Maven
- 企业微信 AES-256-CBC 加解密

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/damoon30/wechat-openclaw.git
cd wechat-openclaw
```

### 2. 配置

复制 `application.yml.example` 为 `application.yml`，填入你的配置：

```yaml
wechat:
  corp-id: "你的企业ID"
  agent-id: 1000002
  secret: "你的Secret"
  token: "你的Token"
  encoding-aes-key: "你的EncodingAESKey"
  bot-user-id: "你的员工UserID"
  kf-id: ""  # 可选：客服账号ID（用于外部群聊备用方案）

openclaw:
  gateway-url: "http://localhost:8081"
  agent-id: "main"
```

### 3. 编译运行

```bash
mvn clean package
java -jar target/wechat-openclaw-connector-1.0.0.jar
```

## 企业微信后台配置

### 1. 创建自建应用

- 登录企业微信管理后台
- 应用管理 → 自建 → 创建应用
- 记录 `AgentID` 和 `Secret`

### 2. 配置接收消息

- 在应用详情页，点击 "接收消息" → "设置"
- URL: `https://你的域名/wechat/callback`
- Token: 随机生成，与配置一致
- EncodingAESKey: 随机生成，与配置一致

### 3. 获取 Bot UserID

- 通讯录 → 找到机器人对应的员工
- 查看详情，记录 **账号**（不是姓名）
- 填入 `wechat.bot-user-id`

## 外部群聊自动回复（重要）

### 前提条件

要在**外部群聊**（含客户/外部联系人的群）中实现自动回复，必须：

1. **开通会话内容存档**
   - 管理工具 → 会话内容存档
   - 按账号购买（约300-600元/账号/年）
   - 将机器人账号加入存档范围

2. **机器人进群**
   - 机器人必须在目标外部群里
   - 群设置 → 添加成员 → 选择机器人

3. **回调配置**
   - 会话内容存档设置中，配置回调URL
   - 回调地址与自建应用相同

### 限制说明

| 限制项 | 说明 |
|--------|------|
| 消息延迟 | 1-5分钟（会话内容存档推送延迟）|
| 48小时限制 | 客户48小时未互动，无法主动发送消息 |
| 权限要求 | 需要开通会话内容存档功能 |
| 发送方式 | 应用消息接口 |

### 外部群聊消息格式

```xml
<xml>
  <ToUserName><![CDATA[ww1234567890]]></ToUserName>
  <FromUserName><![CDATA[wmabcdef123]]></FromUserName>  <!-- 外部用户以 wm 开头 -->
  <CreateTime>1709834567</CreateTime>
  <MsgType><![CDATA[text]]></MsgType>
  <Content><![CDATA[@AI助手 请问怎么使用？]]></Content>
  <MsgId>1234567890123456</MsgId>
  <ChatId><![CDATA[wrc1234567890]]></ChatId>  <!-- 外部群聊ID -->
  <MentionedList>
    <Item><![CDATA[ZhangSan]]></Item>  <!-- 机器人UserID -->
  </MentionedList>
</xml>
```

## @提及详情解析

项目支持详细的 @信息解析：

```java
MentionInfo info = message.getMentionInfo();

info.isMentionedMe();        // 是否@了机器人
info.isMentionedAll();       // 是否@了所有人
info.getMentionedUserIds();  // 被@的UserID列表 ["ZhangSan", "LiSi"]
info.getMentionedUserNames(); // 被@的用户名 ["AI助手", "张三"]
info.getCleanContent();      // 去除@后的内容 "请问怎么使用？"
info.getMentionText();       // 原始@文本 "@AI助手"
info.getMentionCount();      // @提及数量
```

## Docker 部署

```bash
docker build -t wechat-openclaw .
docker run -p 8080:8080 wechat-openclaw
```

## 常见问题

### Q: 外部群聊收不到消息？
A: 检查是否：
1. 开通会话内容存档
2. 机器人在群里
3. 回调URL配置正确

### Q: 能收到消息但无法回复？
A: 可能是：
1. 客户超过48小时未互动
2. API接口无权限（错误码48002）
3. 不在群聊中（错误码60011）

### Q: 消息响应很慢？
A: 外部群聊有1-5分钟延迟（会话内容存档推送延迟），内部群聊实时

## License

MIT
