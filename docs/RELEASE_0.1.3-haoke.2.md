# 0.1.3-haoke.2 — 语言键盘修复

2026-11-07。正式包 `org.fcitx.fcitx5.android.haoke`，versionCode **11302**，沿用证书 `a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3`，可覆盖升级此前浩科正式版。

修复切换语言后按键仍显示英文字母：韩语显示韩文字母，日语显示假名，注音显示注音字符，僧伽罗语显示对应字母；Shift、按下预览与长按选字同步。符号面板返回、收起重开恢复当前语言；英文、拼音、粤拼和 Telex 保持字母输入。保留八项内置扩展、主题配色及原正式签名，可覆盖升级。自定义 Rime 和部分特殊注音布局仍使用原字母布局。

## 适配范围

- 韩语：按实际引擎配置支持 8 种非 Romaja 布局；默认二套式含 Shift 双辅音。Romaja 保持字母输入。
- 日语：触屏直接使用 Anthy 原生假名 keysym，包含浊音/半浊音，跟随平假名、片假名、半角片假名及字母子模式；保留实体键盘输入配置和原引擎组词转换。
- 注音：Default、IBM、Gin-Yieh、ETen 四种直接布局，含数字行注音和声调键。
- 僧伽罗语：Sayura 对应字符，使用 Noto Sans Sinhala（SIL OFL 1.1，可商用）。
- 自定义 Rime 方案和其他特殊 Chewing 布局仍回退原字母键盘；不宣称适配任意布局。

## 构建与验证边界

用户明确要求“做成正式版无需测试同步更新github源码和网站相关资料”。本轮使用 `Build-Formal.ps1 -SkipTests`，跳过单元、完整 lint、模拟器和真机运行测试。Gradle release 打包自带的 lintVital 完成；其结果不等于完整 lint 或运行测试。

本次正式包构建成功，非 debuggable、包名/版本/既有正式证书一致，ZIP 完整，四 ABI 各 33 个原生库及依赖、64 位 ELF 16 KB LOAD、16 KB ZIP 对齐、v1/v2/v3 与 API34 签名校验通过。没有更换密钥、增加输入服务联网、权限或后台服务。

已有 QA6 证据（同一功能实现，区别在正式配置和版本）：42/42 单元测试，完整 lint 0 errors / 255 warnings；OPPO PKG110 / Android16 ARM64 的 3/3 专项通过。实际 IME 点按输入 `한글`、Shift `까`、`にほん`、`が`、`你好`、`ක`、`hello`，语言地球键、符号面板返回和收起重开通过。长按通过已挂载 KeyView 的真实控件事件、弹层和引擎输入 `빠`，系统级长按注入未送达按键，仍未验证物理手指长按。QA 测试包已卸载、原输入法已恢复；本轮未操作手机。

其他架构仅静态核对；未覆盖全部 OEM、横屏、所有字号、主题和特殊输入方案。没有把上一轮 QA 记录当成本次正式包重新测试。

## 产物与对应源码

- 大小：114,657,792 字节（109.35 MiB）。
- SHA-256：`aea7970fffd26af2c3a8891a1aeb038ac976137cebac2b21860ce2352cdd6059`。
- 源码标签：`haoke-v0.1.3.2`，维护分支 `haoke/stable`。
- [软件和安装包入口](https://haoke.org/software/d000c19470bc74b6)。安装包由维护者上传既有网盘；源码 Release 不托管 APK。
- 12 份固定第三方源码/字典归档及 manifest/SHA256SUMS 随 [源码 Release](https://github.com/zh1414319052-lgtm/fcitx5-android-haoke/releases/tag/haoke-v0.1.3.2) 提供。原作者和所有组件许可保留，整合发行 GPL-3.0-only。
- `docs/release-source-manifest.json` 记录本次实际构建输入；Windows 文件的换行规范化不等于 Git 内容丢失。

## English

Fix language switching leaving Latin labels on the keyboard. Hangul, Japanese kana, Zhuyin and Sinhala now show matching characters, including Shift, previews and long-press alternatives. Returning from symbols or reopening restores the current language. English, Pinyin, Jyutping and Telex retain alphabetic input. Eight bundled extensions, theme settings and the existing release signature are preserved. Custom Rime schemes and some specialized Zhuyin layouts retain the original Latin layout.

The user waived tests for this formal release. Earlier QA6 passed 42 unit tests and three Android16 ARM64 instrumentation tests; long-press used attached-view dispatch and is not physical-gesture evidence. This build only received release packaging, artifact and signature verification. Original application/signing identity, data, update trust key and source licenses are preserved.
