# NFCGate Server

Fastify + Prisma + Redis 后端 API。

## Quick Start

```bash
npm install
docker-compose up -d postgres redis
cp .env.example .env
npx prisma db push
npx prisma generate
npm run db:seed      # 创建管理员 admin@nfcgate.app / admin123
npm run dev          # → http://localhost:3000
```

## API Endpoints

### Auth
- `POST /auth/register` — 注册
- `POST /auth/login` — 登录 → { token, refreshToken }
- `POST /auth/refresh` — 刷新 token

### Cards
- `GET /cards` — 我的卡片列表
- `POST /cards` — 创建卡片
- `PUT /cards/:id` — 更新卡片
- `DELETE /cards/:id` — 删除卡片

### Devices
- `GET /devices` — 我的设备列表
- `POST /devices` — 注册设备
- `DELETE /devices/:id` — 删除设备

### Logs
- `GET /logs` — 我的操作日志

### Events (SSE)
- `GET /events` — Server-Sent Events 实时推送

### Admin (需要 ADMIN 角色)
- `GET /admin/users` — 用户列表
- `PATCH /admin/users/:id/role` — 修改角色
- `DELETE /admin/users/:id` — 删除用户
- `GET /admin/devices` — 所有设备
- `GET /admin/cards` — 所有卡片
- `GET /admin/logs` — 所有日志
- `GET /stats` — 统计数据

## Docker

```bash
docker-compose up --build
```
