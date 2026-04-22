# 前端（Vue 3 + Vite）

整个 `frontend/` 目录可以**直接复制/拉取**，两种运行方式都自带对齐，不用改代码。

---

## 方式 A：纯 Docker（队友默认方式）

替换 `frontend/` 目录后，在仓库根目录：

```bash
docker compose up -d --build frontend
```

对齐靠 `nginx.conf`：nginx 里 `/api/*` 通过 Docker 内网 DNS 转到 `backend:8080`，`/algorithm/*` 转到 `algorithm:8000`。服务名写死在 `docker-compose.yml`，不会随宿主端口变化而漂移，所以不管宿主机上 backend 暴露到 8080 还是别的端口都不影响。

浏览器打开 http://localhost （compose 里 frontend 映射的是 80）。

## 方式 B：本地 Vite + 容器化后端（开发者改前端时的热更新方式）

```bash
cd frontend
npm install
cp .env.example .env.local   # 默认值就够用
npm run dev
```

浏览器打开 http://localhost:5173 。

此时 Vite 代理 `/api` 到 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）。前提是后端在宿主机上可达 —— 只要 `docker-compose.override.yml` 里 backend 暴露了 8080，或本机直接 `mvn spring-boot:run`，就能对上。

后端换端口 / 换机器时改 `.env.local`：

```
VITE_API_PROXY_TARGET=http://localhost:18080
# 或
VITE_API_PROXY_TARGET=http://192.168.1.20:8080
```

---

## 关键约束

- 所有业务请求都走相对路径 `/api/v1/*`（见 `src/api.js`），不硬编码后端地址，是两种方式都能跑的前提。
- `nginx.conf` 里的 `backend` / `algorithm` 必须等于 `docker-compose.yml` 里的 service 名，改 service 名要同步改这里。
- `.env.local` 被 gitignore，只影响本地 dev；Docker 构建完全不读它。

## 生产构建

```bash
npm run build     # 产物在 dist/
```

根目录 `docker-compose.yml` 的 `frontend` 服务会把 `./frontend/dist` 挂进 nginx，但 Dockerfile 里也会在容器内自己 `npm run build` 一次，所以提不提前 build 都行。
