---
status: active
confidence: 0.93
reuse_count: 0
last_used: 2026-06-23
cross_refs:
  - lessons/2026-06-23-automation-environment-and-command-verification.md
  - decisions/2026-06-23-document-and-runtime-verification.md
---

# 自动化优先使用绝对路径

## Pattern

在本机自动化里，先用绝对路径定位记忆、仓库和知识库，再决定是否依赖环境变量或当前工作目录。

## Steps

1. 先确认目标文件的绝对路径。
2. 再验证当前 shell 会话中的变量和命令是否可用。
3. 遇到工作目录异常时，退回到已知存在的根目录。
4. 最后才执行写入、提交和推送。

## Result

- 路径漂移更少。
- 对 `CODEX_HOME` 这类变量失效更稳健。
- 更容易区分“路径错了”和“命令不存在”这两类故障。
