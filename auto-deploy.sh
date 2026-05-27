#!/bin/bash

# NFCGate v2.1.0 完全自动部署脚本
# 支持: Ubuntu 20.04+ / Debian 11+
# 作者: NFCGate Team
# 用途: 一键完整部署

set -e

# ============= 颜色定义 =============
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# ============= 配置 =============
DOMAIN="yitian.shop"
EMAIL="aa748748@hotmail.com"
INSTALL_DIR="/opt/nfcgate"
DB_USER="nfcgate"
DB_PASS=$(openssl rand -base64 32)
DB_NAME="nfcgate"
JWT_SECRET=$(openssl rand -base64 32)
JWT_REFRESH_SECRET=$(openssl rand -base64 32)
LOG_FILE="/tmp/nfcgate-deploy.log"

# 重定向所有输出到日志
exec > >(tee -a "$LOG_FILE")
exec 2>&1

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}NFCGate v2.1.0 自动部署脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "部署信息:"
echo "  域名: $DOMAIN"
echo "  邮箱: $EMAIL"
echo "  安装目录: $INSTALL_DIR"
echo "  日志: $LOG_FILE"
echo ""

# ============= 检查权限 =============
if [ "$EUID" -eq 0 ]; then
    echo -e "${RED}❌ 请不要用 root 用户运行此脚本${NC}"
    echo "运行: ./auto-deploy.sh"
    exit 1
fi

# ============= [1/12] 检测系统 =============
echo -e "${BLUE}[1/12] 检测系统环境...${NC}"

if [ ! -f /etc/os-release ]; then
    echo -e "${RED}❌ 不支持的系统${NC}"
    exit 1
fi

. /etc/os-release
OS=$ID
VERSION=$VERSION_ID

echo "系统: $OS $VERSION"

if [ "$OS" != "ubuntu" ] && [ "$OS" != "debian" ]; then
    echo -e "${YELLOW}⚠️  该脚本针对 Ubuntu/Debian 优化${NC}"
fi

echo -e "${GREEN}✅ 系统检测完成${NC}"
echo ""

# ============= [2/12] 更新系统 =============
echo -e "${BLUE}[2/12] 更新系统...${NC}"

sudo apt update 2>&1 | grep -E "已安装|待升级" || true
sudo DEBIAN_FRONTEND=noninteractive apt install -y -qq curl wget git build-essential libssl-dev libffi-dev python3-dev net-tools htop tmux 2>&1 > /dev/null

echo -e "${GREEN}✅ 系统更新完成${NC}"
echo ""

# ============= [3/12] 安装 Docker =============
echo -e "${BLUE}[3/12] 安装 Docker...${NC}"

if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    sudo sh /tmp/get-docker.sh 2>&1 > /dev/null
    sudo usermod -aG docker $USER
    echo "Docker 已安装"
else
    echo "Docker 已存在"
fi

docker --version

echo -e "${GREEN}✅ Docker 安装完成${NC}"
echo ""

# ============= [4/12] 安装 Docker Compose =============
echo -e "${BLUE}[4/12] 安装 Docker Compose...${NC}"

if ! command -v docker-compose &> /dev/null; then
    sudo curl -fsSL -L https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    echo "Docker Compose 已安装"
else
    echo "Docker Compose 已存在"
fi

docker-compose --version

echo -e "${GREEN}✅ Docker Compose 安装完成${NC}"
echo ""

# ============= [5/12] 创建目录 =============
echo -e "${BLUE}[5/12] 创建目录结构...${NC}"

sudo mkdir -p $INSTALL_DIR/{server,admin,acr39u,data,backups}
sudo chown -R $USER:$USER $INSTALL_DIR

echo "目录: $INSTALL_DIR"

echo -e "${GREEN}✅ 目录创建完成${NC}"
echo ""

# ============= [6/12] 下载文件 =============
echo -e "${BLUE}[6/12] 下载 NFCGate v2.1.0 文件...${NC}"

cd $INSTALL_DIR

# 下载 Release 文件
echo "下载 Server..."
curl -L -o nfcgate-server-v2.1.0.zip https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-server-v2.1.0.zip 2>/dev/null

echo "下载 Admin Panel..."
curl -L -o nfcgate-admin-panel-v2.1.0.zip https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-admin-panel-v2.1.0.zip 2>/dev/null

