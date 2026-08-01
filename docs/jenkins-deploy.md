# Jenkins 流水线部署

## 当前服务器结论

已检查目标服务器：Ubuntu 24.04.4 LTS，2 vCPU、3.6 GiB 内存、1.9 GiB Swap、约 59 GiB 磁盘。

服务器已经具备 Docker Engine 29.6.1 和 Docker Compose v5.3.1，Docker 服务已启用；宿主机没有 Java 和 Maven，Jenkins 也尚未安装。

服务器当前还有一套 `sub2api` 服务占用公网 `8080`，Nginx 占用 `80/443`。因此本项目不能继续使用公网 `8080`，生产环境使用 `18080` 作为本机回源端口，再由 Nginx 转发到它。

这版 Jenkinsfile 已调整为在 Maven Docker 容器中构建，不要求宿主机安装 Java/Maven。Jenkins Agent 仍需要能执行 Docker CLI 和 `docker compose`，并能访问 Docker daemon。

## 推荐部署形态

建议 Jenkins Controller 放在另一台机器或已有 CI 环境，目标服务器只运行 Docker 服务和一个 Jenkins Agent。目标机只有 2 核 3.6 GiB，同时运行 Jenkins Controller、Maven 构建、现有 `sub2api` 和本项目六个容器，内存余量会比较紧。

如果必须把 Jenkins 安装在这台机器上，建议限制为单个执行器，并避免并发构建；Jenkins 本身仍需要 Java 21，但流水线构建不依赖宿主机 Maven。

## 服务器环境准备

Jenkins Agent 所在环境需要确认：

```bash
docker version
docker compose version
docker run --rm hello-world
```

如果 Agent 以 `jenkins` 用户运行，需要允许它访问 Docker：

```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

不要把 Docker socket 暴露到公网。Jenkins 任务工作目录必须是部署目录，或者至少要能访问该目录对应的 Docker bind mount 路径。

## Jenkins 任务

1. 创建 Pipeline 任务。
2. SCM 指向本仓库。
3. Script Path 填写 `Jenkinsfile`。
4. 确保目标服务器存在 `/opt/luntan/.env`，并让 Jenkins Agent 账号可以读取它。
5. Jenkins 节点只保留一个执行器，避免同一台 2 核服务器并行构建。

生产环境变量可以从示例开始。首次准备时，在服务器上创建持久化配置目录：

```bash
sudo mkdir -p /opt/luntan
sudo cp .env.server.example /opt/luntan/.env
sudo chmod 600 /opt/luntan/.env
```

然后替换所有密码和 Token。这个 `.env` 已将 Gateway 设置为 `127.0.0.1:18080`，MySQL 和 Redis 也只绑定本机，不直接暴露到公网。

Jenkinsfile 默认从 `/opt/luntan/.env` 读取配置，不要求把 `.env` 放进 Git 或 Jenkins 工作区。如果 Jenkins Agent 使用 `jenkins` 用户，需要将该文件的读取权限授予它，例如：

```bash
sudo chown jenkins:jenkins /opt/luntan/.env
sudo chmod 600 /opt/luntan/.env
```

不要把真实 `.env` 提交到 Git。更稳妥的做法是使用 Jenkins Credentials/Secret file 在部署阶段写入工作区，并在构建结束后清理。

## Nginx 回源

现有 Nginx 已占用 `80/443`。域名对应的 server 块应将请求转发到：

```nginx
location / {
    proxy_pass http://127.0.0.1:18080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

不要直接把本项目 Gateway 改回公网 `8080`，否则会与现有 `sub2api` 冲突。

## 流水线阶段

- `Verify Toolchain`：确认 Docker daemon 和 Compose 可用。
- `Build`：用 `maven:3.9.9-eclipse-temurin-21` 容器执行 Maven，缓存写入工作区 `.m2`。
- `Validate Compose`：检查 `/opt/luntan/.env` 并验证 Compose 配置，不打印包含密码的展开配置。
- `Deploy`：执行 `docker compose up --build -d --remove-orphans`。
- `Health Check`：读取 `api-gateway` 容器健康状态，不依赖固定的宿主机端口。

流水线参数：

- `SKIP_TESTS`：紧急发布时可跳过测试，默认关闭。
- `DEPLOY`：只构建不部署时关闭，默认开启。

## 常用运维命令

在服务器项目目录执行：

```bash
docker compose ps
docker compose logs -f api-gateway identity-service forum-service
docker compose logs --tail=200 mysql redis
```

停止服务但保留数据：

```bash
docker compose down
```

不要在生产环境执行 `docker compose down -v`，它会删除 MySQL 和 Redis 数据卷。

首次上线前，先确认 `sub2api` 仍然可以使用，再通过 Nginx 域名访问本项目，最后执行注册、发帖、评论等冒烟测试。MySQL 数据卷还需要配置服务器级备份。
