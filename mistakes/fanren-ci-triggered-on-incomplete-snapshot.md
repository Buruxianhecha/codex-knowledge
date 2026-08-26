---
status: active
confidence: 1.0
reuse_count: 0
last_used: 2026-08-25
verified_in: [fanren-human-world-simulator]
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - lessons/verification-claims-must-match-test-depth.md
  - templates/checklist/public-repository-release-checklist.md
---

# CI 在不完整仓库快照上被触发

## 症状

`fanren-human-world-simulator` 在提交 `da2bbae3de79` 推送工作流后，GitHub Actions 执行 `npm test` 并失败：

```text
ENOENT: no such file or directory, open '.../index.html'
```

测试、`package.json` 和工作流已在远端，但核心 `index.html` 尚未推送。

## 根因

一组相互依赖的发布文件被分成连续 push。每次 push 都触发 CI，而中间提交不是可运行快照。

## 后续状态

完整项目随后推送，HEAD `e20f299caeb8` 的 Verify 工作流完成并成功。旧失败仍是有效历史证据，不能删除或假装没有发生。

## 为什么危险

- 分支保护可能阻止后续操作。
- 通知产生无意义噪音。
- 读者只看最新/首条 run 时容易误判。
- 中间提交无法独立 checkout 和验证。

## 预防

- 本地先形成最小可运行快照，再首次 push。
- 可以多次本地 commit，但把依赖组一起推送。
- 或让工作流在核心文件存在时才启用，但不能用条件掩盖真正缺文件。
- 报告 CI 时始终带 HEAD SHA 和 run URL。

## 标签

#mistake #github-actions #ci #atomic-release #git
