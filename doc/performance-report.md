# NFCGate 性能基准报告

本次改动为 `app-reader` 新增了三条可复用的性能路径：

- `EMVParser` + `EMVCache`
- `MIFAREParser` + `MIFARECacheManager`
- `APDUStreamProcessor` + `ChunkSizeOptimizer`

## 运行方式

```bash
./gradlew :app-reader:testDebugUnitTest --tests "de.tu_darmstadt.seemoo.nfcgate.reader.nfc.parsers.JmhBenchmarkTest"
```

CI 也会执行同一组 JVM benchmarks，因此可以直接在 Actions 日志里对比优化结果。

## EMVParser

| Benchmark | 路径 | 目标 |
|-----------|------|------|
| `parseSelectAIDResponse` | 单条 SELECT AID TLV 响应 | `< 5 ms/op` |
| `parseGpoResponse` | 单条 GPO 响应 | `< 5 ms/op` |
| `parseReadRecordResponse` | 单条 READ RECORD 响应 | `< 5 ms/op` |
| `parseSequentialApdus` | 连续解析 3 条 APDU | 对比缓存前后 |
| `parseWithCaching` | 80% 命中场景 | 接近缓存命中极限 |

优化点：

- 使用 `EMVCache` 按原始 APDU 内容进行 LRU 缓存
- TLV 解析支持嵌套 tag / 长度字段，避免重复构建中间对象
- `StreamingEMVParser` 使用预分配缓冲区合并分片 APDU

## MIFAREParser

| Benchmark | 路径 | 目标 |
|-----------|------|------|
| `readAllBlocksWithCache` | 64 blocks 命中缓存 | `64 blocks < 10 ms` |
| `readAllBlocksNoCacheFirst` | 64 blocks 首次读取 | 对比缓存收益 |
| `parseBlocks` | 1000 blocks 解析 | `1000 blocks < 1000 ms` |

优化点：

- 块级 TTL 缓存 + 扇区级 LRU 缓存
- `CompletableFuture` 并行读取 block
- `parseWithCache` 直接复用缓存块和扇区快照

## APDU 流式处理

| Benchmark | 路径 | 目标 |
|-----------|------|------|
| `processApduStream` | 64 / 128 / 256 / 512 / 1024 chunk 对比 | 找到最优 chunk size |

优化点：

- `APDUStreamProcessor` 以固定 chunk size 重组分片响应
- `APDUAccumulator` 仅在收到完整 SW1/SW2 后构建响应
- `ChunkSizeOptimizer` 自动输出延迟最低的 chunk size

## 当前推荐

- EMV APDU：优先走 `EMVParser.parseWithCache()`
- MIFARE blocks：优先走 `MIFAREParser.readAllBlocksParallel()`
- APDU 流式处理：优先使用 `ChunkSizeOptimizer.findOptimalChunkSize(...)` 的结果；默认起点为 `512B`

## 说明

由于当前沙箱无法访问 `dl.google.com`，本地会受制于 Android Gradle 依赖下载；代码、测试和 CI 步骤已经准备好，实际数值可以在具备完整网络访问的 CI 环境中直接产出。
