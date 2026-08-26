---
status: verified
confidence: 0.98
reuse_count: 4
last_used: 2026-08-25
verified_in:
  - cassell-open-world-simulator
  - jianlai-life-simulator
  - fanren-human-world-simulator
  - codex-knowledge
expires_after: none
cross_refs:
  - projects/2026-08-25-browser-simulator-release-series.md
  - mistakes/fanren-ci-triggered-on-incomplete-snapshot.md
  - templates/checklist/public-repository-release-checklist.md
  - lessons/user-perspective-verification.md
---

# 验证声明必须匹配测试深度

## 测试不是一个布尔值

“测试通过”至少要补充四个限定：什么测试、覆盖什么、针对哪个版本、在哪里运行。

| 类型 | 能证明 | 不能证明 |
|------|--------|----------|
| 语法/解析 | 文件可被解析 | 运行逻辑正确 |
| 静态锚点 | 关键结构/文案存在 | 结构被正确使用 |
| 单元行为 | 单个转换满足断言 | 浏览器/服务集成正确 |
| 集成 | 多组件链路可工作 | 真实用户全部路径正确 |
| 压力/长程 | 给定负载下性质保持 | 未测环境也稳定 |
| 构建 | 生产产物生成 | 页面不空白且可交互 |
| CI | 某个 SHA 在 runner 上通过 | 当前本地、其他 SHA 或用户设备通过 |
| 用户路径 | 指定设备和路径成功 | 所有边缘情况通过 |

## 本期对比

- 凡人的 4 项测试是 smoke：HTML、规则锚点、存档关键字和脚本解析。
- 剑来使用 VM 执行真实内联脚本，验证语义选择、随机开场和存档行为。
- 卡塞尔进一步验证单调状态、跨标签页、迁移和五卷各 180 轮内容压力。

三者都可以写“测试通过”，但绝不能写成同一个质量等级。

## SHA 绑定

CI 结论属于 `(repository, commit_sha, workflow, run)` 四元组。凡人项目中：

- `da2bbae3de79`：CI 失败，因为快照缺少 `index.html`。
- `e20f299caeb8`：CI 成功，完整文件已存在。

只说“CI 后来绿了”会丢失中间失败教训；只说“CI 曾红”又会误报当前状态。

## 推荐报告格式

```text
Verified at <SHA>:
- syntax: pass
- unit behavior: 18 tests pass
- production build: pass
- CI run: completed/success
- browser user path: checked / not checked
- remaining gaps: ...
```

## 远端核验阶梯

1. 本地测试。
2. 本地完整快照。
3. 推送默认分支。
4. 回读远端 HEAD 和文件树。
5. 比较 commit/blob。
6. 读取该 HEAD 的 CI。
7. 打开真实入口走用户路径。

每一步只能升级一部分置信度。

## 标签

#testing #ci #github #verification #reporting #sha
