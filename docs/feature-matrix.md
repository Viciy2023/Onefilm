# OneFilm 功能矩阵

**状态**：Draft  
**用途**：记录 FongMi/TV 现有功能、TV/手机差异、新 UI 迁移阶段与回归验证方式。  
**原则**：任何功能在旧 UI 未删除前都算“保留”；新 UI 屏幕只有通过对应验证后才切换默认入口。

## 状态说明

- `legacy`：旧 FongMi UI 已可用，尚未迁移。
- `wip`：新 Compose UI 开发中。
- `done`：新 UI 通过回归。
- `n/a`：该端不支持。
- `TBD`：待 P0 盘点确认。

## 1. 启动与配置

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 冷启动 / Splash | legacy | legacy | P0 | 安装后冷启动，无崩溃 |
| 首次启动引导 | TBD | TBD | P0 | 清数据后启动 |
| 配置 URL 导入 | legacy | legacy | P1 | 导入 `https://tvbox.alibabagroup.de5.net/config.json` |
| 配置文件导入 | legacy | legacy | P1 | 本地 JSON 文件 |
| 二维码导入 | TBD | legacy | P1 | 手机扫码/相册识别 |
| 多仓入口 | legacy | legacy | P1 | 11 条多仓入口可用 |
| 站点切换 | legacy | legacy | P1 | 切换站点后首页/搜索正常 |
| 配置备份/恢复 | legacy | legacy | P3 | 导出/导入配置 |
| 应用更新 | legacy | legacy | P3 | 检查更新/下载/安装 |

## 2. 首页与浏览

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 首页推荐 | legacy | legacy | P1 | 多站点内容加载 |
| 分类/筛选 | legacy | legacy | P1 | 类型/地区/年份筛选 |
| 继续观看 | legacy | legacy | P1 | 播放后回首页显示进度 |
| 搜索 | legacy | legacy | P1 | 关键词、站点聚合、空结果 |
| 详情页 | legacy | legacy | P1 | 简介、剧集、线路、相关推荐 |
| 剧集列表 | legacy | legacy | P1 | 多线路/多集切换 |
| 收藏 | legacy | legacy | P2 | 添加/取消/跨站点 |
| 历史 | legacy | legacy | P2 | 记录、删除、继续播放 |
| 本地文件 | legacy | legacy | P3 | 文件浏览/播放 |
| SMB/WebDAV | legacy | legacy | P3 | 挂载/浏览/播放 |
| 推送/远程输入 | legacy | legacy | P3 | TV 推送链接 |
| DLNA 投屏 | legacy | legacy | P3 | 手机投 TV / TV 投手机 |

## 3. 播放

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 点播播放 | legacy | legacy | P1 | 多站点多格式 |
| 直播播放 | legacy | legacy | P2 | 频道切换、断流恢复 |
| EPG | legacy | legacy | P2 | 节目单/回看（如有） |
| 播放器 OSD | legacy | legacy | P2 | 播放/暂停/进度/选集/线路 |
| 解码切换 | legacy | legacy | P2 | 硬解/软解/IJK/MPV |
| 字幕 | legacy | legacy | P2 | 内嵌/外挂/搜索/切换 |
| 音轨 | legacy | legacy | P2 | 多音轨切换 |
| 倍速 | legacy | legacy | P2 | 0.5x–3x |
| 画面比例 | legacy | legacy | P2 | 16:9/4:3/填充/裁剪 |
| 弹幕 | legacy | legacy | P2 | 开关/透明度/速度/屏蔽词 |
| PiP | n/a | legacy | P2 | 手机画中画 |
| 后台播放 | TBD | TBD | P3 | 音频后台/通知控制 |
| 外部播放器 | legacy | legacy | P3 | 调用系统/第三方播放器 |
| 分享/打开链接 | legacy | legacy | P3 | ACTION_VIEW/SEND |

