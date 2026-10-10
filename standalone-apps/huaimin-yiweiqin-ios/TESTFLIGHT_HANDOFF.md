# 怀民亦未寝 iOS — QQ 群外部测试分发交接（无 iPhone 的开发者）

## 目前状态与不可混淆的边界
- Android **v0.38.2 (62068)** 是 iOS 移植需求参考，不是 iOS 版本号。
- iOS 项目目前为 **v0.1.0**，原生 SwiftUI，可创建 AI 角色、保存 Key 和进行真实文字聊天。
- iOS Simulator 无签名构建成功，只能用于 Xcode/macOS 模拟器；iPhoneOS 无签名构建验证即使成功，也不意味着能安装在 iPhone。
- 需要通过 Apple 有效发行签名后的 TestFlight 或限定设备 Ad Hoc，不能把 Android APK、Simulator .app 或未经签名的 .ipa 直接发给 QQ 群。

## 推荐：TestFlight 外部测试

1. 应用所有者自行注册/持有 **Apple Developer Program** 会员，验证付款及 App Store Connect 登录权限。
2. 由所有者在 App Store Connect 建立新 app record，Bundle ID 暂定 `com.lin.huaimin.ios`（正式注册前可更改）。
3. 准备隐私政策、支持邮箱、测试说明、测试账号/示例接口说明；严禁附带任何真实用户 API Key。
4. 用 **Xcode Cloud**（连接用户仓库）或经 App Store Connect 认证且安全签名的 macOS CI 生成 App Store 发行构建，并上传至 App Store Connect。
5. 在 TestFlight 建立内部测试组，再建立外部测试组并附上可测试版本；首次外部测试构建需通过苹果的 TestFlight App Review。
6. 审核通过后，在外部测试组创建公共邀请链接，发给 QQ 群苹果用户。群友通过 TestFlight 安装，不需开发者本人有 iPhone。
7. 由群友提交机型/iOS 版本、功能操作步骤、无敏感内容的截图、可分享的崩溃报告；逐项归档修复并上传新构建。

官方资料：
- https://developer.apple.com/testflight/
- https://developer.apple.com/help/app-store-connect/test-a-beta-version/invite-external-testers/
- https://developer.apple.com/documentation/xcode/distributing-your-xcode-cloud-builds-through-testflight

## 交付验证门禁

- Xcode iOS Simulator 编译通过，至少跑一次模拟器实际启动。
- Xcode iphoneos 架构编译通过（不等于真机已通过）。
- 真实 API 服务商请求分别验证（用户自行输入 Key，不写入仓库）。
- 聊天记录保存、重启仍在、API Key Keychain 处理与网络异常真实反馈。
- Apple 发行签名 / App Store Connect 上传成功。
- TestFlight 外部审核通过、QQ 群用户实际下载安装、打开和发送第一条消息。
- Android 功能逐项移植后回归：群聊、朋友圈、语音、记忆、完整备份、气泡和字体。
- 确认第三方上游 Cleos 的可分发许可、素材字体许可证以及隐私条款，再公开发行。

## 已知产品差异与技术约束
- 群聊、朋友圈与收藏当前是**尚未移植**，界面标注，不能宣称与 Android 完全一致。
- Android WorkManager、无障碍点击 QQ 音乐、通知监听及后台连续 AI 电话无法被 iOS 直接复制；需要逐项重新设计并告知用户差异。
- Apple App Review 审核和 TestFlight 邀请链接不能通过机器人凭空生成；须由授权的开发者账号实际完成。
- 没有苹果会员时可以继续开发、编译和模拟器验证，但不能给任意 QQ 群 iPhone 用户提供通用可安装 IPA。
- 对用户本地 API Key 不提供共享或托管；AI 对话可能产生所选第三方供应商费用。

## 建议的群友反馈格式
版本号 / iPhone 机型 / iOS 版本 / 使用的服务商和模型（不要写 Key） / 重现步骤 / 实际结果 / 预期结果 / 截图或崩溃日志（注意隐私）。

**原 Android v0.38.2 和其所有现有版本均不受此 iOS 移植分支影响。**
