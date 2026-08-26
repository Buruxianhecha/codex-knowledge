---
status: active
confidence: 0.98
reuse_count: 0
last_used: 2026-08-12
verified_in: [shiguang-album-test-site]
risk: high
cross_refs:
  - projects/2026-08-12-shiguang-album.md
  - lessons/application-acceptance-over-command-success.md
  - patterns/auth-provider-mode-boundary.md
---

# 把拾光相册测试站说成正式上线

## 症状

较早交付声明使用了“最终版本已上线并通过构建与核心交互验收”。同日更深的产品验收报告结论却是“暂不建议上线”，并确认真实上传矩阵、跨设备恢复、服务端授权、真实认证和真机体验仍未完成。

## 根因

- 把“站点可访问”和“核心页面连通”写成“正式上线”。
- 没有区分测试站、公开预览和生产产品。
- 认证与账户隔离仍是本地模拟，却使用了容易让人联想到生产安全的措辞。
- 自动浏览器无法完整操作真实文件选择器，该缺口未在最初结论中获得足够权重。

## 风险

私人照片属于高信任数据。用户可能根据“正式上线”上传无法恢复、无法跨设备访问、没有服务端权限保护的内容。

## 正确表述

```text
拾光相册公开测试站已构建并完成核心交互验收。
当前数据仅保存在本机浏览器，手机/微信认证为模拟流程；
尚未通过正式产品上线验收，不建议托付唯一照片副本。
```

## 预防

- 明确定义 `prototype / test / preview / production`。
- 涉及私人数据时，上线标准包括恢复演练、权限、配额、故障和真机测试。
- 最终结论服从覆盖面最完整、时间最新的验收报告。

## 标签

#mistake #product-launch #privacy #photo-app #verification
