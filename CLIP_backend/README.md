<p align="center">
  <a href="#english">🇺🇸 English</a> • 
  <a href="#chinese">🇨🇳 中文</a>
</p>

---

# 🇺🇸 English Version {#english}

## Project Overview

**CLIP_JAVA** is a photo gallery management system with AI-powered image-text matching and classification capabilities. It provides a RESTful API backend built on Spring Boot that allows users to upload images with natural language descriptions, then leverages an external AI service (CLIP-based) to automatically match images to descriptions and perform category classification.

The project solves the problem of manual photo categorization by combining: (1) a traditional user/photo management system, (2) a 30-day recycle bin with Redis-backed expiration, and (3) AI-driven cross-modal image-text alignment.

---

## Architecture

This is a **multi-module Maven project** with three sub-modules following a clean separation of concerns:

```
CLIP_JAVA/
├── demo-pojo/          # Data layer: entities, DTOs, VO, query objects
├── common/             # Shared utilities: JWT, image storage helpers
└── Management/         # Main application: controllers, services, mappers, config
    └── src/main/
        ├── java/com/hw/manage/
        │   ├── Config/         # Security, Redis, File, Web, Rest config
        │   ├── Controller/     # REST endpoints (user, photos, album)
        │   ├── Service/        # Business logic interfaces + impl
        │   ├── Mapper/         # MyBatis data access layer
        │   └── filter/         # JWT authentication filter
        └── resources/
            ├── application*.yml    # Multi-profile config
            ├── logback.xml         # Logging config
            └── *.Mapper/*.xml      # MyBatis SQL mappings
```

### Data Flow

```
Client (Postman/Frontend)
    │
    ▼
Spring Security + JWT Filter (auth)
    │
    ▼
REST Controllers (/user/*)
    │
    ▼
Service Layer (business logic, @Transactional)
    │
    ├──▶ MyBatis Mapper ──▶ MySQL (users, photos, descriptions)
    ├──▶ Redis (recycle bin Sorted Set, match caching)
    └──▶ RestTemplate ──▶ AI Service (localhost:5000)
```

### Key Interactions

1. **Auth Flow**: User registers/logs in → server returns JWT → all subsequent requests include `Authorization: Bearer <token>` → `JwtRequestFilter` validates per request
2. **Category Upload Flow**: Client sends images + JSON descriptions → server stores images/files + DB records → calls AI service `/upload` endpoint → AI processes cross-modal matching asynchronously
3. **Recycle Bin Flow**: Delete photo → DB soft-delete → image metadata stored in Redis Sorted Set with 30-day TTL score → expired items auto-cleaned on list → permanent delete removes from both Redis and filesystem

---

## Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Java 23 |
| **Framework** | Spring Boot 3.5.6 |
| **Security** | Spring Security + JWT (jjwt 0.9.1) |
| **ORM** | MyBatis 3.x + PageHelper |
| **Database** | MySQL 8.x (photo_system) |
| **Cache** | Redis 7.x (Sorted Set for recycle bin, key-value for match cache) |
| **API Docs** | Knife4j (Swagger enhancement) |
| **Utilities** | Hutool 5.8, Lombok, Jackson |
| **Build** | Maven (multi-module) |
| **External** | AI Python service (CLIP-based, `localhost:5000`) |

---

## Features

- **JWT-based Authentication** — Stateless session with Spring Security; login/register returns signed token with 12-hour expiration
- **Photo CRUD + Recycle Bin** — Upload, paginated list, soft-delete to bin; Redis-backed 30-day auto-expiration with cleanup
- **AI Image-Text Matching** — Submit natural language descriptions; AI service matches them against uploaded images via cross-modal embeddings
- **Batch Category Classification** — Upload multiple images with a JSON description list; AI service assigns each image to the best-matching category
- **User Profile Management** — Update account info with password re-encryption
- **Multi-profile Configuration** — Separate `application-local.yml` and `application-dev.yml` for environment isolation

---

## Installation

### Prerequisites

- JDK 23
- Maven 3.8+
- MySQL 8.x
- Redis 7.x
- AI matching service running at `localhost:5000` (Python/CLIP)

### Setup

```bash
# 1. Clone the repository
git clone <repo-url>
cd CLIP_JAVA

# 2. Configure environment variables (DB/Redis credentials)
# Edit Management/src/main/resources/application-local.yml
#   hw.db.sql.host=<your-mysql-host>
#   hw.db.sql.pw=<your-mysql-password>
#   hw.db.redis.host=<your-redis-host>
#   hw.db.redis.pw=<your-redis-password>

# 3. Initialize the database
mysql -u root -p < schema.sql   # create photo_system DB and tables

# 4. Build the project
mvn clean install -DskipTests

# 5. Run the application
cd Management
mvn spring-boot:run
```

