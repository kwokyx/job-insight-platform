#!/usr/bin/env bash
# 拉最新 github/backend → 同步 backend/ + config/ 到 worktree
# → commit 成 sync:... → 重编 jar → recreate 后端容器
#
# 用法：./scripts/sync-backend.sh
#
# 前置：必须在 frontend 分支上的 worktree 里跑
#      （/Users/gyx/job-insight-platform-wt-integration）
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# 1) 只认 github，不动 gitee。明确保持
BRANCH="${1:-backend}"
echo "▶ sync from github/$BRANCH to current worktree ($(git branch --show-current))"

# 2) 拉最新
echo "▶ 1/5 fetch github $BRANCH"
git fetch github "$BRANCH"

NEW_HEAD=$(git rev-parse "github/$BRANCH")
NEW_SHORT=$(git rev-parse --short "github/$BRANCH")
NEW_SUBJECT=$(git log -1 --format=%s "github/$BRANCH")

# 如果 worktree 的 backend/+config/ 已经和 remote 一致，直接跳过
if git diff --quiet "github/$BRANCH" -- backend config 2>/dev/null; then
  echo "  ✓ 已经和 github/$BRANCH 一致，无需同步"
  exit 0
fi

# 3) checkout 目标目录
echo "▶ 2/5 checkout backend/ + config/"
git checkout "github/$BRANCH" -- backend config

# 4) 自动清理 worktree 中多余的文件（在 worktree 里但 backend 分支没有的）
echo "▶ 3/5 删除 worktree 多余文件"
for dir in backend config; do
  [[ -d "$dir" ]] || continue
  # 远程分支里该目录下所有文件（相对路径）
  remote_files=$(git ls-tree -r --name-only "github/$BRANCH" -- "$dir/" | sort)
  # 当前工作树里的文件
  local_files=$(cd "$dir" && find . -type f ! -path './target/*' ! -path './.mvn/*' \
                 -printf '%P\n' 2>/dev/null \
              || cd "$dir" && find . -type f \
                   -not -path './target/*' -not -path './.mvn/*' \
                 | sed "s|^\./||" | sort)
  # 远程没有、本地有的 → 删
  comm -13 \
    <(echo "$remote_files" | sed "s|^$dir/||" | sort) \
    <(echo "$local_files" | sort) \
  | while IFS= read -r f; do
      [[ -z "$f" ]] && continue
      if [[ -f "$dir/$f" ]]; then
        rm -f "$dir/$f"
        echo "    - $dir/$f"
      fi
    done
done
git add -A backend config

# 5) commit (如果有改动)
if git diff --cached --quiet; then
  echo "  ✓ 没有实际改动，不 commit"
else
  echo "▶ 4a/5 commit as sync"
  # 截取 subject 的前 30 字做简述，太长了会超 50 字规范
  BRIEF=$(echo "$NEW_SUBJECT" | head -c 30)
  git commit -m "sync: 同步 github/$BRANCH 到 $NEW_SHORT（$BRIEF）"
fi

# 6) 重编 jar
echo "▶ 4b/5 mvn clean package"
( cd backend && mvn clean package -Dmaven.test.skip=true -q )

# 7) recreate 后端容器，加载新 jar
echo "▶ 5/5 recreate career-backend"
docker compose -p job-insight-platform \
  -f docker-compose.yml -f docker-compose.override.yml \
  up -d --force-recreate --no-deps backend

echo ""
echo "✓ 完成。worktree 已对齐 github/$BRANCH ($NEW_SHORT)"
echo "  等 Spring Boot 启动约 60 秒后可验证："
echo "  curl -s -m 10 -o /dev/null -w 'HTTP %{http_code}\n' http://localhost:8080/api/v1/analysis/overview"
