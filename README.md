# WeChat OpenClaw Connector

企业微信 / 微信机器人接入 OpenClaw 的 Java 实现。

## 功能特性

- ✅ 企业微信消息接收与解密
- ✅ @机器人消息自动识别
- ✅ 转发到 OpenClaw AI 处理
- ✅ 会话上下文保持
- ✅ 群聊消息自动回复

## 技术栈

- Java 17
- Spring Boot 3.2
- Maven
- 企业微信官方加解密库

## 快速开始

### 1. 克隆项目

```bash
git clone https://github.com/YOUR_USERNAME/wechat-openclaw-connector.git
cd wechat-openclaw-connector
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

openclaw:
  gateway-url: "http://localhost:8081"
  agent-id: "main"
```

### 3. 下载企业微信 SDK

从 [企业微信官方文档](https://developer.work.weixin.qq.com/document/path/90320) 下载 Java 加解密库，放到 `src/main/java/com/example/wechat/util/` 目录。

### 4. 编译运行

```bash
mvn clean package
java -jar target/wechat-openclaw-connector-1.0.0.jar
```

### 5. 企业微信后台配置

- 应用管理 → 自建应用 → 创建应用
- 设置接收消息 URL: `https://你的域名/wechat/callback`
- 配置 Token 和 EncodingAESKey（与 application.yml 一致）

## 项目结构

```
wechat-openclaw-connector/
├── src/main/java/com/example/wechat/
│   ├── config/          # 配置类
│   ├── controller/      # 接口控制器
│   ├── service/         # 业务服务
│   ├── model/           # 数据模型
│   └── util/            # 工具类（含微信SDK）
├── src/main/resources/
│   └── application.yml  # 配置文件
├── pom.xml              # Maven配置
└── Dockerfile           # Docker镜像
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /wechat/callback | 微信服务器验证 |
| POST | /wechat/callback | 接收微信消息 |

## Docker 部署

```bash
docker build -t wechat-openclaw-connector .
docker run -p 8080:8080 wechat-openclaw-connector
```

## 注意事项

1. 需要开通企业微信「会话内容存档」功能才能接收外部群消息
2. 确保服务器能被公网访问（微信需要回调你的接口）
3. 生产环境建议使用 Redis 替换内存中的会话缓存

## License

MIT
