#!/bin/bash

# NFCGate Server JWT 配置修复脚本
# 快速修复 JWT_REFRESH_SECRET 错误

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

INSTALL_DIR="/opt/nfcgate"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}NFCGate Server JWT 配置修复${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# ============= Step 1: 停止容器 =============
echo -e "${BLUE}[1/5] 停止 Server 容器...${NC}"
cd $INSTALL_DIR
docker-compose stop server 2>/dev/null || true
echo -e "${GREEN}✅ 完成${NC}"
echo ""

# ============= Step 2: 生成新密钥 =============
echo -e "${BLUE}[2/5] 生成 JWT 密钥...${NC}"

JWT_SECRET=$(openssl rand -base64 32)
JWT_REFRESH_SECRET=$(openssl rand -base64 32)

echo "JWT_SECRET: $JWT_SECRET"
echo "JWT_REFRESH_SECRET: $JWT_REFRESH_SECRET"
echo -e "${GREEN}✅ 完成${NC}"
echo ""

# ============= Step 3: 创建新的 .env 文件 =============
echo -e "${BLUE}[3/5] 更新环境变量文件...${NC}"

# 获取数据库密码（从 docker-compose.yml 中提取）
DB_PASS=$(grep "POSTGRES_PASSWORD:" $INSTALL_DIR/docker-compose.yml | sed 's/.*: //' | tr -d ' ')

# 如果没有找到，使用默认值
if [ -z "$DB_PASS" ]; then
    DB_PASS="nfcgate"
fi

# 创建新的 .env 文件
sudo tee $INSTALL_DIR/server/.env > /dev/null << EOF
# 数据库配置
DATABASE_URL="postgresql://nfcgate:$DB_PASS@postgres:5432/nfcgate"
REDIS_URL="redis://redis:6379"

# Server 配置
NODE_ENV=production
PORT=8080
HOST=0.0.0.0

# JWT 配置 (已修复)
JWT_SECRET=$JWT_SECRET
JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET
JWT_EXPIRY=900
JWT_REFRESH_EXPIRY=604800

# CORS 配置
CORS_ORIGIN="https://yitian.shop"
ALLOWED_ORIGINS="https://yitian.shop,https://admin.yitian.shop"

# SSL/TLS
HTTPS_ENABLED=true
SSL_CERT_PATH=/etc/letsencrypt/live/yitian.shop/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/yitian.shop/privkey.pem

# Logging
LOG_LEVEL=info
LOG_FORMAT=json

# Rate Limiting
RATE_LIMIT_WINDOW=60000
RATE_LIMIT_MAX_REQUESTS=100
RATE_LIMIT_WHITELIST_IPS=127.0.0.1

# Security
HELMET_ENABLED=true
HSTS_MAX_AGE=31536000
CSRF_PROTECTION=true

# 生产环境
DEBUG=false
PRODUCTION=true
EOF

sudo chown $USER:$USER $INSTALL_DIR/server/.env
echo -e "${GREEN}✅ 完成${NC}"
echo ""

# ============= Step 4: 重启所有容器 =============
echo -e "${BLUE}[4/5] 重启 Docker 容器...${NC}"

cd $INSTALL_DIR

# 确保所有容器都在运行
docker-compose up -d postgres redis

# 等待 PostgreSQL 就绪
echo "等待 PostgreSQL 就绪..."
sleep 10

# 启动 Server
docker-compose up -d server

# 等待容器启动
sleep 5

echo -e "${GREEN}✅ 完成${NC}"
echo ""

# ============= Step 5: 验证修复 =============
echo -e "${BLUE}[5/5] 验证修复...${NC}"

echo -e "\n📋 容器状态:"
docker-compose ps

echo -e "\n📝 环境变量检查:"
docker-compose exec -T server env | grep JWT || echo "无法获取环境变量"

echo -e "\n🔍 查看最新日志 (最后 30 行):"
docker-compose logs server | tail -30

echo ""

# 检查 Server 是否成功启动
SERVER_STATUS=$(docker-compose ps server | grep -c "running" || echo "0")

if [ "$SERVER_STATUS" -gt 0 ]; then
    echo -e "${GREEN}✅ Server 容器已启动${NC}"
    
    # 等待应用完全启动
    echo "等待应用启动..."
    sleep 5
    
    # 检查 API 健康状态
    API_HEALTH=$(curl -s http://localhost:8080/health 2>/dev/null || echo '{"status":"error"}')
    
    if echo "$API_HEALTH" | grep -q "ok"; then
        echo -e "${GREEN}✅ API 健康检查通过！${NC}"
    else
        echo -e "${YELLOW}⏳ API 正在初始化，请稍候...${NC}"
    fi
else
    echo -e "${YELLOW}⚠️  Server 容器启动中，请稍候...${NC}"
    echo "运行以下命令查看日志:"
    echo "cd $INSTALL_DIR && docker-compose logs -f server"
fi

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 修复脚本完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${BLUE}📝 修复信息:${NC}"
echo "JWT_SECRET 已重新生成"
echo "JWT_REFRESH_SECRET 已重新生成"
echo "环境变量文件已更新: $INSTALL_DIR/server/.env"
echo ""

echo -e "${BLUE}✅ 下一步:${NC}"
echo "1. 等待 30 秒让容器完全启动"
echo "2. 运行: cd $INSTALL_DIR && docker-compose logs server"
echo "3. 查看是否有新的错误"
echo "4. 访问: https://admin.yitian.shop"
echo ""

echo -e "${BLUE}🔧 常用命令:${NC}"
echo "查看 Server 日志: cd $INSTALL_DIR && docker-compose logs -f server"
echo "重启 Server: cd $INSTALL_DIR && docker-compose restart server"
echo "检查 API: curl http://localhost:8080/health"
echo "查看所有容器: cd $INSTALL_DIR && docker-compose ps"
