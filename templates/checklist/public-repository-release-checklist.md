---
status: active
confidence: 0.98
reuse_count: 0
last_used: 2026-08-26
verified_in:
  - cassell-open-world-simulator
  - jianlai-life-simulator
  - fanren-human-world-simulator
expires_after: 2026-11-26
cross_refs:
  - lessons/verification-claims-must-match-test-depth.md
  - lessons/minimal-requirements-are-a-contract.md
  - mistakes/fanren-ci-triggered-on-incomplete-snapshot.md
---

# 公开 GitHub 仓库发布清单

## 1. 冻结需求

- [ ] 列出必须有的功能。
- [ ] 列出明确禁止项，尤其是“只/仅/不要/单文件”。
- [ ] 确认仓库名、可见性和默认分支。
- [ ] 确认是否需要在线站点；源码公开不等于站点已部署。

## 2. 整理内容

- [ ] README 说明用途、运行方式、测试方式和当前边界。
- [ ] 项目名、页面标题、仓库名和图标一致。
- [ ] 明确许可证；没有许可证时说明公开可见不等于授权再利用。
- [ ] 同人/第三方内容写非官方、非商业与权利边界。
- [ ] 不提交构建缓存、临时文件和本地状态。

## 3. 安全

- [ ] 搜索 API Key、token、cookie、`state.json`、`.env` 和私钥。
- [ ] 检查 Git 历史，不只检查当前文件。
- [ ] 清理图片 EXIF、文档作者和隐藏元数据。
- [ ] 登录态、OAuth token、微信 `AppSecret` 不进前端和 Git。

## 4. 文件与限额

- [ ] 列出最大文件和仓库总大小。
- [ ] 超过 25 MiB 的单文件不使用浏览器上传。
- [ ] 普通 Git 单对象不得超过 100 MiB；大二进制评估 LFS/Release。
- [ ] 单次 push 不超过 2 GiB。
- [ ] Pages 站点与带宽限制在发布前重新查官方文档。

## 5. 本地验证

- [ ] 格式、Lint、单元/行为测试和构建按项目实际运行。
- [ ] 测试报告写明类型和数量，不只写“全部通过”。
- [ ] 首次 push 前，当前快照可独立运行。
- [ ] 检查工作区，避免带入无关改动。

## 6. 推送与回读

- [ ] 推送目标默认分支。
- [ ] 回读远端 HEAD SHA。
- [ ] 回读远端文件树和关键文件 Blob。
- [ ] 比较本地与远端 SHA/内容。
- [ ] CI 结论与当前 HEAD 一致。
- [ ] 保留失败 run 的真实原因，不用后续绿灯改写历史。

## 7. 用户路径

- [ ] 从仓库 README 的公开入口开始操作。
- [ ] 页面可打开、不空白、不乱码。
- [ ] 核心交互、刷新、重启/重载和移动端路径已检查。
- [ ] 排他需求通过负向检查。
- [ ] 最终报告区分“已验证”和“未验证”。

## 8. 记录

```text
repository:
branch:
head_sha:
remote_blob_checked:
tests:
ci_run:
live_entry_checked:
known_gaps:
```
