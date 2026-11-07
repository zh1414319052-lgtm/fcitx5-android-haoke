# 小企鹅输入法：浩科整合发行版

目标版本：0.1.3-haoke.2（Android versionCode 11302）。上游：Fcitx5 Android 0.1.3，提交 `048f581c652367567b8ee5c28c5163b805288895`。本版是独立维护版本，与原作者没有官方合作或背书关系。

本版修复语言切换时固定显示英文键盘的问题；韩文、日文假名、注音和僧伽罗文按键、Shift、预览和长按标签跟随对应引擎与配置。具体支持范围和历史 QA 验证见 [本版发行记录](RELEASE_0.1.3-haoke.2.md)。按用户明确要求，本次正式构建使用 `Build-Formal.ps1 -SkipTests`，未重跑单元测试、完整 lint 或设备测试；常规构建默认仍执行原检查，正式签名和产物身份校验不能跳过。

## 许可与归属

本整合程序按 **GPL-3.0-only** 发行，完整条款见根目录 `COPYING.HAOKE`。上游 `LICENSE`、各文件 SPDX、作者及第三方声明继续保留；这不把所有第三方文件重新授权成 GPL-3.0-only。应用中的“上游应用许可”和“整合发行许可”分别说明二者。新增更新客户端采用 GPL-3.0-only。

本版实际链接 Rime octagram（GPL-3.0-only），其 GPL 要求适用于整合发行。Sayura 源文件 `fcitx5-sayura/src/{sayura.cpp,sayura.h}` 明确为 **GPL-2.0-or-later**，此前清单的 GPL-2.0-only 标记已纠正；以实际固定源码为准。Anthy、中文附加组件、粤拼和 Unikey 的相关 GPL-2.0-or-later 部分在本次组合中选择 GPLv3。LGPL-2.1-only 的 libchewing 依据 LGPL 2.1 第 3 节允许的 GPL 转换路径组合；其原始源码及 LGPL 文本保留。其他 LGPL-2.1-or-later、LGPL-3.0-only/or-later 组件亦保留对应原许可及源码。Zstd 双许可选择 BSD-3-Clause，Marisa 双许可选择 BSD-2-Clause。其余依赖版权/许可见 `app/licenses` 和各固定源树，不能仅依据本文删除声明。

依据：GNU [许可兼容说明](https://www.gnu.org/licenses/license-compatibility.html)、[GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html)、[LGPL 2.1 第 3 节](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html)。这是本发行的工程许可记录，不替代法律意见。不得把上游名称、企鹅图案或本版名称用于暗示官方背书。

## 对应源码与重建

主工程及 Git 子模块包含本版修改、JNI、全部八项内置扩展与构建配置；`third-party/corresponding-source/manifest.json` 固定第三方预编译依赖的实际来源、递归子模块、字典原料、原始下载地址、字节数和 SHA-256。源包在同目录，包含 prebuilder 及递归子模块完整源树、Boost、原始词典/模型和 Bouncy Castle 源码。源包中的许可证和补丁保留。此目录是重建输入，不能作为缓存删除。

标准联网重建先获取主工程全部固定 Git 子模块。Java 21、Android SDK 36、Build Tools 36.0.0、NDK 28.0.13004108、CMake 3.22.1 以及 Windows ECM/host tools 需要单独安装。`Build-QA.ps1` 构建测试版；`Build-Formal.ps1 -Unsigned` 构建未签名正式配置，**不能作为正式安装包交付**。本机 `Build-Formal.ps1` 默认从外部受保护身份读取公开证书指纹，完成测试、四 ABI universal release、lint 和外部签名。实际签名私钥不随源码提供；第三方重建应使用自己的新包名、密钥和更新绑定，不能冒用本版稳定更新身份。

依赖预构建仓库固定提交：`86ce2c95d42f1132746fbf60c278193aa1f4b758`。对应 prebuilder：`eb156443de3b387089e51f9bd19df4e3ddce1732`。重建第三方库可联网检出该 prebuilder 并执行 `git submodule update --init --recursive`，按其 README、`build.cfg`、Shake/Cabal 配方和补丁构建；其依赖 CMake 3.31.6，不能与 Android 工程的 CMake 版本混淆。也可解压所附完整源包；无 `.git` 的快照在使用会执行 `git checkout .` 的配方前，需给每个源仓库初始化干净本地 Git 基线并提交源文件，保留 manifest 记录的真实上游提交溯源（不要把临时基线冒充上游提交）。字典/Boost 下载可用所附同名源包和配方 SHA-256 检验。已验证本工程四 ABI 使用固定预构建库编译；未声称在本机重新编译了每一个第三方静态库。

## 身份与更新边界

正式包 `org.fcitx.fcitx5.android.haoke`；原版包名、测试包名不同，能共存但不共享用户词库。正式签名 SHA-256：`a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3`。升级须保持包名/签名并递增 versionCode。不能覆盖原版迁移；请先从原版导出备份，再在本版导入。

只有设置 Activity 的首页/关于页可请求固定 HTTPS 版本源；输入服务不创建更新客户端。请求不携带输入内容、词库、剪贴板或设备标识；服务器仍能看到 IP/连接元数据。签名原始 payload 先经固定 Ed25519 公钥验证，再检查软件 ID、包名、通道、证书、有效期、架构、SDK、版本与防回滚状态。拒绝重定向、超限响应和身份不符内容。离开安全页面、开始编辑、切后台或销毁 Activity 时取消请求，结果不跨编辑状态弹窗。只提示并跳转版本页，不自动下载安装。网页和通知发布状态以根目录唯一交付说明为准。

## 设计资产

主题预览按卡片宽度铺满；主题图库使用紧凑示意布局，图片主题编辑保持真实键盘和裁剪比例。设置中的“键盘配色模式”提供跟随系统、手动日间和手动深色；沿用现有偏好，不重置自定义主题。跟随系统以手机外观配置为准，不根据时间推测。应用设置界面继续跟随系统外观。

`design/icon/penguin-keyboard-foreground-v1.png` 是实际 AI 生成的透明 PNG 原件，没有伪造矢量源文件；`tools/build_launcher_icon.py` 生成传统、自适应及单色启动图标。它不含文字。界面商用字体为 Noto Sans SC / Noto Sans CJK SC，以及本版加入的 Noto Sans Sinhala，均为 SIL OFL 1.1；保留源工程中的字体许可。原有全页面设计基准与原栈实现继续维护。
