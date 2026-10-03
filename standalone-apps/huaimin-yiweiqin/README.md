# 怀民亦未寝（Cleos 增量定制）

这是 `codex-knowledge` 中完全隔离的 Android 定制应用目录，不属于知识库的 `projects/` 条目，不参与知识条目计数，也不会修改其他项目。

## 目标

- 保留 Cleos 当前基线版本的全部原有功能。
- 手机安装后显示名称：**怀民亦未寝**。
- 使用独立 Android applicationId：`com.lin.huaimin`，可与原版 Cleos 共存。
- 保留原有 `set_alarm` / `set_timer`。
- 新增 `get_next_alarm`：读取 Android 系统公开的“下一次闹钟”时间。
- 新增 `show_alarms`：直接打开手机系统时钟的闹钟列表。
- AI 被明确告知 Android 普通应用不能读取系统时钟中的完整闹钟明细，因此不能编造全部闹钟列表。

## 基线

上游：`jqzhang921-sudo/cleos`

固定提交：`c19e50bcc0f75b09e0b54dff8f45a2e74163eb37`（Cleos 0.35.3）

为了保持上游功能完整且避免把一个未声明项目级 LICENSE 的仓库整份重新发布到这里，本目录采用“固定上游提交 + 最小增量补丁”的方式。GitHub Actions 构建时会拉取该固定提交，再应用 `apply_huaimin.py`。

## 构建

工作流：`.github/workflows/huaimin-yiweiqin-android.yml`

每次本目录或工作流发生变化，会自动：
1. 拉取固定版本 Cleos；
2. 应用“怀民亦未寝”增量修改；
3. 运行 Android 单元测试；
4. 构建 Debug APK；
5. 上传名为 `怀民亦未寝-debug-apk` 的 Actions Artifact。

构建前会实际解码图标，构建后会反向解析 APK 的 `icon`、`roundIcon` 和桌面启动入口，并确认包内图片与原图字节一致、可完整解码且 APK 签名有效。损坏图片不能再通过构建并被上传。

手机端可在 GitHub 仓库的 **Actions** 页面进入最新成功运行，下载 Artifact，再解压安装 APK。

> 注意：这是个人定制构建。上游仓库当前未声明项目级 LICENSE；如需公开分发、商业化或作为独立产品发布，应先确认上游作者授权。
