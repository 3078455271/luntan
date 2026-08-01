# 论坛微服务后端

这是一个面向 Docker Desktop 的 Java 论坛后端 MVP，包含 API Gateway、用户认证服务和论坛内容服务。

## 技术栈

- Java 21
- Spring Boot 4.1.0
- Spring Cloud 2025.1.2
- MySQL 8.4 + Flyway
- Redis 8
- Maven 多模块构建

## 服务边界

- `api-gateway`：`8080`，路由、JWT 验签、CORS、Redis 限流和请求 ID。
- `identity-service`：容器内 `8081`，注册、登录、刷新令牌、退出、用户资料和内部用户摘要。
- `forum-service`：容器内 `8082`，板块、帖子、评论、点赞和分页搜索。
- MySQL：宿主机 `3306`，拥有独立的 `identity_db` 与 `forum_db`。
- Redis：宿主机 `6379`，用于限流、刷新会话和短 TTL 用户摘要缓存。

两个业务服务各自拥有数据库，业务表由服务启动时的 Flyway 迁移创建。论坛只保存 `author_id`，不会跨库建立外键。

## Windows + Docker Desktop 启动

前置条件：Docker Desktop 已启动，且启用 Linux containers；本机安装 Java 21 和 Maven 3.9+ 可用于本地构建。

```powershell
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
chcp 65001 > $null

Copy-Item .env.example .env
mvn test
mvn package -DskipTests

docker compose config
docker compose up --build -d
docker compose ps
```

首次启动会下载基础镜像和 Maven 依赖，可能需要几分钟。健康检查通过后，API 入口为 `http://localhost:8080`。

查看日志：

```powershell
docker compose logs -f api-gateway identity-service forum-service
docker compose logs --tail=200 mysql redis
```

停止服务但保留数据：

```powershell
docker compose down
```

连同 MySQL、Redis 数据卷一起重置：

```powershell
docker compose down -v
```

重置卷会删除本地开发数据，不能用于生产环境。

## 健康检查和 Swagger

- Gateway：`GET http://localhost:8080/actuator/health`
- Identity Swagger：容器内部 `http://identity-service:8081/swagger-ui.html`；本地直接运行 identity 服务时为 `http://localhost:8081/swagger-ui.html`
- Forum Swagger：容器内部 `http://forum-service:8082/swagger-ui.html`；本地直接运行 forum 服务时为 `http://localhost:8082/swagger-ui.html`
- 业务服务端口默认不映射到宿主机，外部请求通过 Gateway 访问。需要本机单独调试服务时，可临时增加 Compose 端口映射。

## API 冒烟流程

以下示例通过 Gateway 执行。PowerShell 中使用 `Invoke-RestMethod` 时，令牌不要写入日志或提交到文件。

1. 注册并获取令牌：

```powershell
$register = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register `
  -ContentType 'application/json' `
  -Body (@{
    username = 'demo_user'
    email = 'demo@example.com'
    password = 'password123'
    displayName = 'Demo User'
  } | ConvertTo-Json)
$accessToken = $register.accessToken
$headers = @{ Authorization = "Bearer $accessToken" }
```

2. 查询板块并创建帖子：

```powershell
$sections = Invoke-RestMethod -Method Get -Uri http://localhost:8080/api/sections
$sectionId = $sections[0].id
$post = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/posts `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{
    sectionId = $sectionId
    title = '第一篇帖子'
    content = '这是论坛的第一篇内容。'
  } | ConvertTo-Json)
$postId = $post.id
```

3. 评论、点赞和查询：

```powershell
$comment = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/posts/$postId/comments" `
  -Headers $headers -ContentType 'application/json' `
  -Body (@{ content = '第一条评论' } | ConvertTo-Json)

Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/posts/$postId/like" -Headers $headers
Invoke-RestMethod -Method Get -Uri "http://localhost:8080/api/posts/$postId" -Headers $headers
Invoke-RestMethod -Method Get -Uri 'http://localhost:8080/api/posts?page=0&size=20'
```

## 数据库只读核验

Docker Desktop 启动后，可用项目配置连接 MySQL。优先使用 `D:\MySQL\bin\mysql.exe`：

```powershell
$mysql = 'D:\MySQL\bin\mysql.exe'
& $mysql -h 127.0.0.1 -P 3306 -u root -p'root_password' -e 'SHOW DATABASES;'
& $mysql -h 127.0.0.1 -P 3306 -u identity_app -p'identity_password' identity_db -e 'SHOW TABLES; DESCRIBE users; SHOW INDEX FROM users; SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
& $mysql -h 127.0.0.1 -P 3306 -u forum_app -p'forum_password' forum_db -e 'SHOW TABLES; DESCRIBE posts; SHOW INDEX FROM posts; SELECT installed_rank,version,description,success FROM flyway_schema_history ORDER BY installed_rank;'
```

以上命令只读查询数据库事实。若本机没有该路径的 MySQL 客户端，可使用 Docker 中的客户端：

```powershell
docker compose exec mysql mysql -u root -p'root_password' -e 'SHOW DATABASES;'
```