# Changelog

All notable changes to this project will be documented in this file.

## [4.1] - 2026-05-22

### 🧪 POS 真机测试基础设施（POS Testing Infrastructure）
- **Mock EMV POS 终端**（`tools/pos-mock/`）：模拟 Visa APPROVE / Mastercard DECLINE / TIMEOUT 三种交易场景
- **端到端测试 Runner**：自动化 A↔Relay↔B↔POS 全链路验证，生成 JUnit XML 报告
- **Android 仪器测试**（`androidTest/`）：HCE relay 模式、APDU 注入、超时与重试覆盖
- **真机测试清单**（`doc/pos-smoke-test.md`）：标准化的硬件验收 SOP（Step-by-Step）
- **CI 集成**：`pos-test.yml` workflow，PR 合入前自动跑 mock E2E

### 🔔 Firebase Cloud Messaging（推送通知）
- **AES-256 加密 Token 存储**：FCM token 存入 EncryptedSharedPreferences，密钥由 Android Keystore 派生
- **App A 推送**：POS 结果（Approved / Declined / Timeout）→ 推送到用户主设备
- **App B 推送**：A 端采集完成 → 推送「Capture ready, awaiting relay」
- **后端集成**：`firebase-admin` Python SDK，topic / device-token 双模式推送
- **配置文档**（`doc/fcm-setup.md`）：Firebase Console 配置、`google-services.json` 注入、env vars 说明

### 🌐 生产部署基础设施（Production Deployment）
- **Docker Compose 栈**：relay-server + upload-server + nginx + prometheus + grafana 一键启动
- **Nginx TLS 反向代理**：HTTP/2 + WebSocket upgrade + Let's Encrypt 自动续签
- **Google Cloud Run 脚本**：`deploy/cloud-run/`，含 Dockerfile + service.yaml + 一键脚本
- **Railway / Render 配置**：`railway.toml`、`render.yaml` 一键部署模板
- **Prometheus + Grafana 监控**：默认 dashboard（活动 session、APDU 速率、错误率、token 刷新）
- **运维手册**（`doc/deployment.md`）：Docker / Cloud Run / Railway / Render 四种部署模式 + TLS / 回滚 / 备份

### 📦 Version
- versionName: `4.1`
- versionCode: `19`
- app-reader versionCode: `3`
- app-hce versionCode: `3`

---

## [4.0] - 2026-05-22

### 🔴 P0 — 实时配对中继（A↔B Real-Time Relay）
- **WebSocket 中继服务**（`tools/relay-server/`）：Flask-SocketIO，支持 `/relay` 命名空间，Session 房间隔离
- **6 位配对码 + QR 扫码**：`POST /pair/create` 生成码+二维码，`POST /pair/redeem` App B 绑定，5 分钟 TTL，单次使用
- **App A `RelayClient`**：OkHttp3 WebSocket，自动重连（指数退避，最多 5 次），APDU 实时上行
- **App B `RelayReceiver`**：订阅 `apdu.from_reader`，Noise XX 解密，HCE 回放，POS 结果下行
- **POS E2E 测试脚本**（`tools/pos-e2e/`）：Mock A/B 客户端 + APDU fixture，CI job `pos-e2e.yml`
- **架构文档**（`doc/relay-architecture.md`）：Mermaid 序列图（配对、APDU 流、重连）
- 中继层仅见密文，明文 APDU 只在端侧解密（Noise XX 终止于 App B）

### 🟠 P1 — 账户系统（Account System）
- **用户注册/登录 API**：`POST /auth/register`、`POST /auth/login`、`POST /auth/refresh`、`POST /auth/logout`、`GET /auth/me`
- **JWT 鉴权**：access token 15 分钟 TTL（HS256），refresh token 30 天（存 SHA-256 哈希）
- **设备绑定**：`POST /devices/register`（SHA-256 device fingerprint），`GET /devices`
- **App A / App B 登录界面**：邮箱+密码，`AuthManager`（EncryptedSharedPreferences，自动 token 刷新），游客模式保留
- **数据库迁移**（`migrations/001_add_accounts.py`）：users / devices / refresh_tokens 表，sessions 表新增 user_id
- **Web 管理后台**（`/dashboard`，管理员专用）：用户/设备/会话/审计日志四个模块，CSV 导出，响应式 UI
- **账户系统文档**（`doc/account-system.md`）：认证流程 Mermaid 图，token 生命周期，设备绑定说明

