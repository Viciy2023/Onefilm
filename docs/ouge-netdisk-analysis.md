# 欧歌影视网盘实现分析

## 目标

- 安装包：`com.cctv.yangshipin.app.androidq`
- 版本：`6.1.2`（versionCode `169`）
- APK SHA-256：`1862a909da193b7f0b596a38afd192880aacd2248f9679e8646617decb8eec40`
- 分析方式：JADX/REA 静态反编译，未执行目标代码

## 入口与菜单

`com.github.tvbox.osc.ui.activity.DriveActivity` 是网盘入口。

- 列表数据来自 Room 的 `StorageDriveDao.getAll()`。
- 点击右上角添加按钮，弹出选择框，菜单项为：
  1. 本地目录
  2. WebDav
  3. Alist网页
- 选择结果处理在 `com.github.tvbox.osc.ui.activity.OooO0o.OooO00o`：
  - `LOCAL`：请求存储权限，打开文件夹选择器 `ChooserDialog`。
  - `WEBDAV`：打开 `com.androidx.m61` 对话框。
  - `ALISTWEB`：打开 `com.androidx.o0oOO` 对话框。

## 数据模型

`com.github.tvbox.osc.cache.StorageDrive`：

- `id: int`
- `name: String`
- `type: int`
- `configJson: String`

`StorageDrive` 通过 Room 持久化。`DriveFolderFile` 把一条存储记录包装成目录树节点，并保存当前路径、父目录、文件/目录标记、修改时间。

`xv0.OooO00o` 枚举只有三种类型：

- `LOCAL`
- `WEBDAV`
- `ALISTWEB`

## WebDAV

添加/编辑对话框：`com.androidx.m61`（布局 `dialog_webdav` 一类）。

字段：

- 空间名称
- WebDav 地址
- 初始路径 `initPath`
- 用户名
- 密码

保存时生成 JSON：

```json
{
  "url": "https://example.com/dav/",
  "initPath": "media",
  "username": "user",
  "password": "pass"
}
```

目录列表由 `com.androidx.h61` 实现：

- 使用 `com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine`
- `setCredentials(username, password)`
- `sardine.list(url + currentPath)` 获取 `DavResource` 列表
- 将目录/文件转换为 `DriveFolderFile`

播放时 `DriveActivity.OooOOo` 会读取 `getWebDAVBase64Credential()`，给播放器加：

```http
Authorization: Basic <base64(username:password)>
```

## Alist 网页

添加对话框：`com.androidx.o0oOO`。

字段：

- 空间名称
- Alist 网页地址
- 初始路径 `initPath`
- 密码

保存的 JSON：

```json
{
  "url": "https://alist.example.com/",
  "initPath": "media",
  "password": "pass"
}
```

目录列表由 `com.androidx.o0` 实现：

- 请求地址：`{url}api/public/path`
- POST JSON：

```json
{
  "path": "/",
  "password": "pass",
  "page_num": 1,
  "page_size": 30
}
```

- 返回结果转成 `DriveFolderFile`
- 播放时使用 `TVBOX_DRIVE` 系列 source key，并把 `driveUrl` 写入 `VodInfo`

## 本地目录

- `com.androidx.u30` 使用 Java `File.listFiles()` 列出目录
- 文件选中后走同一个 `tvbox-drive://` 播放路径
- 本地目录同样作为 `StorageDrive` 保存，而不是临时文件选择器

## 播放路径

`DriveActivity.OooOOo` 把选中文件包装成 `VodInfo`：

- `playFlag = "drive"`
- URL 形式：`tvbox-drive://<path>`
- WebDAV 额外带 Basic Authorization
- Alist 使用 `TVBOX_DRIVE` source key

## 改造前状态

改造前 OneFilm 没有：

- `StorageDrive` 数据表
- WebDAV 客户端
- Alist `api/public/path` 客户端
- 网盘播放适配

因此当时导航里的「网盘」临时打开了旧 `FileActivity`。本轮已替换为独立的 `Routes.NETDISK` 网盘页。

## OneFilm 已实现部分

- `StorageDrive` Room 实体与 `StorageDriveDao`，数据库版本 35 → 36，新增 `StorageDrive` 表。
- `AppNetdiskDataSource`：
  - WebDAV：OkHttp `PROPFIND` + XML 解析 + Basic Auth
  - Alist：`api/public/path` 列表，`api/fs/get` 解析 `raw_url`
- `NetdiskScreen`：
  - 网盘列表、添加/编辑/删除
  - WebDAV / Alist 类型切换
  - 文件夹进入与返回
  - 文件点击后解析并进入播放器
- 播放：WebDAV 直接播放 URL，并带 Basic Authorization header；Alist 使用 `raw_url` 直接播放。

## 当前限制

- Alist 已支持「加载更多」分页，每页 100 条。
- WebDAV PROPFIND 的根路径解析以常见服务器为准，反向代理/特殊根路径需要真机回归。
- 本地目录通过 SAF 文件夹选择器接入，视频以 `content://` 交给播放器；MPV 引擎兼容性需真机确认。
- 真实 WebDAV/Alist 服务器未在本轮环境完成端到端操作验证；已用本机 PROPFIND 测试服务验证响应格式，但手机端填写 URL 受输入法影响未完成自动化录入。

## 后续优化

1. WebDAV 特殊根路径、反向代理、302 跳转的真机回归。
2. 网盘播放历史、断点续播与封面。
3. 本地目录在 MPV 引擎下的兼容性回归。
4. 删除已废弃的 `FileLauncher` 残留代码。

## 证据

- `com.github.tvbox.osc.ui.activity.DriveActivity#init`
- `com.github.tvbox.osc.ui.activity.OooO0o#onClick`
- `com.github.tvbox.osc.ui.activity.OooO0o.OooO00o#OooO00o`
- `com.github.tvbox.osc.cache.StorageDrive`
- `com.androidx.m61#onCreate`
- `com.androidx.h61#OooO00o`
- `com.androidx.g61#run`
- `com.androidx.o0oOO#onViewCreated`
- `com.androidx.o0#OooO00o`
- `com.androidx.u30#OooO00o`
