# 怀民亦未寝 iOS：QQ群免费 7 天签名测试交接

状态：**选择免费 Apple Account（Apple ID）个人签名测试路线，不购买苹果付费开发者会员。**
对应 iOS 独立项目 `v0.1.0`，基于 Android v0.38.2 源码结构迁移。**本阶段仅移植基础角色与文字单聊，不等于 Android 功能完整移植。**

## 给群友的 IPA 是什么？

自动构建 `.github/workflows/huaimin-ios.yml` 在 macOS runner 生成 Release `iphoneos` 设备架构的 `HuaiminIOS.app`，
打包为标准 `Payload/HuaiminIOS.app` 结构，产物文件为：
`Huaimin-iOS-0.1.0-UNSIGNED-7day-sideload.ipa`。

该 IPA **没有苹果发行或个人签名**，不允许当成下载即装的应用发给群友。
它是让测试者在自己电脑上使用自己的 Apple Account 签名后安装的输入文件。
每个人独立签名，无须也不可向作者提供自己的 Apple Account 密码。

**下载时认准带 `UNSIGNED` 的英文文件名，不是正式发布、不适用企业共享证书、不提供签名绕过服务。**

## 首选：Windows + Sideloadly （初期群测）

群友自行操作，作者没有 iPhone 不影响这条流程：

1. 从本项目构建产物下载原始未签名 IPA，并在电脑上保存到本地。
2. 自行访问 **Sideloadly 官方网站** https://sideloadly.io/ 下载 Windows 安装包。
3. 根据工具提示安装苹果设备驱动；Windows 可能需要苹果官网独立安装的 iTunes/iCloud。
4. 用 USB 连接 iPhone，解锁并在手机上确认“信任此电脑”。
5. 将提供的 IPA 载入 Sideloadly，选择自己的设备，使用**群友本人**的 Apple Account 按工具流程签名与安装。
6. 根据 iOS 版本提示在“设置 → 隐私与安全性”启用开发者模式，并在设置里信任对应开发者。
7. 进入应用创建 AI 角色，并输入群友自己的合法 API Key 与模型 ID 测试真实聊天；模型服务可能需要自行付费。
8. 普通免费 Apple Account 的个人签名一般仅有效约 **7 天**；在到期前用同一个账号/设备重新签名或使用工具自动刷新，需要电脑在线并可连到 iPhone。

安装后第一轮需要测试：角色创建、选择服务商、API Key 保存、发送消息、退出重开数据是否还在、网络错误反馈。
请勿测试尚未移植的群聊、朋友圈、语音、备份或 AI 主动行为并误认为已经支持。

**注意：** 保持相同 Bundle ID、签名者、工具设置时，原有数据才有机会在刷新/覆盖更新时保留。
不保证第三方工具所有版本、所有 iOS 型号都能覆盖更新；
若遇到“签名不一致/要求卸载”**先保存聊天记录并反馈，不要直接卸载**。
当前第一阶段尚未实现 Android 式完整备份，也没有通用的 iOS 导出备份流程。

官方网站（第三方安装工具，并非 Apple 产品）：
- Sideloadly: https://sideloadly.io/
- Sideloadly FAQ: https://sideloadly.io/faq.html

## 备选：AltStore Classic + AltServer

- 电脑安装 AltServer（Windows 或 Mac），安装 AltStore Classic 到自己的 iPhone。
- 用 AltStore Classic 导入同一份 IPA，应用在测试者自己的 Apple Account 下签名。
- 免费账户应用通常 **7 天到期**，需通过同一 Wi-Fi 上可达的电脑 AltServer、或 USB 刷新。
- 免费账户可安装的侧载 App 数量有限（AltStore 文档：同时 3 个）；其他自签应用可能占用名额。
- 需要 AltStore **Classic**，不要将受地区限制的 AltStore PAL 或其它商店误认为可直接导入任意 IPA。

官方工具帮助：
- Windows 安装：https://faq.altstore.io/altstore-classic/how-to-install-altstore-windows
- 免费签名及刷新：https://faq.altstore.io/altstore-classic/your-altstore
- 电脑服务要求：https://faq.altstore.io/altstore-classic/altserver

## 费用和其他限制

- 本方案**不需作者购买 Apple Developer Program**，群友也不用付 688 元年费。
- 每个测试者仍需有自己的 Apple Account、可用 Windows/Mac 和 iPhone。
- 本方案**不实现下载 IPA 后在 iPhone 上直接点开安装**。首次安装通常要电脑；之后刷新仍依赖工具与条件。
- 个人签名 7 天失效后 App 可能无法打开，直到重新签名刷新。
- 某些应用能力（推送通知、特定 entitlements、App Groups 等）受免费 provisioning 限制。
- 该免费路径适合小规模内部群测，不适合大量非技术群友的一键长期分发。
- 所有源码素材与第三方上游 Cleos 许可需要在对群友进一步分发前核实。
- 不要分享 Apple Account 密码、验证码、证书私钥，不能把模型 Key 直接放在 APK/IPA。

## QQ 群反馈模板

```text
iOS 版本：
iPhone 机型：
怀民亦未寝 iOS 版本：0.1.0
使用的自签工具：Sideloadly / AltStore Classic
现象：无法签名 / 无法安装 / 无法打开 / 登录模型失败 / 其它
复现步骤：
错误提示（去除个人资料和 API Key）：
截图（注意隐私）：
```

## 未来可能变更路线

如果以后用户希望「不用电脑/不用续签/所有群友点击链接直接安装」，需要重新讨论合规的苹果分发路径；
**不可以把普通 7 天免费个人签名宣传成符合这些要求**。
本文件是当前主选路线，原 `TESTFLIGHT_HANDOFF.md` 仅作为可选的未来付费分发备选方案。