### 🟡 P2 — 增强功能（Enhancement）
- **多设备管理**：每账号最多 10 台设备，`PATCH /devices/{id}`（重命名），`DELETE /devices/{id}`（解绑），App 内「我的设备」界面
- **FCM 推送通知**：A 采集完成 → 推送 B「Capture ready」；POS 结果 → 推送 A「Approved / Declined」；firebase-admin Python SDK
- **Rate Limiting**（Flask-Limiter）：register 5次/小时/IP，login 10次/15min/IP，pair/create 20次/小时/用户，全局 200次/min/IP，返回 429 + `Retry-After`
- **数据导出**：`GET /export/sessions?format=json|csv`，流式响应，PAN 永久脱敏，App 内「导出我的数据」→ Downloads 文件夹 + 分享
- **GDPR 账户删除**：`DELETE /auth/account`，会话匿名化（user_id→NULL，PAN 清除），设备/Token/FCM 全删，用户软删除（保留邮件哈希 30 天防滥用），审计日志保留
- **审计日志 UI 增强**：filter by 用户/操作类型/日期范围，分页（50条/页），管理员 CSV 导出，支持 10 种操作类型
- **通知文档**（`doc/notifications.md`）：FCM 配置指南，env vars，测试说明
- **GDPR 文档**（`doc/gdpr.md`）：数据保留策略，删除流程

### 📦 Version
- versionName: `4.0`
- versionCode: `18`
- app-reader versionCode: `2`
- app-hce versionCode: `2`

---

## [3.01] - 2026-05-22

### 🔴 Critical Fixes
- **EMVCache 线程竞态修复**：将双 `synchronized` 块合并为单个原子操作，彻底消除多线程并发解析时的缓存污染与重复计算问题
- **MIFARECacheManager TOCTOU 修复**：消除 `getBlock()` 中的检查-使用竞态条件，使用 `remove(key, value)` 原子删除过期条目

### 🟠 High Improvements
- **自动缓存清理**：MIFARECacheManager 新增后台守护线程（`MIFARE-Cache-Cleanup`），每 60 秒自动清理过期块缓存，并提供 `shutdown()` 生命周期管理
- **EMVParser 日志增强**：解析失败时记录数据长度与异常；解析超过 5ms 时输出慢解析警告
- **MIFAREParser 日志增强**：`readBlock()` 异常不再静默吞掉，改为日志记录

### 🟡 Medium Improvements
- **APDUAccumulator 整数溢出防护**：多字节 TLV 长度解析中增加 `Integer.MAX_VALUE >> 8` 溢出检查，防止恶意输入触发移位溢出
- **ValidationUtils（新增）**：新增包级输入验证工具类，提供 `validateApdu()`（最大 64KiB）和 `validateBlockData()`（最大 1KiB）

### 🔧 Build Fixes
- 移除失效的 dynamic feature modules（`:feature-emv` 等）引用
- 注释 `dynamicFeatures`，移除 `play:core` 依赖，修复 AGP 兼容性问题
- `DynamicModuleManager` 改为 no-op stub，消除编译错误

### ✅ New Tests
- `EMVCacheThreadSafetyTest`：8 线程并发验证缓存实例一致性
- `MIFARECacheCleanupTest`：TTL 过期、自动清理、shutdown 幂等性
- `ValidationUtilsTest`：null / 空 / 超长输入全覆盖
- `APDUAccumulatorEdgeCaseTest`：2 字节长度、截断、溢出路径

### 📦 Version
- versionName: `3.01`
- versionCode: `17`

---

## [0.3.0] - 2026-05-21

### Added
- Server-side automatic parsing for uploaded sessions, including EMV and MIFARE parsing outputs.
- EMV parser coverage for major brands (Visa, Mastercard, Amex, JCB, UnionPay).
- Optional end-to-end encryption between Reader and HCE using Noise XX with TOFU peer fingerprint confirmation.
- Audit logging for sensitive data access events.

### Changed
- PAN values are masked by default in parsed EMV output.
- PAN unmasking now requires an admin token.
- Plain HTTPS session upload remains supported for backward compatibility.
- E2EE upload is opt-in and remains disabled by default.

### Security
- Added Noise XX end-to-end encryption path for Reader↔HCE traffic protection independent of server-side TLS.
- Restricted PAN unmasking to authenticated admin-token requests and recorded unmask actions in audit logs.