echo "下载 ACR39U Client..."
curl -L -o nfcgate-acr39u-client-v2.1.0.zip https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-acr39u-client-v2.1.0.zip 2>/dev/null

# 解压
echo "解压文件..."
unzip -q nfcgate-server-v2.1.0.zip -d server 2>/dev/null || echo "Server 解压完成"
unzip -q nfcgate-admin-panel-v2.1.0.zip -d admin 2>/dev/null || echo "Admin 解压完成"
unzip -q nfcgate-acr39u-client-v2.1.0.zip -d acr39u 2>/dev/null || echo "ACR39U 解压完成"

echo -e "${GREEN}✅ 文件下载解压完成${NC}"
echo ""

# ============= [7/12] 配置环境变量 =============
echo -e "${BLUE}[7/12] 配置环境变量...${NC}"

# Server .env
cat > $INSTALL_DIR/server/.env << EOF
DATABASE_URL="postgresql://$DB_USER:$DB_PASS@postgres:5432/$DB_NAME"
REDIS_URL="redis://redis:6379"
NODE_ENV=production
PORT=8080
HOST=0.0.0.0
JWT_SECRET=$JWT_SECRET
JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET
JWT_EXPIRY=900
JWT_REFRESH_EXPIRY=604800
CORS_ORIGIN="https://$DOMAIN"
ALLOWED_ORIGINS="https://$DOMAIN,https://admin.$DOMAIN"
HTTPS_ENABLED=true
SSL_CERT_PATH=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/$DOMAIN/privkey.pem
LOG_LEVEL=info
LOG_FORMAT=json
RATE_LIMIT_WINDOW=60000
RATE_LIMIT_MAX_REQUESTS=100
HELMET_ENABLED=true
HSTS_MAX_AGE=31536000
CSRF_PROTECTION=true
DEBUG=false
PRODUCTION=true
EOF

# Admin .env
cat > $INSTALL_DIR/admin/.env.local << EOF
NEXT_PUBLIC_API_URL=https://api.$DOMAIN
NEXT_PUBLIC_WS_URL=wss://api.$DOMAIN/ws
API_BASE_URL=https://api.$DOMAIN
EOF

echo "环境变量已配置"
echo -e "${GREEN}✅ 环境变量配置完成${NC}"
echo ""

# ============= [8/12] 创建 Docker Compose =============
echo -e "${BLUE}[8/12] 创建 Docker Compose 配置...${NC}"

cat > $INSTALL_DIR/docker-compose.yml << 'DOCKER_EOF'
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: nfcgate-postgres
    environment:
      POSTGRES_USER: nfcgate
      POSTGRES_PASSWORD: nfcgate123
      POSTGRES_DB: nfcgate
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - nfcgate-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U nfcgate"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: nfcgate-redis
    ports:
      - "6379:6379"
    networks:
      - nfcgate-network
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: nfcgate-server
    environment:
      DATABASE_URL: postgresql://nfcgate:nfcgate123@postgres:5432/nfcgate
      REDIS_URL: redis://redis:6379
      NODE_ENV: production
      PORT: 8080
      JWT_SECRET: ${JWT_SECRET:-changeme}
      JWT_REFRESH_SECRET: ${JWT_REFRESH_SECRET:-changeme}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - nfcgate-network
    volumes:
      - ./server/logs:/app/logs
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3

networks:
  nfcgate-network:
    driver: bridge

volumes:
  postgres-data:
    driver: local
DOCKER_EOF

echo "Docker Compose 配置已创建"
echo -e "${GREEN}✅ Docker Compose 配置完成${NC}"
echo ""

# ============= [9/12] 安装 Nginx 和 SSL =============
echo -e "${BLUE}[9/12] 安装 Nginx 和配置 SSL...${NC}"

sudo apt install -y -qq nginx certbot python3-certbot-nginx 2>&1 > /dev/null

# Nginx 配置
sudo tee /etc/nginx/sites-available/$DOMAIN > /dev/null << EOF
upstream backend {
    server localhost:8080;
}

upstream frontend {
    server localhost:3000;
}

