#!/bin/bash
# ============================================
# GitHub 推送脚本
# ============================================
# 用法: ./github-push.sh <你的GitHub用户名> [仓库名]
# 示例: ./github-push.sh myusername

set -e

USERNAME=${1:-}
REPO_NAME=${2:-wechat-openclaw-connector}

if [ -z "$USERNAME" ]; then
    echo "❌ 错误: 请提供 GitHub 用户名"
    echo ""
    echo "用法:"
    echo "  ./github-push.sh <你的GitHub用户名>"
    echo ""
    echo "示例:"
    echo "  ./github-push.sh john_doe"
    exit 1
fi

echo "=========================================="
echo "🚀 WeChat OpenClaw Connector GitHub 推送"
echo "=========================================="
echo ""
echo "GitHub 用户名: $USERNAME"
echo "仓库名: $REPO_NAME"
echo ""

# 检查 git 状态
cd "$(dirname "$0")"

echo "📋 Step 1: 检查 Git 状态..."
git status --short
if [ -n "$(git status --short)" ]; then
    echo "⚠️  有未提交的更改，先提交..."
    git add .
    git commit -m "update: $(date '+%Y-%m-%d %H:%M:%S')" || true
fi
echo "✅ Git 状态正常"
echo ""

# 添加远程仓库
echo "📡 Step 2: 配置远程仓库..."
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/$USERNAME/$REPO_NAME.git"
echo "✅ 远程仓库已配置: https://github.com/$USERNAME/$REPO_NAME.git"
echo ""

# 重命名分支为 main
echo "🌿 Step 3: 切换到 main 分支..."
git branch -M main 2>/dev/null || true
echo "✅ 当前分支: $(git branch --show-current)"
echo ""

# 推送
echo "⬆️  Step 4: 推送到 GitHub..."
echo "   正在推送代码..."

if git push -u origin main; then
    echo ""
    echo "=========================================="
    echo "🎉 推送成功！"
    echo "=========================================="
    echo ""
    echo "📎 仓库地址:"
    echo "   https://github.com/$USERNAME/$REPO_NAME"
    echo ""
    echo "📝 下一步:"
    echo "   1. 访问上面的链接查看代码"
    echo "   2. 下载企业微信 SDK 到 src/main/java/com/example/wechat/util/"
    echo "   3. 配置 application.yml"
    echo "   4. mvn clean package 编译运行"
    echo ""
else
    echo ""
    echo "=========================================="
    echo "❌ 推送失败"
    echo "=========================================="
    echo ""
    echo "可能的原因和解决方案:"
    echo ""
    echo "1. 仓库不存在"
    echo "   👉 访问 https://github.com/new 创建仓库 '$REPO_NAME'"
    echo "   ⚠️  不要勾选 Initialize with README"
    echo ""
    echo "2. 认证失败"
    echo "   👉 使用 Personal Access Token:"
    echo "      git remote set-url origin https://<TOKEN>@github.com/$USERNAME/$REPO_NAME.git"
    echo ""
    echo "   或者使用 SSH:"
    echo "      git remote set-url origin git@github.com:$USERNAME/$REPO_NAME.git"
    echo ""
    echo "3. 网络问题"
    echo "   👉 检查网络连接或尝试使用代理"
    echo ""
    exit 1
fi
