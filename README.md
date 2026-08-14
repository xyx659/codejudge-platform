# codejudge-platform

数智化编程考核与智能分析系统。面向高校编程类课程，提供在线编程环境、自动判卷、多维度反作弊、学情数据分析、课程题库管理等能力，覆盖平时作业、随堂测验、实验考核、期末机考全流程。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | Vue 3 + Vite + Vue Router + Pinia |
| 后端 | Java 17 + Spring Boot 3 + Spring Security |
| 数据 | MySQL（用户、提交元数据）+ MongoDB（题目、提交答案、AI 评审） |
| 评测 | Judge0 CE + Docker 沙箱 |
| AI | DeepSeek API 或本地千问大模型 |
| 部署 | Docker Compose，支持本地机房 / 云端双部署 |

## 目录结构

```text
codejudge-platform/
├── README.md                 # 项目说明（本文件）
├── docs/                     # 设计文档、数据库设计、申报书、会议记录等
├── scripts/                  # 构建、部署、运维脚本
├── frontend/                 # Vue 3 前端
│   ├── public/               # 静态资源
│   └── src/
│       ├── api/              # 后端接口封装（按角色分包）
│       │   ├── student/      # 学生端接口
│       │   ├── teacher/      # 教师端接口
│       │   └── admin/        # 管理端接口
│       ├── assets/           # 图片、样式等资源
│       ├── components/       # 公共组件 + 按角色组件
│       │   ├── student/
│       │   ├── teacher/
│       │   └── admin/
│       ├── router/           # 路由（按角色做权限路由）
│       ├── stores/           # Pinia 状态管理
│       ├── layouts/          # 角色布局
│       │   ├── student/
│       │   ├── teacher/
│       │   └── admin/
│       ├── views/            # 页面（按角色分包）
│       │   ├── student/      # 学生端：在线编程、提交、成绩反馈
│       │   ├── teacher/      # 教师端：题库、考试、学情、监考
│       │   └── admin/        # 管理端：用户、系统配置、审计
│       ├── App.vue
│       └── main.js
└── backend/                  # Java Spring Boot 后端
    └── src/
        ├── main/
        │   ├── java/com/codejudge/platform/
        │   │   ├── common/        # 通用工具、异常、统一返回
        │   │   ├── config/        # 配置类（Redis、CORS、WebSocket 等）
        │   │   ├── controller/    # REST 接口层（按角色分包）
        │   │   │   ├── student/   # 学生端接口
        │   │   │   ├── teacher/   # 教师端接口
        │   │   │   └── admin/     # 管理端接口
        │   │   ├── service/       # 业务服务层
        │   │   ├── repository/    # 数据访问层
        │   │   ├── entity/        # 数据库实体
        │   │   ├── dto/           # 请求/响应对象
        │   │   ├── security/      # 登录鉴权、RBAC
        │   │   └── CodejudgeApplication.java
        │   └── resources/
        │       ├── application.yml
        │       └── db/migration/  # 数据库迁移脚本
        └── test/java/com/codejudge/platform/  # 单元/集成测试
```

## 模块规划

- 用户端：学生端（在线编程、提交、成绩与 AI 反馈）、教师端（题库、考试、监考、学情）、管理端（用户、系统配置、审计）
- 网关与鉴权：统一请求入口，登录鉴权、RBAC 权限控制、限流、操作日志
- 评测链路：提交进入队列，Docker 沙箱内 Judge0 编译测试（黑盒），AI 白盒评审，结果实时推送
- 反作弊：切屏检测、全屏锁定、行为分析、代码查重
- 学情分析：成绩统计、能力画像、教学诊断，形成"教学 → 考核 → 学情分析 → 反馈改进"闭环

## 本地运行

环境要求：JDK 17、Maven 3.9+、Node.js 18+

### 后端（Spring Boot，端口 8080）

```bash
cd backend
mvn package -DskipTests
java -jar target/codejudge-backend-0.0.1-SNAPSHOT.jar
```

若系统默认 JDK 不是 17，可显式指定：

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn package -DskipTests
/usr/lib/jvm/java-17-openjdk-amd64/bin/java -jar target/codejudge-backend-0.0.1-SNAPSHOT.jar
```

验证接口：

```bash
curl http://localhost:8080/api/student/questions
curl http://localhost:8080/api/teacher/questions
curl http://localhost:8080/api/admin/users
```

### 前端（Vue 3 + Vite，端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 已配置 `/api` 代理到 `http://localhost:8080`，前后端可直接联通。三个端通过路由隔离，无共享入口页：

- 学生端：`/student/home`、`/student/scores`
- 教师端：`/teacher/home`、`/teacher/questions`
- 管理端：`/admin/home`、`/admin/users`

### 数据库（MySQL + MongoDB）

使用 Docker Compose 一键启动：

```bash
docker-compose up -d
```

- MySQL：端口 `3306`，数据库 `codejudge`，账号 `test / 123456`
- MongoDB：端口 `27017`，数据库 `codejudge`，账号 `test / 123456`

后端默认连接本机 `3306`/`27017`，也可通过环境变量覆盖：

```bash
MYSQL_HOST=localhost MYSQL_PORT=3306 MYSQL_USER=test MYSQL_PASSWORD=123456 \
MONGO_URI='mongodb://test:123456@localhost:27017/codejudge?authSource=admin' \
java -jar target/codejudge-backend-0.0.1-SNAPSHOT.jar
```

验证数据库连接：

```bash
curl http://localhost:8080/api/admin/db/check
```

正常返回 `mysql.status=ok`、`mongodb.status=ok`，并显示两个库的初始化数据量。

详细库表结构见 [docs/database-design.md](docs/database-design.md)。

## 下一步

1. 补充数据库迁移脚本与更多业务实体
2. 接入 Spring Security，实现登录鉴权与 RBAC 权限控制
3. 将已完成的 Java 原型 demo 按后端分层迁移到 Spring Boot 项目中
4. 补充 Judge0 评测链路、AI 评审、反作弊与学情分析模块