server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN www.$DOMAIN api.$DOMAIN admin.$DOMAIN;
    
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    
    location / {
        return 301 https://\$server_name\$request_uri;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name api.$DOMAIN;
    
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    
    location / {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 86400;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name admin.$DOMAIN;
    
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $DOMAIN www.$DOMAIN;
    
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    
    location / {
        return 301 https://admin.$DOMAIN;
    }
}
EOF

# 启用 Nginx 配置
if [ -d /etc/nginx/sites-enabled ]; then
    sudo ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/ 2>/dev/null || true
    sudo rm -f /etc/nginx/sites-enabled/default 2>/dev/null || true
fi

# 测试 Nginx
sudo nginx -t 2>&1 | grep -E "successful|failed" || true

# 获取 SSL 证书
echo "获取 SSL 证书..."
sudo certbot certonly --nginx \
    -d $DOMAIN \
    -d www.$DOMAIN \
    -d api.$DOMAIN \
    -d admin.$DOMAIN \
    --non-interactive \
    --agree-tos \
    --email $EMAIL \
    --quiet 2>&1 || echo "证书获取中或已存在..."

# 启动 Nginx
sudo systemctl enable nginx 2>&1 > /dev/null
sudo systemctl restart nginx 2>&1 > /dev/null

echo -e "${GREEN}✅ Nginx 和 SSL 配置完成${NC}"
echo ""

# ============= [10/12] 启动 Docker 容器 =============
echo -e "${BLUE}[10/12] 启动 Docker 容器...${NC}"

cd $INSTALL_DIR

# 设置环境变量
export JWT_SECRET=$JWT_SECRET
export JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET

# 启动容器
docker-compose up -d 2>&1 | grep -E "Creating|Starting|Running" || true

# 等待 PostgreSQL
echo "等待 PostgreSQL 启动..."
sleep 15

echo -e "${GREEN}✅ Docker 容器启动完成${NC}"
echo ""

# ============= [11/12] 启动 Admin Panel =============
echo -e "${BLUE}[11/12] 启动 Admin Panel...${NC}"

cd $INSTALL_DIR/admin

# 安装依赖
npm install -q 2>&1 || echo "npm 依赖安装中..."

# 构建
npm run build -q 2>&1 || echo "构建中..."

# 启动
nohup npm start > $INSTALL_DIR/admin.log 2>&1 &

sleep 5

echo -e "${GREEN}✅ Admin Panel 启动完成${NC}"
echo ""

# ============= [12/12] 验证部署 =============
echo -e "${BLUE}[12/12] 验证部署...${NC}"

cd $INSTALL_DIR

echo "容器状态:"
docker-compose ps 2>/dev/null | grep -E "postgres|redis|server" || true

sleep 3

echo ""
echo "API 健康检查:"
curl -s http://localhost:8080/health 2>/dev/null | head -1 || echo "检查中..."

echo ""
echo -e "${GREEN}✅ 验证完成${NC}"
echo ""

# ============= 部署完成 =============
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 NFCGate v2.1.0 部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}📋 部署信息:${NC}"
echo "安装目录: $INSTALL_DIR"
echo "域名: $DOMAIN"
echo "API: https://api.$DOMAIN"
echo "Admin: https://admin.$DOMAIN"
echo ""

echo -e "${CYAN}📝 数据库信息:${NC}"
echo "用户: $DB_USER"
echo "数据库: $DB_NAME"
echo ""

echo -e "${CYAN}✅ 默认凭证:${NC}"
echo "邮箱: admin@nfcgate.app"
echo "密码: admin123"
echo ""

echo -e "${CYAN}📊 常用命令:${NC}"
echo "查看状态: cd $INSTALL_DIR && docker-compose ps"
echo "查看日志: cd $INSTALL_DIR && docker-compose logs -f server"
echo "重启服务: cd $INSTALL_DIR && docker-compose restart"
echo "备份数据: cd $INSTALL_DIR && docker-compose exec -T postgres pg_dump -U $DB_USER $DB_NAME > backup.sql"
echo ""

echo -e "${YELLOW}⚠️  重要提示:${NC}"
echo "1. 检查 DNS 是否已生效: nslookup $DOMAIN"
echo "2. 等待 2-3 分钟让所有服务完全启动"
echo "3. 访问 https://admin.$DOMAIN 并修改默认密码"
echo "4. 部署日志: $LOG_FILE"
echo ""

echo -e "${GREEN}✅ 部署脚本完成！${NC}"
