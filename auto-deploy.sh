#!/bin/bash

# NFCGate v2.1.0 完全自动部署脚本 (支持 root 用户)
# 支持: Ubuntu 20.04+ / Debian 11+

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
DB_PASS="nfcgate123"
DB_NAME="nfcgate"
JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
JWT_REFRESH_SECRET=$(openssl rand -base64 48 | tr -d '\n')
LOG_FILE="/tmp/nfcgate-deploy.log"

# ============= 自动检测 sudo =============
# 如果是 root 用户，不需要 sudo；否则使用 sudo
if [ "$EUID" -eq 0 ]; then
    SUDO=""
    CURRENT_USER="root"
    echo -e "${YELLOW}ℹ️  以 root 用户身份运行${NC}"
else
    SUDO="sudo"
    CURRENT_USER="$USER"
    echo -e "${YELLOW}ℹ️  以普通用户 $USER 身份运行 (使用 sudo)${NC}"
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}NFCGate v2.1.0 自动部署脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "部署信息:"
echo "  域名: $DOMAIN"
echo "  邮箱: $EMAIL"
echo "  安装目录: $INSTALL_DIR"
echo "  运行用户: $CURRENT_USER"
echo "  日志: $LOG_FILE"
echo ""

# ============= [1/12] 检测系统 =============
echo -e "${BLUE}[1/12] 检测系统环境...${NC}"

if [ ! -f /etc/os-release ]; then
    echo -e "${RED}❌ 不支持的系统${NC}"
    exit 1
fi

. /etc/os-release
echo "系统: $ID $VERSION_ID"
echo -e "${GREEN}✅ 系统检测完成${NC}"
echo ""

# ============= [2/12] 更新系统 =============
echo -e "${BLUE}[2/12] 更新系统和安装基础依赖...${NC}"

export DEBIAN_FRONTEND=noninteractive
$SUDO apt update -qq
$SUDO apt install -y -qq curl wget git unzip openssl ca-certificates gnupg lsb-release net-tools

echo -e "${GREEN}✅ 基础依赖安装完成${NC}"
echo ""

# ============= [3/12] 安装 Docker =============
echo -e "${BLUE}[3/12] 安装 Docker...${NC}"

if ! command -v docker &> /dev/null; then
    curl -fsSL https://get.docker.com -o /tmp/get-docker.sh
    $SUDO sh /tmp/get-docker.sh
    if [ "$EUID" -ne 0 ]; then
        $SUDO usermod -aG docker $USER
    fi
fi

$SUDO systemctl enable docker
$SUDO systemctl start docker
docker --version

echo -e "${GREEN}✅ Docker 安装完成${NC}"
echo ""

# ============= [4/12] 安装 Docker Compose =============
echo -e "${BLUE}[4/12] 安装 Docker Compose...${NC}"

if ! command -v docker-compose &> /dev/null; then
    COMPOSE_VERSION="v2.24.0"
    $SUDO curl -fsSL -L "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    $SUDO chmod +x /usr/local/bin/docker-compose
fi

docker-compose --version
echo -e "${GREEN}✅ Docker Compose 安装完成${NC}"
echo ""

# ============= [5/12] 安装 Node.js =============
echo -e "${BLUE}[5/12] 安装 Node.js 20...${NC}"

if ! command -v node &> /dev/null || [ "$(node -v | cut -d. -f1 | tr -d 'v')" -lt 18 ]; then
    curl -fsSL https://deb.nodesource.com/setup_20.x | $SUDO bash -
    $SUDO apt install -y -qq nodejs
fi

node --version
npm --version
echo -e "${GREEN}✅ Node.js 安装完成${NC}"
echo ""

# ============= [6/12] 创建目录 =============
echo -e "${BLUE}[6/12] 创建目录结构...${NC}"

$SUDO mkdir -p $INSTALL_DIR/{server,admin,acr39u,data,backups,logs}
$SUDO chown -R $CURRENT_USER:$CURRENT_USER $INSTALL_DIR

echo "目录: $INSTALL_DIR"
echo -e "${GREEN}✅ 目录创建完成${NC}"
echo ""

# ============= [7/12] 下载 v2.1.0 文件 =============
echo -e "${BLUE}[7/12] 下载 NFCGate v2.1.0 文件...${NC}"

