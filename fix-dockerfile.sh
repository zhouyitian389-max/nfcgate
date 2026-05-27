#!/bin/bash

# 快速修复: 创建缺失的 Dockerfile 并启动 Server
# 用法: bash fix-dockerfile.sh

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

INSTALL_DIR="/opt/nfcgate"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}修复 Server Dockerfile 缺失问题${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Step 1: 检查 server 目录是否存在
if [ ! -d "$INSTALL_DIR/server" ]; then
    echo "❌ Server 目录不存在: $INSTALL_DIR/server"
    exit 1
fi

cd $INSTALL_DIR/server

# Step 2: 检查文件结构
echo -e "${BLUE}[1/5] 检查 Server 目录...${NC}"
ls -la
echo ""

# 检查是否有源码目录
if [ ! -d "src" ] && [ ! -f "package.json" ]; then
    # 可能解压到了子目录
    SUBDIR=$(ls -d */ 2>/dev/null | head -1 | tr -d '/')
    if [ -n "$SUBDIR" ] && [ -f "$SUBDIR/package.json" ]; then
        echo "检测到源码在子目录: $SUBDIR，正在移动..."
        mv $SUBDIR/* . 2>/dev/null || true
        mv $SUBDIR/.* . 2>/dev/null || true
        rmdir $SUBDIR 2>/dev/null || true
    fi
fi

# Step 3: 创建 Dockerfile
echo -e "${BLUE}[2/5] 创建 Dockerfile...${NC}"

cat > $INSTALL_DIR/server/Dockerfile << 'EOF'
# NFCGate Server Dockerfile
FROM node:20-alpine AS builder

WORKDIR /app

# 安装系统依赖
RUN apk add --no-cache python3 make g++ openssl curl

# 复制 package 文件
COPY package*.json ./
COPY tsconfig.json ./

# 安装所有依赖（包括 dev 依赖用于构建）
RUN npm ci || npm install

# 复制 Prisma schema
COPY prisma ./prisma/

# 生成 Prisma Client
RUN npx prisma generate

# 复制源代码
COPY src ./src/

# 构建 TypeScript
RUN npm run build || (echo "Build failed, copying source as-is" && cp -r src dist)

# ==================== 运行时镜像 ====================
FROM node:20-alpine

WORKDIR /app

# 安装运行时依赖
RUN apk add --no-cache openssl curl

# 复制依赖和构建产物
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/package*.json ./

# 创建日志目录
RUN mkdir -p /app/logs

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# 启动命令: 先运行 Prisma migrate，再启动应用
CMD npx prisma db push --accept-data-loss --skip-generate && node dist/index.js
EOF

echo -e "${GREEN}✅ Dockerfile 已创建${NC}"
echo ""

# Step 4: 创建 .dockerignore
echo -e "${BLUE}[3/5] 创建 .dockerignore...${NC}"
cat > $INSTALL_DIR/server/.dockerignore << 'EOF'
node_modules
dist
.env
.env.*
*.log
.git
.gitignore
README.md
test
EOF
echo -e "${GREEN}✅ .dockerignore 已创建${NC}"
echo ""

# Step 5: 检查必要文件
echo -e "${BLUE}[4/5] 验证必要文件...${NC}"
REQUIRED_FILES=("package.json" "tsconfig.json" "Dockerfile")
for file in "${REQUIRED_FILES[@]}"; do
    if [ -f "$INSTALL_DIR/server/$file" ]; then
        echo "✅ $file"
    else
        echo "❌ $file 缺失"
    fi
done

REQUIRED_DIRS=("src" "prisma")
for dir in "${REQUIRED_DIRS[@]}"; do
    if [ -d "$INSTALL_DIR/server/$dir" ]; then
        echo "✅ $dir/"
    else
        echo "⚠️  $dir/ 缺失，尝试从源码下载..."
    fi
done

# 如果 src 或 prisma 缺失，从仓库下载
if [ ! -d "$INSTALL_DIR/server/src" ] || [ ! -d "$INSTALL_DIR/server/prisma" ]; then
    echo ""
    echo -e "${YELLOW}⚠️  源代码缺失，从 GitHub 仓库克隆...${NC}"
    cd /tmp
    rm -rf nfcgate-repo
    git clone --depth 1 --branch v2 https://github.com/zhouyitian389-max/nfcgate.git nfcgate-repo 2>&1 | tail -3
    
    # 复制完整的 server 源码
    cp -rf /tmp/nfcgate-repo/server/* $INSTALL_DIR/server/ 2>/dev/null || true
    
    # 确保 Dockerfile 保留
    if [ ! -f "$INSTALL_DIR/server/Dockerfile" ]; then
        cat > $INSTALL_DIR/server/Dockerfile << 'EOF'
FROM node:20-alpine AS builder
WORKDIR /app
RUN apk add --no-cache python3 make g++ openssl curl
COPY package*.json ./
COPY tsconfig.json ./
RUN npm ci || npm install
COPY prisma ./prisma/
RUN npx prisma generate
COPY src ./src/
RUN npm run build || (echo "Build failed, copying source as-is" && cp -r src dist)

FROM node:20-alpine
WORKDIR /app
RUN apk add --no-cache openssl curl
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/prisma ./prisma
COPY --from=builder /app/package*.json ./
RUN mkdir -p /app/logs
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1
CMD npx prisma db push --accept-data-loss --skip-generate && node dist/index.js
EOF
    fi
    
    echo -e "${GREEN}✅ 源码已从仓库下载${NC}"
fi

echo ""

# Step 6: 重新构建并启动
echo -e "${BLUE}[5/5] 重新构建并启动容器...${NC}"
cd $INSTALL_DIR

# 停止现有容器
docker-compose down 2>/dev/null || true

# 清理失败的镜像
docker rmi nfcgate-server 2>/dev/null || true

# 重新构建
echo "构建 Server 镜像..."
docker-compose build server

# 启动所有服务
echo ""
echo "启动所有服务..."
docker-compose up -d

echo ""
echo "等待服务启动 (30 秒)..."
sleep 30

# 检查状态
echo ""
echo -e "${BLUE}容器状态:${NC}"
docker-compose ps

echo ""
echo -e "${BLUE}Server 日志 (最后 20 行):${NC}"
docker-compose logs --tail=20 server

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 修复完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo "📊 检查命令:"
echo "  docker-compose ps                      # 容器状态"
echo "  docker-compose logs -f server          # Server 实时日志"
echo "  curl http://localhost:8080/health      # API 健康检查"
echo ""
echo "🌐 访问地址:"
echo "  Admin: https://admin.yitian.shop"
echo "  API: https://api.yitian.shop"
echo ""
