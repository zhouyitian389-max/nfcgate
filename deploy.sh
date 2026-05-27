#!/bin/bash

# NFCGate v2.1.0 完整部署脚本
# 用途: Linux 服务器完整部署 (Server + Web + USB + Android)
# 支持: Ubuntu 20.04 / 22.04 / CentOS 7+

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}NFCGate v2.1.0 完整部署脚本${NC}"
echo -e "${BLUE}========================================${NC}"

# ============= 配置变量 =============
DOMAIN="yitian.shop"
EMAIL="admin@yitian.shop"
INSTALL_DIR="/opt/nfcgate"
DB_USER="nfcgate"
DB_PASS=$(openssl rand -base64 32)
DB_NAME="nfcgate"
DB_PORT=5432
DOCKER_COMPOSE_VERSION="2.20.0"

echo -e "${YELLOW}配置信息:${NC}"
echo "域名: $DOMAIN"
echo "安装目录: $INSTALL_DIR"
echo "数据库用户: $DB_USER"
echo "数据库密码: [自动生成]"
echo ""

# ============= 1. 检测系统 =============
echo -e "${BLUE}[1/10] 检测系统环境...${NC}"

if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$ID
    VERSION=$VERSION_ID
    echo "系统: $OS $VERSION"
else
    echo -e "${RED}不支持的系统${NC}"
    exit 1
fi

# ============= 2. 安装依赖 =============
echo -e "${BLUE}[2/10] 安装系统依赖...${NC}"

if [ "$OS" = "ubuntu" ] || [ "$OS" = "debian" ]; then
    sudo apt update
    sudo apt install -y \
        curl wget git vim \
        build-essential \
        libssl-dev \
        libffi-dev \
        python3-dev \
        net-tools \
        htop \
        tmux
    
    # 安装 Docker
    echo -e "${YELLOW}安装 Docker...${NC}"
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $(whoami)
    
    # 安装 Docker Compose
    echo -e "${YELLOW}安装 Docker Compose...${NC}"
    sudo curl -L "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    
elif [ "$OS" = "centos" ] || [ "$OS" = "rhel" ]; then
    sudo yum update -y
    sudo yum groupinstall -y "Development Tools"
    sudo yum install -y \
        curl wget git vim \
        openssl-devel \
        libffi-devel \
        python3-devel \
        net-tools \
        htop \
        tmux
    
    # CentOS Docker 安装
    sudo yum install -y docker
    sudo systemctl start docker
    sudo systemctl enable docker
    sudo usermod -aG docker $(whoami)
    
    # Docker Compose
    sudo curl -L "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
fi

echo -e "${GREEN}✅ 依赖安装完成${NC}"

# ============= 3. 创建目录结构 =============
echo -e "${BLUE}[3/10] 创建目录结构...${NC}"

sudo mkdir -p $INSTALL_DIR/{server,admin,acr39u,data}
sudo chown -R $USER:$USER $INSTALL_DIR

echo -e "${GREEN}✅ 目录创建完成: $INSTALL_DIR${NC}"

# ============= 4. 下载 Release 文件 =============
echo -e "${BLUE}[4/10] 下载 v2.1.0 文件...${NC}"

cd $INSTALL_DIR

echo "下载 Server..."
curl -L -o nfcgate-server-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-server-v2.1.0.zip

echo "下载 Admin Panel..."
curl -L -o nfcgate-admin-panel-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-admin-panel-v2.1.0.zip

echo "下载 ACR39U Client..."
curl -L -o nfcgate-acr39u-client-v2.1.0.zip \
    https://github.com/zhouyitian389-max/nfcgate/releases/download/v2.1.0/nfcgate-acr39u-client-v2.1.0.zip

# 解压
echo "解压文件..."
unzip -q nfcgate-server-v2.1.0.zip -d server
unzip -q nfcgate-admin-panel-v2.1.0.zip -d admin
unzip -q nfcgate-acr39u-client-v2.1.0.zip -d acr39u

echo -e "${GREEN}✅ 文件下载完成${NC}"

# ============= 5. 配置环境变量 =============
echo -e "${BLUE}[5/10] 配置环境变量...${NC}"

# 生成 JWT Secret
JWT_SECRET=$(openssl rand -base64 32)
JWT_REFRESH_SECRET=$(openssl rand -base64 32)

# 创建 .env 文件
cat > $INSTALL_DIR/server/.env << EOF
# 数据库配置
DATABASE_URL="postgresql://$DB_USER:$DB_PASS@postgres:$DB_PORT/$DB_NAME"
REDIS_URL="redis://redis:6379"

# Server 配置
NODE_ENV=production
PORT=8080
HOST=0.0.0.0

