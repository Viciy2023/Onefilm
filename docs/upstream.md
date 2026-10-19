# OneFilm 上游同步与许可证

## 1. 上游基线

| 项 | 值 |
|---|---|
| 仓库 | `https://github.com/FongMi/TV` |
| 分支 | `fongmi` |
| 初始 commit | `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e` |
| commit 日期 | 2026-10-18 |
| 版本 | 5.6.3（`app/build.gradle`） |
| 许可证 | GPL-3.0 |

首版导入以该 commit 为准，不跟随移动的 `fongmi` HEAD。

## 2. 新仓库策略

- OneFilm 是新仓库，不是 GitHub Fork。
- 初始代码从上游 pin commit 导入；保留 `LICENSE`、版权头、`NOTICE`。
- 添加 `upstream` remote 指向 FongMi/TV。
- 新 UI/品牌/模块化改动只发生在 OneFilm 仓库。
- 上游 UI 不合并；只同步引擎与关键修复。

## 3. 同步范围

优先同步：

- `:catvod` 的站点/解析/兼容性修复。
- `:quickjs` 的运行时修复。
- `:chaquo` 的 Python 运行时修复。
- 播放器/解码/网络/安全相关的关键修复。
- 配置格式/多仓/直播源兼容性修复。

不同步：

- 上游 `app/src/leanback` / `app/src/mobile` 的 UI 改动。
- 上游品牌、图标、文案、配色。
- 与 OneFilm 设计系统冲突的资源。

如需上游 UI 的某个功能，先评估是否能在 `:core` 或 `:ui` 中独立实现，不直接 merge UI 分支。

## 4. 同步流程

```powershell
# 1. 获取上游
git fetch upstream fongmi

# 2. 查看差异（先看引擎模块）
git log --oneline HEAD..upstream/fongmi -- catvod chaquo quickjs

# 3. 只 cherry-pick 需要的提交，或按模块合并
git cherry-pick <sha>

# 4. 构建
.\gradlew.bat :app:assembleLeanbackDebug :app:assembleMobileDebug

# 5. 回归
# 按 docs/feature-matrix.md 跑配置、搜索、播放、直播、JS/Python 爬虫

# 6. 记录
# 更新本文件的“同步记录”
```

如果必须合并整棵上游树：

```powershell
git merge upstream/fongmi --allow-unrelated-histories
# 解决冲突时，UI 冲突优先保留 OneFilm；引擎冲突逐文件审查
```

## 5. 分支建议

```text
main                    # 可发布
feat/ui-*               # 新 UI 屏幕
chore/upstream-sync-*   # 上游同步
fix/*                   # 修复
```

- 每次上游同步单独开分支，不直接改 `main`。
- 同步分支必须通过完整构建 + 功能矩阵后再合并。

## 6. 许可证合规

- OneFilm 及其衍生作品继续使用 GPL-3.0。
- 保留所有上游版权头，不批量删除。
- 新增 UI 代码同样以 GPL-3.0 发布。
- 发布 APK 时同时公开对应源码 tag。
- `NOTICE` 至少包含：
  - FongMi/TV，GPL-3.0，仓库地址，初始 commit。
  - 其他第三方库/AAR/字体的许可证与来源。
- 不使用 Apple TV、Disney+、Netflix 的商标、图标、截图或受版权保护的素材。

## 7. 第三方与预编译产物审计

P0 必须审计：

- `app/libs/*.aar`：是否含专有代码、JNI、`com.fongmi` 引用、再分发限制。
- native `.so`：来源、许可证、ABI、是否可再分发。
- 字体/图标/图片：来源与授权。
- `catvod`/`quickjs`/`chaquo` 依赖：许可证与 NOTICE。

如发现不可再分发的 AAR/native，必须替换或移除；不要假设“上游能发我们就能发”。

## 8. 同步记录

| 日期 | 上游 commit | 范围 | 结果 | 备注 |
|---|---|---|---|---|
| 2026-10-18 | `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e` | 初始导入 | 待 P0 | 版本 5.6.3 |
