# Mistakes 压缩版 v6

## 近期错误

| 错误 | 根因 | 预防 |
|------|------|------|
| 豆包脚本成功但客户端未接纳 | 验证过早停止 | 宿主/功能/重启保持 |
| 李跳跳无导入证明 | 截断 JSON 做字符串修复 | 严格解析 + golden sample + 真实导入 |
| 凡人中间红 CI | 依赖文件分批 push | 首次 push 前完整快照，报告带 SHA |
| 极简时钟需求漂移 | 善意扩展覆盖排他要求 | 负向清单 + DOM/文件树检查 |
| 拾光测试站说成 production | 混淆 preview 与正式产品 | 结论服从最完整验收 |
| PowerShell 编码根因写过强 | 把正常 Unicode 转码当因果 | 检查 bytes/decode/replace/encode/BOM/consumer 全链 |
| 知识审计压缩事实源 | 把 Compact 思路施加到 Source | compare + 维护 checklist + Source Regression Guard |

## 长期错误

OCR 复制粘贴、GPT prompt 未传入、下载无鉴权、Flask debug、硬编码路径、静默异常、Provider 迁移漏模型映射、把文档约定当运行时事实。
