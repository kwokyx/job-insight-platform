# 协作规范

## 一、分支模型

三条长期分支：

- `main` — **集成分支**，前后端完整最新版本（里程碑/发布节点使用）
- `backend` — 后端工作分支，后端同学推这里
- `frontend` — 前端工作分支，前端同学推这里

日常开发各推各的分支，**不要直接推 `main`**。里程碑（交付、演示）时把 `backend` 和 `frontend` 都合到 `main`。

## 二、谁负责哪里

- **前端同学** → 负责 `frontend/` 所有代码，推到 `frontend` 分支
- **后端同学** → 负责 `backend/` / `algorithm/` 等非前端目录，推到 `backend` 分支

**前端是最终权威**。后端为了验证接口可以在 `frontend/` 里写 UI（AI 辅助也行），但要知道：**前端同学后续会按自己的版本覆盖**，后端的前端产出任务是"验证后端接口能跑通"，不保证留存。

## 三、怎么拿对方最新代码

### 前端拿后端最新（只拉非前端部分）

```bash
git fetch origin
git diff --name-only --diff-filter=d frontend..origin/backend \
  | grep -v '^frontend/' \
  | xargs -r git checkout origin/backend --
git add -A && git commit -m "sync: 同步后端最新"
```

**想看后端接了哪些新接口**（不自动拉进来，手工看）：

```bash
git diff frontend..origin/backend -- frontend/src/api.js
```

记下新增的函数和 endpoint，**在自己分支里手写对应调用和 UI**。

### 后端拿前端最新

```bash
git fetch origin
git checkout origin/frontend -- frontend/
git add frontend/ && git commit -m "sync: 同步前端最新"
```

这一步会把后端自己写的前端 UI 覆盖成前端同学的版本，这是正常的。

## 四、commit 消息规范

### 格式

```
<type>(<scope>): <简短描述>

<可选：详细说明，与描述之间空一行>
```

### 基本规则

- 全部用**中文**
- 描述行**不超过 50 字**，结尾**不加句号**
- 动词放前面：用"增加 / 修复 / 重构 / 删除"，不写"增加了"、"修复的是"
- 一个 commit **只做一件事**，多的改动拆成多个 commit
- 需要解释"为什么"时，空一行写 body，每行不超过 72 字

### type 列表

| type | 什么时候用 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `style` | 样式/UI 调整（不改功能） |
| `refactor` | 重构，不改功能也不改样式 |
| `perf` | 性能优化 |
| `docs` | 文档变更 |
| `chore` | 依赖、构建、配置等杂项 |
| `test` | 测试 |
| `sync` | 跨分支同步（见第三节） |
| `integrate` | 集成合并到 main |

### scope 常用取值

`frontend` / `backend` / `algorithm` / `auth` / `report` / `recommend` / `ai` / `crawler` / `docs` / `deploy`

跨多个 scope 时可省略，例如 `sync:`、`integrate:`、`chore:` 这种全局性改动。

### 好例子 vs 坏例子

```
✓ feat(frontend): 首页加入技能图谱入口
✓ fix(backend): 修复简历解析对空字段的空指针
✓ refactor(frontend): workspace 重构成任务工作室
✓ sync: 同步后端最新
✓ integrate: 合并 frontend 到 main 作为发布分支

✗ update                              # 没说做了啥
✗ 改了首页                             # 没 type
✗ feat: 加了按钮和修了 bug              # 一次做两件事
✗ feat(frontend): 首页加入了很多东西    # 描述不具体
✗ 修复 bug.                            # 没 type、没 scope、结尾带句号
```

### 什么时候写 body（多行 commit）

简单改动一行就够。改动背后有"为什么这么做"的设计决策、取舍、影响面时写 body：

```
refactor(frontend): 把 request 函数移出组件

组件内直接写 fetch 散乱且重复，提到 api.js 后统一加了
缓存和 FormData 处理，其他 View 也能复用。
```

## 五、冲突了怎么办

按"归属"解：**前端文件留前端分支的，后端/算法文件留 backend 分支的**。

```bash
# 前端文件 → 留前端版本
git diff --name-only --diff-filter=U | grep '^frontend/' | xargs -r git checkout --ours && git add frontend/

# 后端/算法 → 留 backend 版本
git diff --name-only --diff-filter=U | grep -E '^(backend/|algorithm/)' | xargs -r git checkout --theirs && git add backend/ algorithm/
```

> 上面命令是前端视角（在 `frontend` 分支 merge `backend`）。后端方向相反，`--ours` 和 `--theirs` 对调。

## 六、里程碑集成到 main

需要把当前代码作为"稳定版"（演示、交付、老师检查等）：

```bash
git checkout main
git merge backend   # 带后端最新
git merge frontend -X theirs   # 带前端最新（冲突时以 frontend 为准）
git push origin main
```

## 七、这些别 commit

`.env` / `.claude/` / `node_modules/` / `target/` / `.DS_Store` / `output/` — `.gitignore` 会自动挡掉，不要硬加。