## 4. 直播

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 直播列表 | legacy | legacy | P2 | 分组/频道 |
| 频道搜索 | legacy | legacy | P2 | 关键词 |
| 收藏频道 | legacy | legacy | P2 | 添加/取消 |
| 频道切换 | legacy | legacy | P2 | 上下键/数字键 |
| 节目单 | legacy | legacy | P2 | EPG 加载 |
| 时移/回看 | TBD | TBD | P2 | 源支持时验证 |
| 直播弹幕 | TBD | TBD | P2 | 源支持时验证 |

## 5. 资料库

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 历史 | legacy | legacy | P2 | 列表/删除/继续 |
| 收藏 | legacy | legacy | P2 | 列表/删除/打开 |
| Keep | legacy | legacy | P2 | 收藏/追剧 |
| 文件夹 | n/a | legacy | P3 | 本地/远程目录 |
| 扫码 | n/a | legacy | P3 | 二维码扫描 |
| 生物识别 | n/a | legacy | P3 | 启动/设置锁 |

## 6. 设置

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| 播放器设置 | legacy | legacy | P3 | 内核/解码/渲染 |
| 解码设置 | legacy | legacy | P3 | 硬解/软解/线程 |
| 预加载设置 | legacy | legacy | P3 | 开关/阈值 |
| 弹幕设置 | legacy | legacy | P3 | 开关/样式 |
| 主题/语言 | legacy | legacy | P3 | 切换/重启 |
| 网络/代理/DoH | legacy | legacy | P3 | 代理/DoH/IPv6 |
| 备份/恢复 | legacy | legacy | P3 | 配置/数据 |
| 关于/更新 | legacy | legacy | P3 | 版本/检查更新 |

## 7. 系统集成

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| Leanback Launcher | legacy | n/a | P0 | TV 桌面图标/横幅 |
| 开机启动 | legacy | n/a | P3 | 重启后自启 |
| 手机快捷方式 | n/a | legacy | P3 | 长按菜单 |
| 通知 | legacy | legacy | P3 | 播放通知/更新 |
| FileProvider | legacy | legacy | P0 | 分享/安装/文件 |
| 搜索 Intent | legacy | legacy | P3 | 系统搜索 |
| 分享 Intent | legacy | legacy | P3 | 文本/视频链接 |
| 外部视频打开 | legacy | legacy | P3 | ACTION_VIEW |
| 本地 HTTP API | TBD | TBD | P3 | 局域网接口 |

## 8. 引擎与源

| 功能 | TV | 手机 | 新 UI 阶段 | 验证方式 |
|---|---|---|---|---|
| Java 爬虫 | legacy | legacy | 不变 | 站点加载/搜索/播放 |
| JS 爬虫（QuickJS） | legacy | legacy | 不变 | JS 站点 |
| Python 爬虫（Chaquopy） | legacy | legacy | 不变 | Python 站点 |
| 解析/嗅探 | legacy | legacy | 不变 | 多解析线路 |
| 多仓/本地仓 | legacy | legacy | 不变 | 11 条入口 |
| 代理/DoH/IPv6 | legacy | legacy | 不变 | 网络设置 |
| 播放器内核 | legacy | legacy | 不变 | Media3/IJK/MPV |

## 9. 新 UI 迁移顺序

1. **P0**：应用身份、包名、构建、模块骨架、旧 UI fallback。
2. **P1**：Home、Search、Detail。
3. **P2**：Player OSD、Live/EPG、History/Favorites。
4. **P3**：Settings、Folder/Scan、DLNA/Cast、备份/更新。
5. **P4**：性能、无障碍、TV 焦点、手机手势、发布。

## 10. 回归基线

- 配置：`https://tvbox.alibabagroup.de5.net/config.json`
- 局域网：`http://192.168.1.20:8899/config.json`
- 多仓：`https://tvbox.alibabagroup.de5.net/tvbox/本地仓库.json`
- 设备：Android 9+ TV、Android 16 手机、Hi3798mv100 低配盒子（兼容性参考）。
- 每次发布前至少跑：冷启动、配置导入、首页、搜索、详情、播放、直播、收藏/历史、设置、返回键、D-pad、PiP（手机）、DLNA（如可用）。
