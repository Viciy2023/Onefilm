# OneFilm TV 性能基线

- 日期：2026-10-23
- 设备：MiTV-ASTP0，armeabi-v7a，1920×1080，Android 9（API 28）
- 包：Release + debug keystore 签名

## 启动

| 项 | 结果 | 来源 |
|---|---|---|
| EntryActivity Displayed（Release） | 约 5.1s | `2026-10-22-tv-performance-analysis.md` |
| ComposeMainActivity Displayed（Release） | 约 2.6s | 同上 |
| 热启动 | 约 0.4–0.6s | 同上 |
| Debug 冷启动 | 约 12s | 仅开发参考，不作为体验基线 |

## 当前优化状态

- 首页/分类/详情/搜索已接持久化缓存，冷启动优先显示上次内容。
- 海报使用 `AsyncImage`，关闭 crossfade；请求尺寸限制为 480×720。
- 配置 JSON 本地缓存 10 分钟；手动加载配置强制刷新。
- 卡片缩放只改绘制层，不触发布局/focus-scroll。
- 焦点滚动使用最小距离 `BringIntoViewSpec`。
- Release 已启用 R8、资源压缩。

## 待测量

- [ ] 首页滚动帧率 / 掉帧数。
- [ ] 分类快速切换与连续滚动的页面帧率。
- [ ] 搜索 434 站点并发时的内存峰值与首屏时间。
- [ ] 详情进入与播放器首帧时间。
- [ ] 弹幕高密度下的帧率。

> 本文件只记录已测量值；未测量项不填估算值。
