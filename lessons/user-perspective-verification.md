---
status: best_practice
confidence: 0.99
reuse_count: 6
last_used: 2026-08-26
verified_in:
  - shiguang-album-test-site
  - cassell-open-world-simulator
  - jianlai-life-simulator
  - fanren-human-world-simulator
  - minimal-tv-clock
  - codex-knowledge-audit
expires_after: none
cross_refs:
  - lessons/quality-gating.md
  - mistakes/assuming-user-error.md
  - anti-patterns/deliver-without-verification.md
  - templates/checklist/user-acceptance-checklist.md
  - lessons/application-acceptance-over-command-success.md
---
# 用户视角验收原则

> 任务完成的标准不是“代码生成成功”，而是最终拥有结果的系统和用户路径已经得到与声明强度匹配的验证。

该原则自 2026-05-25 起持续使用超过三个月，并在相册、多个浏览器模拟器、极简 UI 失败复盘和知识库审计中反复得到支持。

核心要求：未验证不宣称完成；理论与真实体验冲突时继续复现；命令/产物/集成/宿主/用户路径分层报告；同类重复错误提高验收强度；入口、操作顺序、结果和恢复属于完整用户路径。