The server starts on **`http://localhost:8080`**.  
API documentation available at **`http://localhost:8080/doc.html`** (Knife4j).

---

## Usage

```bash
# Login and get JWT token
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# Upload images with descriptions for category matching
curl -X POST http://localhost:8080/user/category/upload \
  -H "Authorization: Bearer <token>" \
  -F "username=test" \
  -F "idNum=1" \
  -F 'descriptionList=["a cat","a dog","a car"]' \
  -F "photoList=@cat.jpg" \
  -F "photoList=@dog.jpg" \
  -F "photoList=@car.jpg"

# Get paginated photo list
curl -X GET http://localhost:8080/user/photos/list \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"page":1,"pagesize":10}'
```

---

## Design Highlights

### Why This Architecture?

- **Multi-module Maven** — Separates POJOs, utilities, and the main app into independent build artifacts. The `common` and `demo-pojo` modules can be reused by other services (e.g., the AI service could consume the same DTOs).
- **Stateless JWT + Spring Security** — No server-side session storage; each request is independently authenticated. This naturally scales horizontally when placed behind a load balancer.
- **Service/Impl pattern** — Interfaces in the `Service` package with implementations in `Service.impl` allow easy mocking for tests and future implementation swapping.

### Trade-offs Considered

| Decision | Alternative | Rationale |
|----------|-------------|-----------|
| Redis Sorted Set for recycle bin | Database flag column | Redis provides O(log N) expiration scans; avoids adding indices to DB |
| BCrypt password encoding | Argon2/scrypt | BCrypt is the Spring Security default with mature ecosystem support |
| Knife4j over SpringDoc | SpringDoc OpenAPI | Knife4j provides a richer Chinese-localized UI; preferred in this ecosystem |
| Synchronous AI call on upload | Message queue (RabbitMQ/Kafka) | Simpler architecture; acceptable for moderate throughput. MQ would be better at scale |

### Scalability Considerations

- Currently the AI service call is **synchronous** — the upload endpoint blocks until the AI service responds. For high throughput, this should be converted to async with a message queue.
- JWT tokens use a **hardcoded signing key** (`kjson108241`) — this should be externalized to environment variables or a secrets manager.
- The file storage uses a **local directory** (`D:/web/uploads/images`) — for production, migrate to OSS (e.g., Alibaba Cloud OSS, AWS S3).

---

## Database Schema

| Table | Purpose |
|-------|---------|
| `users` | User accounts (username, password, phone) |
| `photos` | Uploaded images (path, UUID name, user FK) |
| `description` | Text descriptions (content, user FK, batch idNum) |
| `photo_description` | Cross-modal match results (photo_id ↔ description_id) |

---

