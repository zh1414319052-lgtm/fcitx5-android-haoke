# 0.1.3-haoke.3 — 九宫格拼音

2026-11-11。正式版以 Fcitx5 Android 0.1.3 为基线，包名 `org.fcitx.fcitx5.android.haoke`，版本码 `11303`。从应用的「输入法」列表添加「九宫格拼音」，再用地球键切换。内置 Rime 执行离线解码，支持候选选词、退格和音节分隔。数字与符号面板返回后恢复九键。切换到英文、韩文、日文、注音或僧伽罗语时，键面使用相应字符。全键拼音等其他方案可继续使用。

## 正式包

- Android 6.0+（API 23），targetSdk 36，覆盖 arm64-v8a、armeabi-v7a、x86、x86_64。
- 大小 `114666186` 字节，SHA-256 `9144ae94592693641cb33fa0cb1e6741549a83bc8995ffc35042fd75f18e7e74`。
- 沿用浩科正式签名，证书 SHA-256 `a455f0d17ec4f31972c9a1dc7d9e3c8f5a7c31dd1e101765d25827780a917bf3`。已有浩科正式版可覆盖升级。
- [软件资料与安装包入口](https://haoke.org/software/d000c19470bc74b6)。正式 APK 由维护者通过既有下载入口分发。

## 验证与源码

42 项单元测试通过，正式版 lint 0 错误、260 警告。通用正式 APK 通过 ZIP、四架构原生库及依赖、64 位 ELF 与 ZIP 16 KB 对齐、API 34 签名检查。MuMu Android 15（x86_64）从 0.1.3-haoke.2 覆盖升级成功，输入法选择和测试设置保留。正式包在 MuMu 使用系统触摸验证了添加九宫格、输入后退格、选词上屏“你好”、切到韩语后输入“한글”以及切到日语后显示假名。QA7 另有九宫格和多语言输入的 5 项模拟器专项、回归测试。

整合发行采用 GPL-3.0-only，保留各组件的原许可和作者信息。九宫格的 Rime 修改以 `haoke-rime-nine-key.patch` 纳入主仓库，CMake 将其应用到固定 Rime 子模块的构建副本。请递归检出 `haoke-v0.1.3.3`，按 [构建说明](HAOKE_RELEASE.md) 与 [第三方源码清单](../third-party/corresponding-source/README.md) 重建。该标签的源码 Release 附有固定依赖及校验文件。

## English

Adds offline 9-key Pinyin. Add it under Input Methods and switch with the globe key. Candidate selection, Backspace and syllable separation are supported, and returning from numbers or symbols restores the nine-key layout. Switching to English, Hangul, Japanese kana, Zhuyin or Sinhala shows the corresponding keys.

The universal APK supports Android 6.0+ on four ABIs and uses the established Haoke signing certificate for in-place upgrades. All 42 unit tests passed, release lint reported zero errors and 260 warnings, and the signed artifact passed package, signature and native library checks. MuMu Android 15 verified an in-place upgrade from 0.1.3-haoke.2 while retaining the input method and a test preference. System touch events verified 9-key Pinyin and Hangul input and the switch to a Japanese kana layout. The QA7 build also passed five focused emulator tests.
