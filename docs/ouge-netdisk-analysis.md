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

## 对 OneFilm 的结论

OneFilm 当前没有：

- `StorageDrive` 数据表
- `DriveActivity`
- Sardine/WebDAV 客户端
- Alist `api/public/path` 客户端
- `tvbox-drive://` 播放适配

因此当前导航里的「网盘」只能临时打开旧 `FileActivity`，这是本地文件选择器，不是网盘实现，必须替换。

## 建议接入顺序

1. 新建 `NetdiskDrive` 数据模型与本地存储（Room 或 SharedPreferences JSON），保存 `type/name/configJson`。
2. 实现 WebDAV 目录客户端：
   - 优先复用 Sardine；若不想加依赖，用 OkHttp 发 `PROPFIND`，解析 `multistatus`。
   - 支持 `url/initPath/username/password`。
3. 实现 Alist 目录客户端：
   - POST `{url}api/public/path`，请求体与欧歌一致。
   - 支持 `url/initPath/password`。
4. TV 端新增 `NetdiskScreen`：
   - 存储空间列表
   - 添加/编辑/删除 WebDAV、Alist
   - 文件夹浏览与返回
5. 文件点击接入现有播放链路：
   - WebDAV 需要把 Basic Authorization 传给播放器
   - Alist 需要保留 driveUrl 与路径，交给播放器解析
6. 移除 `AppFileLauncher` 对 `FileActivity` 的绑定，改为 `Routes.NETDISK`。

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
