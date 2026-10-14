# 问薪无愧 · 安卓固定签名与覆盖升级

为了每次可以直接覆盖安装，生产构建只发布使用同一固定私钥签名的 release APK。

## 首次配置（必须一次）

1. 从 ChatGPT 下载“问薪无愧-固定签名密钥-私密备份.zip”，解压后妥善离线保存，不上传公开仓库。
2. 打开 GitHub 仓库 `Buruxianhecha/codex-knowledge` > **Settings > Secrets and variables > Actions > New repository secret**。
3. 创建两个 Repository secret：
   - `WENXIN_KEYSTORE_BASE64`：从备份压缩包中 `GitHub-Secrets-配置.txt` 复制对应的 Base64 一整行。
   - `WENXIN_SIGNING_PASSWORD`：复制同文件中的密码那一行。
4. 在 GitHub Actions 中运行 `Build 问薪无愧 Android (固定签名)`。仅当证书指纹与 `signing/expected-cert-sha256.txt` 一致时才会输出 APK。以后修改源代码可触发构建。

注意：公开代码仓库不应包含 `*.p12` / `*.jks` / base64 私钥及密码。此目录只保留可公开的证书 SHA-256 指纹。

## 后续版本发布规则

- `applicationId` 必须保持 `com.wenxinwukui.ledger`。
- 每次发布递增 `versionCode`；`versionName` 用于显示版本。
- 后续所有 APK 使用同一个别名 `wenxinwukui` 和同一把私钥；千万不要重新生成。
- 如 secrets 丢失或与已固定证书不一致，CI 自动失败；**严禁回退到临时调试签名 APK**。
- 新 APK 与旧 APK 必须包名相同、证书一致且版本号不降低，才能正常覆盖升级并保留本地账本。

## 首次迁移提示

v0.1.0 和 v0.1.1 APK 已使用不同临时 Debug 签名，无法从 APK 提取其私钥，因此无法保证直接覆盖到新的固定证书版本。
从旧版过渡时：在旧 APP 中先“我的 → 导出”保存 JSON 备份，再卸载旧版、安装新的固定签名版，在新 APP 中导入 JSON。**先检查备份实际保存成功再卸载**。此操作只需要做一次，后续就能正常覆盖升级。

## 本地构建（在安全的本机上）

将备份中的 keystore 路径设置为环境变量 `WENXIN_KEYSTORE_PATH`，密码设置为 `WENXIN_SIGNING_PASSWORD` 后运行 `gradle :app:assembleRelease`。私钥与密码绝不应出现在控制台日志、GitHub commit 或公开附件中。
