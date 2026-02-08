# 投资信息分析系统

基于 Spring Boot + Playwright + MySQL 的投资信息聚合分析平台。自动抓取知乎/东方财富股吧内容，存入数据库，通过 DeepSeek AI 提炼投资线索，提供 iOS 风格的 Web 管理界面。

## 功能

- 知乎数据抓取：回答、文章、想法（Pin）、评论（含父子关系）
- 东方财富股吧：帖子列表、评论抓取
- 作者管理：通过知乎主页链接添加作者，一键同步动态
- 增量同步：自动跳过已保存内容
- DeepSeek AI 分析：自动提炼投资线索（可按作者开关）
- 扫码登录：Web 端展示二维码，手机扫码完成知乎登录
- iOS 风格 Web UI：移动端优先，支持内容浏览、任务管理、分页
- 内容阅读器：iOS 风格排版，评论折叠，AI 分析展示
- Docker 部署：一键打包部署到远程服务器

## 技术栈

| 组件 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot (WebFlux) | 3.2.1 |
| Playwright | 1.40.0 |
| MyBatis | 3.0.3 |
| MySQL | 8.0+ |
| DeepSeek API | deepseek-reasoner |
| CommonMark | 0.22.0 |
| Apache POI | 5.2.5 |

## 快速开始

### 1. 环境准备

- JDK 17+
- Maven 3.9+
- MySQL 8.0+（推荐 Docker 运行）
- Playwright 浏览器（首次运行自动下载）

### 2. 启动 MySQL

```bash
docker run -d --name info-analyse-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=info_analyse \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

### 3. 初始化表结构

```bash
# 等待 MySQL 启动完成后执行
docker exec -i info-analyse-mysql mysql -uroot -proot123 < src/main/resources/schema.sql
```

### 4. 配置 DeepSeek API Key（可选）

将 API Key 写入文件，路径在 `application.yml` 中配置：

```yaml
deepseek:
  api-key-file: D:/api.txt   # 修改为你的路径
```

### 5. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/info-analyse-1.0.0.jar
```

访问 http://localhost:8080

### 6. 开发模式快速重启（Windows PowerShell）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\dev-restart.ps1
```

## 数据库

</text>
</invoke>

连接信息（默认）：

| 参数 | 值 |
|------|------|
| Host | localhost:3306 |
| Database | info_analyse |
| Username | root |
| Password | root123 |

共 10 张表，DDL 见 `src/main/resources/schema.sql`：

| 表名 | 说明 |
|------|------|
| zhihu_answer | 知乎回答 |
| zhihu_article | 知乎文章 |
| zhihu_comment | 知乎评论（支持父子关系） |
| zhihu_pin | 知乎想法 |
| zhihu_author | 知乎作者管理（含 auto_analyze 开关） |
| guba_post | 东方财富股吧帖子 |
| guba_comment | 股吧评论 |
| crawl_image | 爬取图片记录 |
| crawl_task | 抓取任务记录 |
| ai_analysis | AI 分析结果 |

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/zhihu/status` | 登录状态 |
| POST | `/api/zhihu/login/qr/session` | 发起扫码登录 |
| GET | `/api/zhihu/login/qr/session/{id}` | 轮询扫码状态 |
| POST | `/api/zhihu/sync` | 同步用户动态 |
| POST | `/api/zhihu/fetch` | 抓取单个链接 |
| POST | `/api/zhihu/analyze` | AI 分析单篇内容 |
| GET | `/api/zhihu/authors` | 作者列表 |
| POST | `/api/zhihu/authors` | 添加作者 |
| PUT | `/api/zhihu/authors/{id}/auto-analyze` | 切换 AI 分析开关 |
| DELETE | `/api/zhihu/authors/{id}` | 删除作者 |
| GET | `/api/outputs` | 内容作者列表 |
| GET | `/api/outputs/{author}/files` | 作者内容列表 |
| DELETE | `/api/outputs/{path}` | 删除内容（级联删除评论和 AI 分析） |
| GET | `/api/tasks` | 任务列表 |
| GET | `/api/tasks/{id}` | 任务详情（含分阶段进度） |
| GET | `/view/zhihu/answer/{id}` | 查看回答（HTML） |
| GET | `/view/zhihu/article/{id}` | 查看文章（HTML） |
| GET | `/view/zhihu/pin/{id}` | 查看想法（HTML） |

## 项目结构

```
src/main/java/com/infoanalyse/
├── InfoAnalyseApplication.java          # 启动类
├── commons/service/
│   └── WordExportService.java           # Word 导出
├── dao/
│   ├── mapper/                          # MyBatis Mapper 接口
│   └── model/                           # DO 和 Example 类（MBG 生成）
├── eastmoney/
│   ├── model/                           # 股吧数据模型
│   └── service/                         # 股吧爬虫和存储服务
├── web/
│   ├── config/StaticResourceConfig.java # 静态资源配置
│   ├── controller/
│   │   ├── ZhihuApiController.java      # 知乎 API
│   │   ├── ZhihuAuthorController.java   # 作者管理 API
│   │   ├── GubaApiController.java       # 股吧 API
│   │   ├── OutputController.java        # 内容列表 API
│   │   ├── MarkdownViewController.java  # 内容阅读器（HTML 渲染）
│   │   └── TaskController.java          # 任务 API
│   └── task/
│       ├── TaskService.java             # 任务队列（单线程执行器）
│       └── TaskInfo.java                # 任务状态（含分阶段进度）
└── zhihu/
    ├── ZhihuCommand.java                # 核心业务逻辑
    ├── model/                           # 知乎数据模型
    └── service/
        ├── ZhihuBrowserCrawlerService.java  # Playwright 爬虫
        ├── ZhihuDbSaveService.java          # 数据库存储
        ├── DeepSeekService.java             # AI 分析服务
        └── AnswerSaveService.java           # 文件存储（旧）

src/main/resources/
├── application.yml                      # 应用配置
├── schema.sql                           # 数据库 DDL
├── mapper/*.xml                         # MyBatis XML 映射
└── static/index.html                    # 前端单页应用
```