# JWT 配置
JWT_SECRET=$JWT_SECRET
JWT_REFRESH_SECRET=$JWT_REFRESH_SECRET
JWT_EXPIRY=900
JWT_REFRESH_EXPIRY=604800

# CORS 配置
CORS_ORIGIN="https://$DOMAIN"
ALLOWED_ORIGINS="https://$DOMAIN,https://admin.$DOMAIN"

# SSL/TLS
HTTPS_ENABLED=true
SSL_CERT_PATH=/etc/letsencrypt/live/$DOMAIN/fullchain.pem
SSL_KEY_PATH=/etc/letsencrypt/live/$DOMAIN/privkey.pem

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

# Admin Panel .env
cat > $INSTALL_DIR/admin/.env.local << EOF
NEXT_PUBLIC_API_URL=https://api.$DOMAIN
NEXT_PUBLIC_WS_URL=wss://api.$DOMAIN/ws
API_BASE_URL=https://api.$DOMAIN
EOF

echo -e "${GREEN}✅ 环境变量配置完成${NC}"
echo "JWT Secret: $JWT_SECRET"
echo "DB Password: $DB_PASS"

# ============= 6. 创建 Docker Compose 配置 =============
echo -e "${BLUE}[6/10] 创建 Docker Compose 配置...${NC}"

cat > $INSTALL_DIR/docker-compose.yml << 'EOF'
version: '3.8'

services:
  # PostgreSQL 数据库
  postgres:
    image: postgres:15-alpine
    container_name: nfcgate-postgres
    environment:
      POSTGRES_USER: ${DB_USER:-nfcgate}
      POSTGRES_PASSWORD: ${DB_PASS:-password}
      POSTGRES_DB: ${DB_NAME:-nfcgate}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    networks:
      - nfcgate-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER:-nfcgate}"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # Redis 缓存
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

  # NFCGate Server
  server:
    build:
      context: ./server
      dockerfile: Dockerfile
    container_name: nfcgate-server
    environment:
      DATABASE_URL: postgresql://${DB_USER:-nfcgate}:${DB_PASS:-password}@postgres:5432/${DB_NAME:-nfcgate}
      REDIS_URL: redis://redis:6379
      NODE_ENV: production
      PORT: 8080
      JWT_SECRET: ${JWT_SECRET}
      CORS_ORIGIN: https://${DOMAIN}
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
EOF

echo -e "${GREEN}✅ Docker Compose 配置完成${NC}"

# ============= 7. 安装 Nginx 和 SSL =============
echo -e "${BLUE}[7/10] 安装 Nginx 和配置 SSL...${NC}"

if [ "$OS" = "ubuntu" ] || [ "$OS" = "debian" ]; then
    sudo apt install -y nginx certbot python3-certbot-nginx
elif [ "$OS" = "centos" ] || [ "$OS" = "rhel" ]; then
    sudo yum install -y nginx certbot python3-certbot-nginx
fi

# 创建 Nginx 配置
sudo tee /etc/nginx/sites-available/$DOMAIN > /dev/null << EOF
upstream backend {
    server localhost:8080;
}

upstream frontend {
    server localhost:3000;
}

# HTTP 重定向到 HTTPS
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

# HTTPS - API
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name api.$DOMAIN;
    
    # SSL 配置（稍后由 certbot 自动配置）
    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;
    
    # 安全头
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-XSS-Protection "1; mode=block" always;
    
    # WebSocket 支持
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

# HTTPS - Admin Panel
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
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}

# HTTPS - 主页重定向到 Admin
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
    sudo ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/
    sudo rm -f /etc/nginx/sites-enabled/default
fi

# 测试 Nginx 配置
sudo nginx -t

# 获取 SSL 证书
echo -e "${YELLOW}获取 Let's Encrypt SSL 证书...${NC}"
sudo certbot certonly --nginx \
    -d $DOMAIN \
    -d www.$DOMAIN \
    -d api.$DOMAIN \
    -d admin.$DOMAIN \
    --non-interactive \
    --agree-tos \
    --email $EMAIL

# 启动 Nginx
sudo systemctl enable nginx
sudo systemctl restart nginx

echo -e "${GREEN}✅ Nginx 和 SSL 配置完成${NC}"

# ============= 8. 启动 Docker 容器 =============
echo -e "${BLUE}[8/10] 启动 Docker 容器...${NC}"

cd $INSTALL_DIR

# 设置环境变量
export DB_USER=$DB_USER
export DB_PASS=$DB_PASS
export DB_NAME=$DB_NAME
export JWT_SECRET=$JWT_SECRET
export DOMAIN=$DOMAIN

# 启动容器
docker-compose up -d

