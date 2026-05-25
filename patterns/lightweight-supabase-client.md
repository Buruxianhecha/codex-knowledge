---
status: active
since: 2026-05-25
verified_in: [pdf-to-excel]
cross_refs:
  - decisions/local-remote-dual-write.md (为什么需要这个客户端)
---
# 轻量 Supabase REST 客户端模式

## 适用场景
不想引入 @supabase/supabase-js 或 supabase-py 的完整依赖，只需要 Auth + Storage + REST 的基本操作。

## 结构
直接用 httpx/requests 调用 Supabase REST API，自己管理 anon key 和 Bearer token。

## 代码示例
```python
def _headers(auth_token=None):
    h = {"apikey": SUPABASE_KEY}
    h["Authorization"] = f"Bearer {auth_token or SUPABASE_KEY}"
    return h

def supabase_signin(email, password):
    r = httpx.post(f"{SUPABASE_URL}/auth/v1/token?grant_type=password",
                   json={"email": email, "password": password},
                   headers=_headers())
    return r.json()
```

## 已知应用
- pdf-to-excel: supabase_client.py 完整实现

## 标签
#supabase #rest #lightweight-client