## 配置说明

`src/main/resources/application.yml` 关键配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/info_analyse?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: root123

deepseek:
  api-key-file: D:/api.txt    # DeepSeek API Key 文件路径
  base-url: https://api.deepseek.com
  model: deepseek-reasoner

server:
  address: 0.0.0.0            # 监听所有网卡
```

注意：JDBC URL 中 charset 参数必须用 `characterEncoding=UTF-8`（Java charset 名），不能用 `utf8mb4`。

## Docker 部署

```powershell
# 部署到远程服务器（需要 PuTTY plink/pscp）
powershell -ExecutionPolicy Bypass -File scripts\deploy-docker.ps1 -Password "your-ssh-password"
```

部署脚本会：打包 → 上传 jar → 构建 Docker 镜像（基于 playwright/java） → 启动容器 → 健康检查。

## 注意事项

- 应用基于 WebFlux（Netty），不是 Tomcat
- 任务执行器为单线程（`zhihu-task-runner`），任务串行执行
- Playwright 首次运行会自动下载 Chromium 浏览器
- 知乎登录状态保存在 `zhihu_cookies.json` 文件中
- 抓取间隔 3-6 秒随机延迟，避免触发反爬

## 许可证

MIT License

---


## 🤖 AI 复现指南

> 本节面向 AI Agent（如 ChatGPT、Claude、Kiro 等），提供从零在本地复现运行本项目的完整步骤。

### 前置条件

- JDK 17+（推荐 Eclipse Temurin / Oracle JDK）
- Maven 3.9+
- Docker（用于运行 MySQL）
- Git
- （可选）DeepSeek API Key，用于 AI 分析功能

### Step 1: 克隆仓库

```bash
git clone <仓库地址>
cd info-analyse
```

### Step 2: 启动 MySQL 容器

```bash
docker run -d --name info-analyse-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=info_analyse \
  mysql:8.0 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

等待 MySQL 完全启动（约 10-20 秒），可用以下命令检查：

```bash
docker exec info-analyse-mysql mysqladmin ping -uroot -proot123
```

### Step 3: 初始化数据库表结构

执行 `src/main/resources/schema.sql`，共创建 10 张表：

```bash
docker exec -i info-analyse-mysql mysql -uroot -proot123 < src/main/resources/schema.sql
```

Windows CMD 下如果管道不可用，可以：

```cmd
docker exec -i info-analyse-mysql mysql -uroot -proot123 info_analyse < src\main\resources\schema.sql
```

10 张表清单：`zhihu_answer`、`zhihu_article`、`zhihu_comment`、`zhihu_pin`、`zhihu_author`、`guba_post`、`guba_comment`、`crawl_image`、`crawl_task`、`ai_analysis`。

### Step 4: 配置 application.yml

文件路径：`src/main/resources/application.yml`

关键配置项（默认值已可直接使用，无需修改）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/info_analyse?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: root123
```

⚠️ 注意：JDBC URL 中字符集参数必须用 `characterEncoding=UTF-8`（Java charset 名称），不能用 `utf8mb4`。

如需 AI 分析功能，将 DeepSeek API Key 写入一个文本文件，并配置路径：

```yaml
deepseek:
  api-key-file: D:/api.txt   # 修改为实际路径，文件内容仅一行 API Key
  base-url: https://api.deepseek.com
  model: deepseek-reasoner
```

如不需要 AI 分析，可跳过此步，不影响其他功能。

### Step 5: 编译打包

```bash
mvn clean package -DskipTests
```

产物：`target/info-analyse-1.0.0.jar`

### Step 6: 启动应用

```bash
java -jar target/info-analyse-1.0.0.jar
```

或使用 Maven 直接运行：

```bash
mvn spring-boot:run
```

应用启动后监听 `0.0.0.0:8080`。

### Step 7: 验证

浏览器访问 http://localhost:8080 ，应看到 iOS 风格的 Web 管理界面。

验证 API 可用：

```bash
curl http://localhost:8080/api/zhihu/status
```

### 补充说明

1. **Playwright 浏览器**：首次运行时 Playwright 会自动下载 Chromium，耗时约 1-3 分钟，无需手动安装。
2. **知乎登录**：通过 Web 界面扫码登录，登录状态保存在项目根目录的 `zhihu_cookies.json` 文件中，重启后自动加载。
3. **应用架构**：基于 Spring WebFlux（Netty），不是传统的 Tomcat Servlet 容器。
4. **任务执行**：使用单线程执行器（`zhihu-task-runner`），所有爬取和分析任务串行执行。
5. **端口占用**：如果 8080 端口被占用，需先释放。Windows 下可用 `netstat -ano | findstr :8080` 查找进程。
6. **开发模式重启**（Windows）：`powershell -ExecutionPolicy Bypass -File scripts\dev-restart.ps1`，会自动杀旧进程 → Maven 打包 → 启动 → 等待端口就绪。
