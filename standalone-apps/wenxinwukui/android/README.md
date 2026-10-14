# 问薪无愧 Android 0.1.0-beta

离线 WebView 容器，包含完整 `ledger_app/index.html` 的静态 HTML + PWA assets，不依赖公网服务。

* package: `com.wenxinwukui.ledger`
* minSdk: 26, targetSdk: 35
* 通过 Android 系统文件选择器导入 JSON，通过系统保存文档导出 JSON。
* 数据保存在此 APP 内 WebView 的本地持久存储。与浏览器中的 PWA 数据不自动共享；卸载 APP 会删除本地账单，请先备份。
* 这是演示版，未接入真实支付、微信、AI 云端、推送、会员计费与生物识别锁。
* 编译：`gradle :app:assembleDebug`，输出在 `app/build/outputs/apk/debug/app-debug.apk`。
* Debug APK 使用构建环境的调试签名，跨构建环境不保证覆盖安装。正式发行须配置固定 Release 签名。


## v0.1.2 Beta
- 安卓桌面启动器图标改为暖米白底橙色钱包。


**固定签名升级规则**：见 `SIGNING.md`，CI 不再发布临时 debug 签名包。
