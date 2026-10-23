# OneFilm 0.1.0 发布说明（测试版）

- 日期：2026-10-23
- 版本：`0.1.0`
- 许可证：GPL-3.0（见 `LICENSE.md`、`NOTICE`）
- 上游：FongMi/TV `fongmi` @ `0af3b31a1783b8fa4f0ce12e52ac9fad78ddfb1e`

## 安装包

| 端 | 路径 |
|---|---|
| TV（armeabi-v7a，debug key 签名，仅测试） | `Release/apk/leanback-armeabi-v7a-debugsigned-latest.apk` |
| TV（arm64-v8a） | `Release/apk/leanback-arm64_v8a.apk` |
| 手机 | `Release/apk/mobile-*.apk`（按实际构建产物） |

TV 最低 Android 9（API 28），推荐 1920×1080 及以上。

## 已完成

- Compose TV 首页、分类、搜索、详情、播放器、设置、收藏/历史、网盘。
- 首页/分类/详情/搜索持久化缓存；设置页可清除。
- 海报卡片选中放大 1.1，弹簧缓动，固定占位不推动整页。
- 主页分类向下进入导航“历史”；分类行显式左右焦点表。
- 全屏播放控件自动隐藏，隐藏态左右 5 秒 seek、确认暂停/继续。
- 详情页左上小窗播放器 + 右上信息/操作区。
- 历史/收藏进入自动聚焦首卡，详情返回恢复原卡。
- 搜索结果自动聚焦首卡，详情返回恢复原卡。

## 已知限制

- TV 真机全量 D-pad 矩阵尚未全部完成，重点剩余：全屏暂停后确认继续的复测、设置逐项焦点完整回归。
- 旧 TV `HomeActivity` 与部分旧 XML/Fragment 暂未删除，等待 SEARCH/SEND/VIEW 外部 Intent 迁移。
- 手机播放器手势、网络代理/Hosts/超时、声道切换、DLNA 真机、DV 真片、MPV 预加载/长稳未完成。
- Release 使用 debug keystore 签名，仅用于测试，不可作为正式商店包。
- 预编译 AAR/native 的再分发审计仍需在公开发布前完成。

## 验证命令

```powershell
$env:JAVA_HOME='C:\Users\Amy\.jdks\jbr-21.0.11'
.\gradlew.bat :compose:testDebugUnitTest :app:testLeanbackDebugUnitTest --offline
.\gradlew.bat :compose:connectedDebugAndroidTest --offline
.\gradlew.bat :app:assembleLeanbackRelease --offline
```