## API Overview

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/user/login` | No | User login, returns JWT |
| POST | `/user/register` | No | User registration |
| POST | `/user/sendmsg` | No | Send verification code (WIP) |
| PUT | `/user/change` | Yes | Update user profile |
| POST | `/user/match/upload` | Yes | Upload text description for matching |
| GET | `/user/match/download` | Yes | Download match results |
| POST | `/user/category/upload` | Yes | Batch upload images + descriptions |
| GET | `/user/category/download` | Yes | Download classification results |
| GET | `/user/photos/list` | Yes | Paginated photo list |
| GET | `/user/photos/binlist` | Yes | List recycle bin (30-day window) |
| DELETE | `/user/photos/{url}` | Yes | Soft-delete photo to recycle bin |
| DELETE | `/user/photos/bin/{url}` | Yes | Permanent delete from bin + filesystem |
| POST | `/user/album/add` | Yes | Add to album (TODO) |
| GET | `/user/album/get` | Yes | Get album photos (TODO) |
| DELETE | `/user/album/delete/{id}` | Yes | Delete from album (TODO) |

---

## Future Work

- [ ] **Async AI Processing** — Introduce RabbitMQ/Kafka to decouple upload from AI inference; return immediate "processing" status
- [ ] **Album Feature** — Complete the `albumController` implementation (currently skeleton)
- [ ] **Externalize Secrets** — Move JWT signing key, DB passwords to environment variables or Vault
- [ ] **OSS Migration** — Replace local filesystem storage with cloud object storage (OSS/S3/MinIO)
- [ ] **Unit & Integration Tests** — Currently no tests; add Spring Boot Test + Testcontainers for DB/Redis
- [ ] **Rate Limiting** — Add request rate limiting on login and upload endpoints
- [ ] **Containerization** — Add Dockerfile + docker-compose for one-command deployment
- [ ] **Frontend** — Build a web UI (Vue/React) consuming this API

---

# 🇨🇳 中文版本 {#chinese}

## 项目简介

**CLIP_JAVA** 是一个基于 AI 的图库管理系统，支持图文匹配与自动分类。项目采用 Spring Boot 构建 RESTful API 后端，用户可通过自然语言描述上传图片，系统调用外部 AI 服务（基于 CLIP 模型）自动将图片与描述进行跨模态匹配，完成智能分类。

项目核心价值在于：将传统用户/图片管理、30 天回收站（Redis 自动过期）、以及 AI 驱动的图文对齐三者有机结合，解决人工分类照片的低效问题。

---

## 系统架构

**多模块 Maven 项目**，按职责清晰拆分：

```
CLIP_JAVA/
├── demo-pojo/          # 数据层：Entity、DTO、VO、Query 对象
├── common/             # 公共工具：JWT 工具类、图片存储工具类
└── Management/         # 主应用：Controller、Service、Mapper、Config
    └── src/main/
        ├── java/com/hw/manage/
        │   ├── Config/         # Security、Redis、文件、RestTemplate 等配置
        │   ├── Controller/     # REST 接口（userController, PhotosController, albumController）
        │   ├── Service/        # 业务逻辑接口与实现
        │   ├── Mapper/         # MyBatis 数据访问层
        │   └── filter/         # JWT 认证过滤器
        └── resources/
            ├── application*.yml    # 多环境配置
            └── com.hw.manage.Mapper/*.xml  # MyBatis SQL 映射
```

### 数据流动

```
客户端 (Postman/前端)
    │
    ▼
Spring Security + JWT 过滤器（身份认证）
    │
    ▼
REST Controllers（/user/*）
    │
    ▼
Service 层（业务逻辑，事务管理 @Transactional）
    │
    ├──▶ MyBatis Mapper ──▶ MySQL（users, photos, description, photo_description）
    ├──▶ Redis（回收站 Sorted Set + 匹配结果缓存）
    └──▶ RestTemplate ──▶ AI 服务（localhost:5000，基于 CLIP 的图文匹配）
```

---

## 技术栈

| 类别 | 技术 |
|------|------|
| **编程语言** | Java 23 |
| **框架** | Spring Boot 3.5.6 |
| **安全认证** | Spring Security + JWT（jjwt 0.9.1） |
| **ORM** | MyBatis 3.x + PageHelper 分页 |
| **数据库** | MySQL 8.x（photo_system 库） |
| **缓存** | Redis 7.x（有序集合管理回收站，键值对缓存匹配结果） |
| **API 文档** | Knife4j（Swagger 增强） |
| **工具库** | Hutool 5.8、Lombok、Jackson |
| **构建工具** | Maven（多模块） |
| **外部服务** | AI Python 端（CLIP 模型，`localhost:5000`） |

---

## 核心功能

- **JWT 认证鉴权** — 无状态会话 + Spring Security；登录/注册返回签名 Token，12 小时有效期
- **图片增删改查 + 回收站** — 上传/分页列表/软删除至回收站；Redis 有序集合管理 30 天自动过期与清理
- **AI 图文匹配** — 提交自然语言描述，AI 服务基于跨模态嵌入将图片与描述进行匹配
- **批量分类上传** — 同时上传多张图片与 JSON 描述列表，AI 服务自动为每张图片匹配最佳分类
- **用户信息管理** — 修改账户信息，密码自动重新加密
- **多环境配置** — 区分 `application-local.yml` 与 `application-dev.yml`，环境隔离

---

## 安装方法

### 环境要求

- JDK 23
- Maven 3.8+
- MySQL 8.x
- Redis 7.x
- AI 匹配服务运行于 `localhost:5000`（Python / CLIP）

### 安装步骤

```bash
# 1. 克隆仓库
git clone <repo-url>
cd CLIP_JAVA

# 2. 配置环境变量（数据库 / Redis 连接信息）
# 编辑 Management/src/main/resources/application-local.yml
#   hw.db.sql.host=<你的MySQL主机>
#   hw.db.sql.pw=<你的MySQL密码>
#   hw.db.redis.host=<你的Redis主机>
#   hw.db.redis.pw=<你的Redis密码>

# 3. 初始化数据库
mysql -u root -p < schema.sql   # 创建 photo_system 库及表结构

# 4. 构建项目
mvn clean install -DskipTests

# 5. 启动应用
cd Management
mvn spring-boot:run
```

服务默认运行在 **`http://localhost:8080`**。  
API 文档地址：**`http://localhost:8080/doc.html`**（Knife4j 界面）。

---

## 使用方法

```bash
# 登录获取 JWT Token
curl -X POST http://localhost:8080/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 批量上传图片并分类
curl -X POST http://localhost:8080/user/category/upload \
  -H "Authorization: Bearer <token>" \
  -F "username=test" \
  -F "idNum=1" \
  -F 'descriptionList=["一只猫","一只狗","一辆车"]' \
  -F "photoList=@cat.jpg" \
  -F "photoList=@dog.jpg" \
  -F "photoList=@car.jpg"

# 分页获取图片列表
curl -X GET http://localhost:8080/user/photos/list \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"page":1,"pagesize":10}'
```

---

## 设计亮点

### 架构选择理由

- **多模块 Maven 结构** — POJO、工具类、主应用独立构建。`common` 和 `demo-pojo` 可被其他服务（如 AI 端）直接复用，避免重复定义。
- **无状态 JWT 认证** — 无需服务端 Session，每个请求独立校验，天然支持水平扩展和负载均衡。
- **Service/Impl 分层** — 接口与实现分离，便于单元测试 Mock 和未来实现替换。

### 方案权衡

| 决策 | 替代方案 | 选择理由 |
|------|----------|----------|
| Redis 有序集合管理回收站 | 数据库标记字段 | Redis 提供 O(log N) 的过期扫描；避免为回收站增加额外索引 |
| BCrypt 密码加密 | Argon2/scrypt | BCrypt 是 Spring Security 默认方案，生态成熟 |
| Knife4j 文档 | SpringDoc OpenAPI | Knife4j 提供更丰富的中文 UI 界面，更适合当前生态 |
| 同步调用 AI 服务 | 消息队列（RabbitMQ/Kafka） | 架构更简单；中等并发量下可接受，高并发需升级为异步 |

### 可扩展性考量

- 当前 AI 调用为**同步方式**，上传接口会阻塞等待 AI 响应。高并发场景建议引入消息队列改为异步处理。
- JWT 签名密钥目前**硬编码**（`kjson108241`），生产环境应迁移至环境变量或密钥管理服务。
- 文件存储使用**本地磁盘路径**（`D:/web/uploads/images`），生产环境应迁移至对象存储（如阿里云 OSS、AWS S3）。

---

## 数据库表结构

| 表名 | 用途 |
|------|------|
| `users` | 用户账户（用户名、密码、手机号） |
| `photos` | 上传图片（存储路径、UUID 名、用户外键） |
| `description` | 文本描述（内容、用户外键、批次 idNum） |
| `photo_description` | 图文匹配结果（photo_id ↔ description_id） |

---

## API 概览

| 方法 | 端点 | 认证 | 说明 |
|------|------|------|------|
| POST | `/user/login` | 否 | 用户登录，返回 JWT |
| POST | `/user/register` | 否 | 用户注册 |
| POST | `/user/sendmsg` | 否 | 发送验证码（待完成） |
| PUT | `/user/change` | 是 | 修改用户信息 |
| POST | `/user/match/upload` | 是 | 上传文本描述进行匹配 |
| GET | `/user/match/download` | 是 | 下载匹配结果 |
| POST | `/user/category/upload` | 是 | 批量上传图片与描述进行分类 |
| GET | `/user/category/download` | 是 | 下载分类结果 |
| GET | `/user/photos/list` | 是 | 分页获取图片列表 |
| GET | `/user/photos/binlist` | 是 | 查看回收站（30 天内） |
| DELETE | `/user/photos/{url}` | 是 | 软删除图片至回收站 |
| DELETE | `/user/photos/bin/{url}` | 是 | 从回收站永久删除（含文件系统） |
| POST | `/user/album/add` | 是 | 添加到相册（TODO） |
| GET | `/user/album/get` | 是 | 获取相册（TODO） |
| DELETE | `/user/album/delete/{id}` | 是 | 从相册删除（TODO） |

---

## 未来工作

- [ ] **异步 AI 处理** — 引入 RabbitMQ / Kafka 解耦上传与推理，上传后立即返回"处理中"
- [ ] **相册功能完善** — `albumController` 当前仅为骨架代码
- [ ] **密钥外部化** — 将 JWT 签名密钥、数据库密码迁移至环境变量或 Vault
- [ ] **对象存储迁移** — 将本地文件存储替换为 OSS / S3 / MinIO
- [ ] **单元测试与集成测试** — 当前无测试代码；补充 Spring Boot Test + Testcontainers
- [ ] **接口限流** — 对登录和上传接口添加请求频率限制
- [ ] **容器化部署** — 编写 Dockerfile + docker-compose 实现一键部署
- [ ] **前端界面** — 开发 Vue / React Web 前端对接此 API
