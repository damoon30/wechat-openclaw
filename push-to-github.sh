#!/bin/bash
# GitHub 提交脚本

set -e

echo "=== WeChat OpenClaw Connector GitHub 提交脚本 ==="
echo ""

# 检查参数
if [ -z "$1" ]; then
    echo "用法: ./push-to-github.sh <你的GitHub用户名>"
    echo "例如: ./push-to-github.sh myusername"
    exit 1
fi

USERNAME=$1
REPO_NAME="wechat-openclaw-connector"

echo "Step 1: 初始化 Git 仓库..."
cd /root/.openclaw/workspace/$REPO_NAME
git init

echo ""
echo "Step 2: 添加所有文件..."
git add .

echo ""
echo "Step 3: 创建初始提交..."
git commit -m "Initial commit: WeChat OpenClaw Connector

- 企业微信消息接收与解密
- @机器人消息识别
- OpenClaw AI 集成
- 会话上下文管理
- 群聊自动回复"

echo ""
echo "Step 4: 添加 GitHub 远程仓库..."
git remote add origin "https://github.com/$USERNAME/$REPO_NAME.git" 2>/dev/null || true

echo ""
echo "==================================="
echo "下一步操作（请手动执行）："
echo "==================================="
echo ""
echo "1. 在 GitHub 上创建仓库:"
echo "   访问: https://github.com/new"
echo "   仓库名: $REPO_NAME"
echo "   选择 Public 或 Private"
echo "   不要勾选 Initialize with README"
echo ""
echo "2. 推送代码到 GitHub:"
echo "   git push -u origin main"
echo ""
echo "如果遇到权限问题，使用 Token 认证:"
echo "   git remote set-url origin https://<TOKEN>@github.com/$USERNAME/$REPO_NAME.git"
echo ""
echo "==================================="
