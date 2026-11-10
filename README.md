# 小企鹅输入法 · 浩科优化版

Fcitx5 Android Haoke Edition — an independently maintained Android input method based on [Fcitx5 Android](https://github.com/fcitx5-android/fcitx5-android). Original authors, history and component licenses are retained. This is not an official upstream release.

[软件介绍与安装包 / App and downloads](https://haoke.org/software/d000c19470bc74b6) · [对应源码 Release / Source release](https://github.com/zh1414319052-lgtm/fcitx5-android-haoke/releases/tag/haoke-v0.1.3.3) · [上游说明](README.upstream.md)

## 0.1.3-haoke.3

新增离线九宫格拼音。在应用「输入法」列表点「＋」添加，使用地球键切换，长按地球键可直接选方案。使用内置 Rime 进行候选解码，支持退格、音节分隔以及数字、符号面板返回。切到英文、韩文、日文、注音或僧伽罗文时，键面显示对应字符。全键拼音、双拼、五笔、仓颉及自定义码表仍可选。

主包内置 RIME、粤拼、新酷音注音、日语 Anthy、韩语 Hangul、越南语 Unikey、僧伽罗语 Sayura 与 ClearURLs 链接净化，可在“语言与扩展”独立开关，关闭保留配置和学习数据。全页面原生界面涵盖 30 个功能页面与 16 类键盘面板/应用弹窗。

应用提供企鹅键盘图标和全宽主题预览。“主题 → 配置 → 键盘配色模式”支持跟随系统、手动日间和手动深色。跟随系统以手机外观为准，不按时钟判断，已有自定义主题不重置。中英文随系统首选语言，其他语言回退英文。

正式包名 `org.fcitx.fcitx5.android.haoke`，与上游原版和 QA 包共存。词库迁移需从原版导出、在本版导入。签名版本通知仅在设置首页/关于页前台安全状态检查，编辑、离页或后台时取消；输入服务不创建更新客户端，不上传输入、词库或剪贴板，不自动下载安装。服务端仍会看到 IP 等通常的连接信息。没有新增遥测、旧远程公告或后台轮询。

| 正式产物 | 值 |
| --- | --- |
| 版本 | 0.1.3-haoke.3 / versionCode 11303 |
| 系统 / 架构 | Android 6.0+ / API23；armeabi-v7a、arm64-v8a、x86、x86_64 |
| 大小 | 114,666,186 字节（109.35 MiB） |
| APK SHA-256 | `9144ae94592693641cb33fa0cb1e6741549a83bc8995ffc35042fd75f18e7e74` |
| 签名证书 SHA-256 | `a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3` |
| 上游基线 | 0.1.3 / `048f581c652367567b8ee5c28c5163b805288895` |

安装包由维护者通过上述浩科软件页面的既有入口分发，不附在源码 Release。后续正式升级需要相同包名、签名及更高 versionCode。

## 源码、构建与验证

整合发行采用 **GPL-3.0-only**，见 [COPYING.HAOKE](COPYING.HAOKE)。各文件、组件的原许可与作者声明保留，上游许可文本在 [LICENSE](LICENSE)。许可组合、重建步骤与签名边界见 [发行维护说明](docs/HAOKE_RELEASE.md)。

克隆时使用 `git clone --recurse-submodules` 并检出本版标签；GitHub 自动生成的源码 ZIP 不包含子模块。12 个固定第三方源码/字典归档以同版本 Release 附件提供，下载与校验说明见 [对应源码目录](third-party/corresponding-source/README.md)，版本、原地址和 SHA-256 见 [manifest](third-party/corresponding-source/manifest.json)。包含完整 prebuilder 递归源树、配方和补丁，不使用无法取得内容的 LFS 指针。

需要 Git、Java21、SDK36、BuildTools36.0.0、NDK28.0.13004108、CMake3.22.1 及相应 ECM/主机工具。Windows 构建入口为 `Build-QA.ps1`、`Build-Formal.ps1`；`Build-Formal.ps1 -Unsigned` 用于未签名重建。私钥、密码、网站授权码和机器运行数据库均不随源码发布。第三方重建应采用自己的包名、签名和更新绑定。

本版 42 项单元测试通过，正式配置 lint 为 0 错误、260 警告。设备验证和适用范围见 [本版发行记录](docs/RELEASE_0.1.3-haoke.3.md)。

四 ABI 各 33 个原生库、依赖、64 位 ELF 16KB LOAD、16KB ZIP 对齐及 API34 签名均已静态验证。Android14 没有真机运行证据，其他三种架构仅静态检查；未覆盖所有 OEM、横屏、外接键盘和所有词库。使用固定预编译库完成构建，未宣称在本机重新编译每个第三方库。

图标透明 PNG 原件、资源生成脚本及可编辑 UI 设计在 `design`。字体为 Noto Sans SC / Noto Sans CJK SC / Noto Sans Sinhala，SIL OFL1.1，允许商用。原栈为 Android Views / Fragment / Material。

## English

Version **0.1.3-haoke.3 (11303)** adds offline 9-key Pinyin. Add it in Input Methods and use the globe key to switch. It supports candidate selection, Backspace, syllable separation and returning from numbers or symbols. English, Hangul, Japanese kana, Zhuyin and Sinhala use their matching key layouts. Full-key Pinyin and the existing input methods, bundled extensions and themes remain available.

Signed version checks run only on safe foreground settings pages. They are canceled when editing, navigating away or backgrounding. The IME does not create this client; input, dictionaries and clipboard content are not uploaded, and updates are never installed automatically. Normal network connection metadata remains visible to the server. Export/import is required to migrate dictionaries from the upstream app.

Clone recursively and check out `haoke-v0.1.3.3`. Download all 12 pinned corresponding-source/data archives from the matching Release and verify them against the manifest linked above. The integrated distribution uses GPL-3.0-only, with original file/component notices retained. Source, build recipes, tests, design assets and font licenses are provided; signing keys and credentials are excluded. See the release-maintenance document for complete prerequisites and reconstruction limits.

All 42 unit tests passed and release lint reported zero errors and 260 warnings. See the [release record](docs/RELEASE_0.1.3-haoke.3.md) for emulator and artifact verification.