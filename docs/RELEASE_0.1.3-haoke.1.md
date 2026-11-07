# 0.1.3-haoke.1 对应产物与验收

- Android applicationId：`org.fcitx.fcitx5.android.haoke`；versionCode 11301。
- 正式 APK：114,571,552 字节；SHA-256 `9384afce1c9c73a22a1911505335714f3e04ae915a809fddd7b8782f1337f748`。
- 独立签名证书 SHA-256：`a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3`。
- 安装包入口：https://haoke.org/software/d000c19470bc74b6 。安装包由用户维护的既有入口分发，源码发布未代传 APK 或核验网盘文件。
- 对应标签：`haoke-v0.1.3.1`；上游基线 `048f581c652367567b8ee5c28c5163b805288895`。
- 整合发行 GPL-3.0-only，原作者/文件/组件许可保留。12 份对应源码/字典附件按 `third-party/corresponding-source/manifest.json` 核对。

最终构建的 37 项 JVM 单元测试通过，lint 0 errors / 260 warnings。真机为 Android16 ARM64，未使用模拟器。`UiScreensTest` 的六个针对性用例通过：

1. `themeGalleryPreviewFillsItsCard`：示意键盘填满卡片，所有行完整可见。
2. `imageThemePreviewFillsWidthAndRetainsCropAspect`：图片主题编辑保持完整宽度及实际键盘/裁剪比例。
3. `keyboardColorModesPersistAndReturnToSystem`：实点日间/深色/跟随系统与取消，处理旧版交叉配色槽，持久化/重建有效，旧槽值保留。
4. `themeConfigurationSurvivesActivityRecreation`：配置标签与主题在 Activity 重建后保持。
5. `storedThemeOptionsKeepTheirValuesWithNewDefaults`：已有主题偏好不被新默认值覆盖。
6. `largeKeyGlyphsRenderWithoutClipping`：大号数字、字母及中文按键位图不被裁切。

同源码正式签名包已正常同签升级，设备安装文件的 SHA-256 与上列一致。实际软键盘输入 hello + Enter、nihao → 你好通过；合成短语对话框取消，未保存记录。正式包实际检查线上签名 feed，显示当前版本无兼容新版。更新 modal/前后台/编辑取消专项在前一正式构建中 4/4 通过，QA5 全页面与引擎测试为此前验收，未把历史数量计成本次新增测试。

四 ABI 各 33 个原生库及依赖、64 位 ELF LOAD 至少 16KB、16KB ZIP 对齐、APK 完整性、v1/v2/v3 与限定 API34 签名校验通过。19,695 个编译/构建输入与实际构建副本逐文件哈希一致，清单在 `release-source-manifest.json`。设计/维护文档不作为编译输入；本次发布只补文档和分发归档规则，没有修改已验收的应用可执行代码或重签 APK。

测试结束恢复原键盘、输入子类型和剪贴板偏好，卸载 QA/测试程序，保留正式版。手机日间模式依用户明确要求保留，键盘配色为跟随系统；未改变全局语言、字体缩放和密度。

边界：Android14 未真机运行，其他三种 CPU 架构仅静态检查；没有逐项覆盖所有 OEM、横屏、外接键盘、放大字体与词库。固定第三方预编译库已用于构建，未声称在本机重建了每个第三方库；未执行全量安全审计。对应源码重建说明和许可依据见 `HAOKE_RELEASE.md`。
