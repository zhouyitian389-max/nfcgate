#!/bin/bash
# NFCGate v2.1.0 Fully Automated Release Script

set -e

echo "🚀 NFCGate v2.1.0 全自动发布"
echo "=================================================="
echo ""

# 验证 Git
if ! command -v git &> /dev/null; then
    echo "❌ Git 未安装"; exit 1
fi

# 验证仓库
if [ ! -d ".git" ]; then
    echo "❌ 不在 Git 仓库目录"; exit 1
fi

echo "✅ 环境检查完成"
echo ""

# 配置 Git
git config --global user.name "zhouyitian389-max" 2>/dev/null || true
git config --global user.email "zhouyitian389@gmail.com" 2>/dev/null || true
echo "✅ Git 用户已配置"
echo ""

# 切换分支
git fetch origin v2 2>/dev/null || true
git checkout v2 2>/dev/null || true
echo "✅ 已切换到 v2 分支"
echo ""

# 清理旧 tag
if git rev-parse v2.1.0 >/dev/null 2>&1; then
    git tag -d v2.1.0 2>/dev/null || true
    git push origin --delete v2.1.0 2>/dev/null || true
    echo "✅ 已清理旧 tag"
    echo ""
fi

# 创建 tag
git tag -a v2.1.0 -m "v2.1.0: Enhanced error handling & logging"
echo "✅ Tag v2.1.0 已创建"
echo ""

# 推送 tag
echo "⏳ 正在推送 tag 到 GitHub..."
git push origin v2.1.0 --force
echo "✅ Tag 已推送"
echo ""

echo "=================================================="
echo "✨ v2.1.0 自动发布已启动！"
echo ""
echo "🔗 监控构建："
echo "   https://github.com/zhouyitian389-max/nfcgate/actions"
echo ""
echo "📦 查看 Release："
echo "   https://github.com/zhouyitian389-max/nfcgate/releases/tag/v2.1.0"
echo ""
echo "⏱️  预计完成：25-35 分钟"
echo "=================================================="