cd $INSTALL_DIR

echo "下载 Server..."
curl -fsSL -o nfcgate-server-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-server-v2.1.0.zip

echo "下载 Admin Panel..."
curl -fsSL -o nfcgate-admin-panel-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-admin-panel-v2.1.0.zip

echo "下载 ACR39U Client..."
curl -fsSL -o nfcgate-acr39u-client-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-acr39u-client-v2.1.0.zip

echo "解压文件..."
rm -rf server admin acr39u
unzip -oq nfcgate-server-v2.1.0.zip -d server
unzip -oq nfcgate-admin-panel-v2.1.0.zip -d admin
unzip -oq nfcgate-acr39u-client-v2.1.0.zip -d acr39u

echo -e "${GREEN}✅ 文件下载完成${NC}"
echo ""

# ============= [8/12] 配置环境变量 =============
echo -e "${BLUE}[8/12] 配置环境变量...${NC}"

cat > $INSTALL_DIR/server/.env << EOF
DATABASE_URL=postgresql://$DB_USER:$DB_PASS@postgres:5432/$DB_NAME
REDIS_URL=redis://redis:6379
NODE_ENV=production
PORT=8080
HOST=0.0.0.0
JWT_SECRET=$JWT_SECRET
JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET
JWT_EXPIRY=900
JWT_REFRESH_EXPIRY=604800
CORS_ORIGIN=https://$DOMAIN
ALLOWED_ORIGINS=https://$DOMAIN,https://admin.$DOMAIN,https://api.$DOMAIN
LOG_LEVEL=info
LOG_FORMAT=json
RATE_LIMIT_WINDOW=60000
RATE_LIMIT_MAX_REQUESTS=100
HELMET_ENABLED=true
HSTS_MAX_AGE=31536000
DEBUG=false
PRODUCTION=true
EOF

cat > $INSTALL_DIR/admin/.env.local << EOF
NEXT_PUBLIC_API_URL=https://api.$DOMAIN
NEXT_PUBLIC_WS_URL=wss://api.$DOMAIN/ws
API_BASE_URL=https://api.$DOMAIN
EOF

# 保存凭证文件
cat > $INSTALL_DIR/CREDENTIALS.txt << EOF
================================
NFCGate v2.1.0 部署凭证
================================
部署时间: $(date)
域名: $DOMAIN

数据库:
  用户: $DB_USER
  密码: $DB_PASS
  数据库: $DB_NAME

JWT 密钥:
  JWT_SECRET: $JWT_SECRET
  JWT_REFRESH_SECRET: $JWT_REFRESH_SECRET

默认管理员账户:
  邮箱: admin@nfcgate.app
  密码: admin123

访问地址:
  Admin: https://admin.$DOMAIN
  API: https://api.$DOMAIN
================================
EOF

chmod 600 $INSTALL_DIR/CREDENTIALS.txt
echo -e "${GREEN}✅ 环境变量配置完成${NC}"
echo ""

# ============= [9/12] 创建 Docker Compose =============
echo -e "${BLUE}[9/12] 创建 Docker Compose 配置...${NC}"

cat > $INSTALL_DIR/docker-compose.yml << EOF
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    container_name: nfcgate-postgres
    environment:
      POSTGRES_USER: $DB_USER
      POSTGRES_PASSWORD: $DB_PASS
      POSTGRES_DB: $DB_NAME
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - nfcgate-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $DB_USER"]
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
    env_file:
      - ./server/.env
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
      - ./logs:/app/logs
    restart: unless-stopped

networks:
  nfcgate-network:
    driver: bridge

volumes:
  postgres-data:
    driver: local
EOF

echo -e "${GREEN}✅ Docker Compose 配置完成${NC}"
echo ""

# ============= [10/12] 安装 Nginx 和 SSL =============
echo -e "${BLUE}[10/12] 安装 Nginx 和配置 SSL...${NC}"

$SUDO apt install -y -qq nginx certbot python3-certbot-nginx

# Nginx 配置
$SUDO tee /etc/nginx/sites-available/$DOMAIN > /dev/null << NGINX_EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN www.$DOMAIN api.$DOMAIN admin.$DOMAIN;

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        return 301 https://\$host\$request_uri;
    }
}
NGINX_EOF

