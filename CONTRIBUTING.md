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

## 四、commit 消息

**格式**：`类型(范围): 做了啥`，用中文。

**类型**：`feat` 新功能 / `fix` 修 bug / `style` 样式 / `refactor` 重构 / `docs` 文档 / `chore` 杂项 / `sync` 同步 / `integrate` 集成合并。

**例子**：

```
feat(frontend): 首页加入技能图谱入口
fix(backend): 修复简历解析的空指针
refactor(frontend): workspace 重构成任务工作室
sync: 同步后端最新
integrate: 合并 frontend 到 main 作为发布分支
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
