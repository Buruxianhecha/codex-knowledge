---
status: active
confidence: 0.96
reuse_count: 0
last_used: 2026-06-27
verified_in: [li-tiaotiao-rule-repair]
cross_refs:
  - projects/2026-08-26-two-month-learning-audit.md
  - lessons/application-acceptance-over-command-success.md
  - lessons/claim-strength-must-match-evidence.md
---

# 李跳跳规则修复缺少真实导入证明

## 症状

用户收到对象式转换后报告“显示格式错误”。后续检查发现大段 `popup_rules` 输入在末尾截断，本身不是完整 JSON。曾提出修复尾逗号、转义和外层结构，并保留可恢复规则，但没有找到最终成品被李跳跳成功导入的证据。

## 根因

- 在完整解析输入前就开始做字符串层转换。
- 把“生成了看似正确的 JSON”当成目标应用兼容。
- 输入已经截断，却曾出现“保留所有规则”一类过强承诺。
- 没有用已知可导入的最小样本做结构对照和回归。

## 结构化数据修复的正确顺序

```text
原始字节与编码
  -> 严格解析
  -> 报告截断位置/不可恢复段
  -> 建目标 Schema
  -> 结构化转换
  -> 再次解析
  -> 目标应用真实导入
  -> 抽样核对规则行为
```

## 不能声称的内容

- 不能确认所有规则都保留。
- 不能确认 UTF-8 文件可导入。
- 不能确认外层数组和转义形式就是当前目标版本唯一格式。
- 不能把“文件可下载”写成“导入成功”。

## 预防

1. 禁止用正则或字符串替换修复嵌套 JSON 主结构。
2. 对截断输入输出“可恢复数量、跳过数量、首个错误位置”。
3. 保存一个用户已确认可导入的 golden sample。
4. 只有真实导入后才使用“兼容/可导入”。

## 标签

#mistake #json #structured-data #encoding #import #verification
