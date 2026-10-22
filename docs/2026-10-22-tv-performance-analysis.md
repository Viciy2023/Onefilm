# TV 端卡顿分析与优化记录

- 日期：2026-10-22
- 设备：客厅 TV `192.168.1.22`（MiTV-ASTP0，armeabi-v7a，1920×1080）
- 模拟器：Android TV API28 x86

## 1. 日志证据

### Debug 包冷启动

- `EntryActivity` Displayed：约 +12s
- `ComposeMainActivity` Displayed：约 +5s
- 启动过程中 `Skipped 188 frames`
- 主线程长帧：约 3.1s；随后仍出现 1.5s 长帧
- Home 数据加载时出现 `VodConfig.load` / `Decoder.getJson` 调用

### Release 包冷启动

- `EntryActivity` Displayed：约 +5.1s
- `ComposeMainActivity` Displayed：约 +2.6s
- 热启动 `EntryActivity`：约 0.4–0.6s
- 启动期间仍有个别长帧（首次 Compose 布局 + 海报加载）

### 关键日志

- `AndroidAutoSize InitProvider` 反复探测旧 `android.support.v4.app.FragmentManager`，产生大量 `ClassNotFoundException` 日志。
- `LegacyWallpaper` 在 `CustomWallView` 中解码壁纸和计算 Palette。
- 首次 Home 数据依赖远程配置和站点首页请求，配置没有本地快速缓存。

## 2. 原因判断

1. **Debug 包是最大原因**
   Debug 包约 96MB，无 R8/资源压缩；Release 包约 45–65MB。低配 TV 上 dex 加载、类验证和 Compose 首次布局差距明显。

2. **启动阶段仍有主线程工作**
   - 壁纸 Drawable 解码和 Palette 取色在 `CustomWallView` 中同步执行。
   - 搜索/配置解析路径中有阻塞调用；搜索 `VodConfig.ensureLoaded()` 已移到 IO。

3. **海报图使用 `SubcomposeAsyncImage`**
   每个卡片都建立子组合，5 列海报网格首次加载和滚动时开销偏大；已改为 `AsyncImage`，关闭 crossfade。

4. **远程配置与首页数据没有快速缓存**
   冷启动需要先获取配置，再请求站点首页；TV 网络条件下会出现 10s 以上的加载空白。

5. **AutoSize 初始化日志**
   旧版 AndroidAutoSize 在 AndroidX Fragment 环境下反复探测 support-v4 类，增加启动噪声和类加载开销。

6. **欧歌流畅的原因**
   欧歌主要是原生 Activity/View + `TvRecyclerView`，走成熟 View 复用和系统焦点；Release 构建、配置/数据缓存更成熟。Compose 本身不是不能流畅，但不能用 Debug 包 + 子组合海报 + 主线程解码来对比。

## 3. 已实施优化

- 启动页等待 1800ms → 600ms。
- 详情自动播放延迟 350ms → 150ms。
- 搜索 `VodConfig.ensureLoaded()` 放入 `Dispatchers.IO`。
- 壁纸图片解码、Palette 取色移到后台线程。
- 海报 `SubcomposeAsyncImage` → `AsyncImage`，关闭 crossfade。
- 配置 JSON 增加本地缓存（10 分钟 TTL；手动加载配置时强制刷新）。
- 页面切换过渡关闭，减少旧页面停留。
- Release 构建已生成，使用 R8/资源压缩。

## 4. 仍需继续

- [ ] 安装并长期使用 **Release APK**；Debug 包不再作为 TV 体验包。
- [x] 首页/分类/详情/搜索增加内存/磁盘缓存，启动先显示上次内容再刷新。
- [ ] 评估移除 AndroidAutoSize InitProvider，仅在旧版 Activity 首次进入时初始化。
- [ ] 首页/分类海报预取（图片请求尺寸已限制为卡片尺寸）。
- [ ] 真机测量 Release 的页面切换、搜索、详情帧率。
- [ ] 分类连续切换时取消上一次网络请求，避免焦点快速移动造成并发加载。
- [x] 卡片缩放改为内部绘制层，选中放大不再触发整页 focus-scroll 位移。

## 5. 验证方式

```powershell
adb -s 192.168.1.22:5555 logcat -c
adb -s 192.168.1.22:5555 shell am force-stop com.onefilm.android.tv
adb -s 192.168.1.22:5555 shell am start -W -n com.onefilm.android.tv/com.onefilm.android.tv.ui.activity.EntryActivity
adb -s 192.168.1.22:5555 logcat -d -v time | Select-String 'Displayed|Skipped|longMsg|doFrame'
```

Release 测试包（debug keystore 签名，仅用于验证）：

- `Release/apk/leanback-armeabi-v7a-debugsigned-latest.apk`
- `Release/apk/leanback-x86-debugsigned.apk`
