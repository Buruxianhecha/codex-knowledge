# 怀民亦未寝 · iOS 独立移植工程

**移植基线：Android v0.38.2 / 62068，Git commit `41d3801c3f9faa4424fa3c5767181cffbceaf296`。**

此目录与原 Android 构建隔离：不会覆盖 Android 源码、签名、数据库或群友现有 APK。
这是 **iOS 第一阶段的可运行功能切片，不是 Android 全功能已移植。CI 会生成可供个人签名工具使用的未签名 IPA，但此文件不能直接在 iPhone 安装**。

## 技术策略

由于 Android 应用对 Jetpack Compose、Activity、Room、DataStore、WorkManager、Android 系统语音/通知等依赖紧密，
第一阶段采用 SwiftUI + Foundation + URLSession + Keychain 建立 iOS 原生运行底座，
而不是声称能够直接把 APK 转换为 IPA。未来可以视代码依赖清理情况提取 Kotlin Multiplatform 共享模型/业务逻辑。

## 第一阶段已规划/实现范围

- 独立 iOS 身份和四个一级页面：聊天、通讯录、发现、主页。
- 可创建、修改 AI 角色的名称、性格、模型标识、支持 HTTPS 的兼容聊天接口地址。
- Android 版本已有的六种服务商可以选择常见默认接入地址（需用户自行填写 API Key 和具体模型名）。
- Key 只进入 iOS Keychain；聊天/角色保存在本机 Documents 下带文件保护的 JSON；不会预埋任何真实 Key。
- 真实 URLSession 模型请求（非演示回复），OpenAI chat-completions 兼容单次回复，聊天记录本地持久化。
- 失败时真实错误提示，不伪造模型回答；不在日志打印密钥。

## 未移植：需按模块后续迭代

- 群聊与多个模型轮流回复、@、撤回、拍一拍、记忆全文回查。
- Android Room 历史库迁移与 ZIP 备份恢复（不导入原 APK 私有目录）。
- 朋友圈发布、AI 自动浏览/评论、收藏的真正读写。
- 语音识别、TTS、通话、后台主动消息和 WorkManager 替换方案。
- 多 Key / 跨服务商自动切换，壁纸、原有字库和自定义气泡逐项移植。
- 对 Android 专属手机工具（QQ 音乐无障碍、通知监听等）的 iOS 能力替代。
- iPhone 真机兼容性、App Store Connect/TestFlight 签名及苹果外部测试审核。

**「发现」中的后续功能会如实显示移植中，不伪造完整支持。**

## 编译

在 Mac 上安装 Xcode 与 XcodeGen：

```sh
cd standalone-apps/huaimin-yiweiqin-ios
brew install xcodegen
xcodegen generate
xcodebuild -project HuaiminIOS.xcodeproj -scheme HuaiminIOS \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```

对应 GitHub Actions：`.github/workflows/huaimin-ios.yml`，使用 macOS runner 生成工程并分别编译 **iOS 模拟器 unsigned .app** 和 **iPhoneOS unsigned Release .ipa**。
模拟器构建无法安装到真正的 iPhone，也不能直接分发给 QQ 群。

## QQ 群 iPhone 测试分发（已选择免费 7 天签名路线）

**首选免费 Apple Account + Sideloadly（Windows/macOS），备选 AltStore Classic + AltServer。**

- GitHub Actions 生成 **iPhoneOS Release 架构的未签名 IPA**，文件名为 `Huaimin-iOS-0.1.0-UNSIGNED-7day-sideload.ipa`。
- 每位 QQ 群测试者使用 **自己的 Apple Account** 在 **自己的电脑** 上签名并安装。作者不需要苹果设备、也不收集 Apple 密码/验证码。
- 普通免费个人签名一般有效 7 天，需在到期前依赖自己的电脑重签或刷新；无法实现下载直接点开安装。
- Apple Developer Program 与 TestFlight 是以后可选的付费分发路径，并非当前首选。
- **详细图文步骤与风险说明见 [FREE_SIDELOAD_HANDOFF.md](FREE_SIDELOAD_HANDOFF.md)**；未来付费方案仍可见 [TESTFLIGHT_HANDOFF.md](TESTFLIGHT_HANDOFF.md)。

## iOS 项目资源

- 已接入 Android 同源的「海底峡谷」壁纸，且通过 CI iOS 模拟器截图验收。
- 模拟器 .app 与设备目标 IPA 分开打包，避免群友误将模拟器构建当真机文件。

## 开发/许可与边界

Android 源码基于第三方 Cleos 上游叠加补丁；正式向群友发行之前，须确认上游代码和所用字体/素材的再分发授权。
开发中的功能断言仅表示源码/CI 的验证，不等于 iPhone 真机使用已经验证。
