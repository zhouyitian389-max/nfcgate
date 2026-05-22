# NFCGate 构建优化报告

## APK 大小

| 指标 | 优化前 | 优化后 | 改进 |
|------|-------|--------|------|
| Debug APK | 基线待测 | 优化后待测 | 通过 CI 输出 |
| Mapping 文件 | 无 | Release 构建产出 | ✅ |
| 资源压缩 | 关闭 | shrinkResources + split | ✅ |

## 编译时间

| 场景 | 基线 | 优化后 | 说明 |
|------|------|--------|------|
| Clean Build | 待执行 `scripts/benchmark-build.sh` | 待测 | 记录在脚本输出 |
| Incremental Build | 待测 | 待测 | 使用 touch 单文件方式 |
| Build Cache | 待测 | 待测 | 使用 `--build-cache` |

## 应用启动优化

- 新增 `NfcGateApplication` + `AppStartupManager`
- 关键组件（Room/Preferences）同步初始化
- 次要组件（证书信任管理）异步初始化
- Manifest 移除 `androidx.startup.InitializationProvider` 自动初始化入口

## 可交付物

1. `app/build.gradle`：启用 R8 + 资源压缩 + App Bundle split + dynamicFeatures
2. `app/proguard-rules.pro`：补充组件/Room/ViewModel/NFC 规则与日志裁剪规则
3. `scripts/`：`r8-check.sh`、`optimize-resources.sh`、`benchmark-build.sh`
4. `app/src/main/res/raw/keep.xml`：资源保留/丢弃规则
5. `feature-*`：四个动态功能模块骨架
6. `.github/workflows/build-optimization.yml`：CI 构建指标采集