# 等待 PostgreSQL 启动
echo "等待 PostgreSQL 启动..."
sleep 10

# 数据库迁移
echo "执行数据库迁移..."
docker-compose exec -T server npm run migrate

echo -e "${GREEN}✅ Docker 容器启动完成${NC}"

# ============= 9. 启动 Admin Panel =============
echo -e "${BLUE}[9/10] 启动 Admin Panel...${NC}"

cd $INSTALL_DIR/admin

# 安装依赖
npm install

# 构建生产版本
npm run build

# 启动 PM2 (如果已安装)
if command -v pm2 &> /dev/null; then
    pm2 start "npm start" --name "nfcgate-admin" --instances max
else
    # 否则用 nohup
    nohup npm start > admin.log 2>&1 &
fi

echo -e "${GREEN}✅ Admin Panel 启动完成${NC}"

# ============= 10. 最终配置 =============
echo -e "${BLUE}[10/10] 最终配置和验证...${NC}"

# 创建监控脚本
cat > $INSTALL_DIR/monitor.sh << 'EOF'
#!/bin/bash

echo "=== NFCGate 服务状态 ==="
echo ""
echo "Docker 容器:"
docker-compose ps

echo ""
echo "Nginx 状态:"
sudo systemctl status nginx

echo ""
echo "检查 API 健康状态:"
curl -s https://api.yitian.shop/health | jq .

echo ""
echo "检查 Admin 面板:"
curl -s -I https://admin.yitian.shop | head -3
EOF

chmod +x $INSTALL_DIR/monitor.sh

# 创建日志查看脚本
cat > $INSTALL_DIR/logs.sh << 'EOF'
#!/bin/bash

case $1 in
    server)
        docker-compose logs -f server
        ;;
    postgres)
        docker-compose logs -f postgres
        ;;
    redis)
        docker-compose logs -f redis
        ;;
    *)
        docker-compose logs -f
        ;;
esac
EOF

chmod +x $INSTALL_DIR/logs.sh

# 创建备份脚本
cat > $INSTALL_DIR/backup.sh << 'EOF'
#!/bin/bash

BACKUP_DIR="/opt/nfcgate/backups"
mkdir -p $BACKUP_DIR

echo "备份数据库..."
docker-compose exec -T postgres pg_dump -U nfcgate nfcgate | gzip > $BACKUP_DIR/nfcgate-$(date +%Y%m%d-%H%M%S).sql.gz

echo "备份完成！"
ls -lh $BACKUP_DIR/
EOF

chmod +x $INSTALL_DIR/backup.sh

echo -e "${GREEN}✅ 配置完成${NC}"

# ============= 部署完成总结 =============
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 NFCGate v2.1.0 部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

echo -e "${BLUE}📋 部署信息:${NC}"
echo "安装目录: $INSTALL_DIR"
echo "域名: $DOMAIN"
echo "API: https://api.$DOMAIN"
echo "Admin: https://admin.$DOMAIN"
echo ""

echo -e "${BLUE}📝 数据库信息:${NC}"
echo "用户: $DB_USER"
echo "密码: $DB_PASS"
echo "数据库: $DB_NAME"
echo ""

echo -e "${BLUE}🔐 JWT 密钥:${NC}"
echo "JWT Secret: $JWT_SECRET"
echo ""

echo -e "${BLUE}✅ 默认凭证:${NC}"
echo "邮箱: admin@nfcgate.app"
echo "密码: admin123"
echo ""

echo -e "${BLUE}📊 常用命令:${NC}"
echo "查看状态: cd $INSTALL_DIR && docker-compose ps"
echo "查看日志: cd $INSTALL_DIR && ./logs.sh"
echo "监控服务: cd $INSTALL_DIR && ./monitor.sh"
echo "备份数据: cd $INSTALL_DIR && ./backup.sh"
echo "停止服务: cd $INSTALL_DIR && docker-compose down"
echo "启动服务: cd $INSTALL_DIR && docker-compose up -d"
echo ""

echo -e "${BLUE}🌐 访问地址:${NC}"
echo "Admin 面板: https://admin.$DOMAIN"
echo "API: https://api.$DOMAIN"
echo ""

echo -e "${YELLOW}⚠️  重要提示:${NC}"
echo "1. 保存以上信息，特别是密钥信息"
echo "2. 检查防火墙规则 (80, 443 端口已开放)"
echo "3. 配置域名 DNS 指向服务器 IP"
echo "4. 等待 DNS 生效 (1-24 小时)"
echo "5. 访问 https://admin.$DOMAIN 验证部署"
echo ""

echo -e "${GREEN}✅ 部署脚本执行完成！${NC}"
EOF
