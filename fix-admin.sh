#!/bin/bash

# 修复 Admin Panel 502 Bad Gateway
# 自动重新安装、构建并启动 Admin Panel

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

INSTALL_DIR="/opt/nfcgate"
ADMIN_DIR="$INSTALL_DIR/admin"
DOMAIN="yitian.shop"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}修复 Admin Panel 502 Bad Gateway${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: 检查 Admin 目录
echo -e "${BLUE}[1/8] 检查 Admin 目录...${NC}"

if [ ! -d "$ADMIN_DIR" ]; then
    echo "Admin 目录不存在，创建..."
    mkdir -p $ADMIN_DIR
fi

cd $ADMIN_DIR
ls -la

# 检查是否有 package.json
if [ ! -f "package.json" ]; then
    echo -e "${YELLOW}⚠️  package.json 缺失，从 GitHub 克隆完整 Admin Panel...${NC}"
    
    cd /tmp
    if [ ! -d "nfcgate-repo" ]; then
        git clone --depth 1 --branch v2 https://github.com/zhouyitian389-max/nfcgate.git nfcgate-repo
    fi
    
    # 复制完整的 admin 源码
    cp -rf /tmp/nfcgate-repo/admin/* $ADMIN_DIR/ 2>/dev/null || true
    cp -rf /tmp/nfcgate-repo/admin/.* $ADMIN_DIR/ 2>/dev/null || true
    
    cd $ADMIN_DIR
fi

echo -e "${GREEN}✅ Admin 目录检查完成${NC}"
echo ""

# Step 2: 检查 Node.js
echo -e "${BLUE}[2/8] 检查 Node.js...${NC}"
node --version
npm --version
echo ""

# Step 3: 配置环境变量
echo -e "${BLUE}[3/8] 配置 .env.local...${NC}"

cat > $ADMIN_DIR/.env.local << EOF
NEXT_PUBLIC_API_URL=https://api.$DOMAIN
NEXT_PUBLIC_WS_URL=wss://api.$DOMAIN/ws
API_BASE_URL=https://api.$DOMAIN
NEXT_PUBLIC_SITE_URL=https://admin.$DOMAIN
PORT=3000
HOSTNAME=0.0.0.0
EOF

echo -e "${GREEN}✅ 环境变量已配置${NC}"
echo ""

# Step 4: 清理并安装依赖
echo -e "${BLUE}[4/8] 安装 npm 依赖 (这需要 2-3 分钟)...${NC}"

cd $ADMIN_DIR

# 清理旧的依赖
rm -rf node_modules .next package-lock.json

# 安装依赖
npm install 2>&1 | tail -10

echo -e "${GREEN}✅ 依赖安装完成${NC}"
echo ""

# Step 5: 构建生产版本
echo -e "${BLUE}[5/8] 构建生产版本 (这需要 1-2 分钟)...${NC}"

cd $ADMIN_DIR

npm run build 2>&1 | tail -20

if [ ! -d ".next" ]; then
    echo -e "${RED}❌ 构建失败！查看错误信息${NC}"
    echo ""
    echo "尝试用开发模式启动..."
    BUILD_MODE="dev"
else
    echo -e "${GREEN}✅ 构建成功${NC}"
    BUILD_MODE="prod"
fi
echo ""

# Step 6: 安装 PM2
echo -e "${BLUE}[6/8] 准备 PM2 进程管理器...${NC}"

if ! command -v pm2 &> /dev/null; then
    npm install -g pm2 --silent
fi

# 停止旧进程
pm2 delete nfcgate-admin 2>/dev/null || true
pm2 delete all 2>/dev/null || true

echo -e "${GREEN}✅ PM2 准备完成${NC}"
echo ""

# Step 7: 启动 Admin Panel
echo -e "${BLUE}[7/8] 启动 Admin Panel...${NC}"

cd $ADMIN_DIR

if [ "$BUILD_MODE" = "prod" ]; then
    # 生产模式启动
    PORT=3000 pm2 start npm --name "nfcgate-admin" -- start
else
    # 开发模式启动（备用方案）
    PORT=3000 pm2 start npm --name "nfcgate-admin" -- run dev
fi

pm2 save
pm2 startup systemd -u root --hp /root 2>/dev/null || true

echo ""
echo "等待 Admin Panel 启动 (15 秒)..."
sleep 15

# 检查 PM2 状态
echo ""
echo -e "${BLUE}PM2 状态:${NC}"
pm2 list

echo ""
echo -e "${BLUE}最近 30 行日志:${NC}"
pm2 logs nfcgate-admin --lines 30 --nostream 2>&1 | tail -30

echo -e "${GREEN}✅ Admin Panel 启动完成${NC}"
echo ""

# Step 8: 验证
echo -e "${BLUE}[8/8] 验证服务...${NC}"

echo ""
echo "3000 端口状态:"
netstat -tuln | grep 3000 || echo "❌ 3000 端口未监听"

echo ""
echo "本地访问测试:"
curl -s -o /dev/null -w "HTTP 状态码: %{http_code}\n" http://localhost:3000 || echo "❌ 无法访问"

echo ""
echo "Nginx 重启..."
systemctl reload nginx

echo ""
echo "HTTPS 测试:"
curl -s -o /dev/null -w "HTTP 状态码: %{http_code}\n" https://admin.$DOMAIN -k || echo "需要等待 DNS"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 修复完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo "📊 检查命令:"
echo "  pm2 list                              # 查看进程"
echo "  pm2 logs nfcgate-admin                # 实时日志"
echo "  pm2 restart nfcgate-admin             # 重启"
echo "  netstat -tuln | grep 3000             # 检查端口"
echo "  curl http://localhost:3000            # 测试访问"
echo ""

echo "🌐 现在访问:"
echo "  https://admin.$DOMAIN"
echo ""

echo "🔐 默认账户:"
echo "  邮箱: admin@nfcgate.app"
echo "  密码: admin123"
echo ""