$SUDO ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/
$SUDO rm -f /etc/nginx/sites-enabled/default
$SUDO mkdir -p /var/www/html
$SUDO nginx -t && $SUDO systemctl restart nginx

# 获取 SSL 证书
echo "获取 SSL 证书..."
$SUDO certbot certonly --webroot -w /var/www/html \
    -d $DOMAIN -d www.$DOMAIN -d api.$DOMAIN -d admin.$DOMAIN \
    --non-interactive --agree-tos --email $EMAIL || \
    echo -e "${YELLOW}⚠️  SSL 证书获取失败，请检查 DNS 是否生效${NC}"

# HTTPS Nginx 配置
if [ -f /etc/letsencrypt/live/$DOMAIN/fullchain.pem ]; then
$SUDO tee /etc/nginx/sites-available/$DOMAIN > /dev/null << NGINX_EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN www.$DOMAIN api.$DOMAIN admin.$DOMAIN;
    location /.well-known/acme-challenge/ { root /var/www/html; }
    location / { return 301 https://\$host\$request_uri; }
}

server {
    listen 443 ssl http2;
    server_name api.$DOMAIN;
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location / {
        proxy_pass http://localhost:8080;
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
    server_name admin.$DOMAIN $DOMAIN www.$DOMAIN;
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=31536000" always;

    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
NGINX_EOF
    $SUDO nginx -t && $SUDO systemctl reload nginx
fi

$SUDO systemctl enable nginx
echo -e "${GREEN}✅ Nginx 配置完成${NC}"
echo ""

# ============= [11/12] 启动 Docker 容器 =============
echo -e "${BLUE}[11/12] 启动 Docker 容器...${NC}"

cd $INSTALL_DIR
docker-compose up -d --build

echo "等待容器启动..."
sleep 20

docker-compose ps
echo -e "${GREEN}✅ Docker 容器启动完成${NC}"
echo ""

# ============= [12/12] 启动 Admin Panel =============
echo -e "${BLUE}[12/12] 启动 Admin Panel...${NC}"

cd $INSTALL_DIR/admin

if [ -f package.json ]; then
    echo "安装 npm 依赖..."
    npm install --silent 2>&1 | tail -5

    echo "构建 Admin Panel..."
    npm run build 2>&1 | tail -5 || echo -e "${YELLOW}⚠️  构建有警告，继续...${NC}"

    # 安装 PM2
    if ! command -v pm2 &> /dev/null; then
        $SUDO npm install -g pm2 --silent
    fi

    # 停止旧进程
    pm2 delete nfcgate-admin 2>/dev/null || true

    # 启动
    pm2 start npm --name "nfcgate-admin" -- start
    pm2 save
    pm2 startup systemd -u $CURRENT_USER --hp $HOME 2>/dev/null || true
fi

sleep 5
echo -e "${GREEN}✅ Admin Panel 启动完成${NC}"
echo ""

# ============= 部署完成 =============
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 NFCGate v2.1.0 部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${CYAN}📋 部署信息:${NC}"
echo "  安装目录: $INSTALL_DIR"
echo "  Admin: https://admin.$DOMAIN"
echo "  API: https://api.$DOMAIN"
echo ""

echo -e "${CYAN}✅ 默认管理员账户:${NC}"
echo "  邮箱: admin@nfcgate.app"
echo "  密码: admin123"
echo ""

echo -e "${CYAN}🔐 凭证文件:${NC}"
echo "  $INSTALL_DIR/CREDENTIALS.txt"
echo ""

echo -e "${CYAN}📊 常用命令:${NC}"
echo "  容器状态: cd $INSTALL_DIR && docker-compose ps"
echo "  Server 日志: cd $INSTALL_DIR && docker-compose logs -f server"
echo "  Admin 日志: pm2 logs nfcgate-admin"
echo "  重启服务: cd $INSTALL_DIR && docker-compose restart"
echo ""

echo -e "${YELLOW}⚠️  下一步:${NC}"
echo "  1. 检查 DNS: nslookup $DOMAIN"
echo "  2. 访问: https://admin.$DOMAIN"
echo "  3. 登录后立即修改默认密码"
echo ""

echo -e "${GREEN}✅ 部署完成！${NC}"
