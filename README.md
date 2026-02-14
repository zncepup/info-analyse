# 投资信息分析系统

基于 Spring Boot + Playwright + MySQL 的投资信息聚合分析平台。自动抓取知乎/东方财富股吧内容，存入数据库，通过 DeepSeek AI 提炼投资线索并分类评论，提供 iOS 风格的 Web 管理界面。

## 功能概览

**数据采集**
- 知乎数据抓取：回答、文章、想法（Pin）、评论（含父子关系）
- 东方财富股吧：帖子列表、评论抓取
- 作者管理：通过知乎主页链接添加作者，一键同步动态
- 增量同步：自动跳过已保存内容，评论支持增量同步（只新增不删除）
- 扫码登录：Web 端展示二维码，手机扫码完成知乎登录

**AI 分析**
- DeepSeek AI 自动提炼投资线索（可按作者开关）
- 评论投资相关性分类：AI 自动判断评论线程是否与投资相关
- 批量历史评论分类：一次性对所有未分类评论进行投资相关性标注
- 评论区 AI 分析：对投资相关评论线程做进一步投资线索提炼

**Web 界面**
- iOS 风格 Web UI：移动端优先，支持内容浏览、任务管理、分页
- 内容阅读器：iOS 风格排版，评论折叠（仅展示投资相关评论），AI 分析展示
- 全局关键字搜索：跨文章、回答、想法、评论、AI 分析结果搜索，按内容维度聚合展示
- 页内搜索：从搜索结果点入详情页后，自动高亮关键词并支持上下导航
- Word 导出：将内容及 AI 分析导出为 Word 文档

**部署**
- Docker 部署：一键打包部署到远程服务器
- 本地 Docker 运行：本地容器化运行
- 数据迁移：从旧版 Markdown 文件迁移到数据库

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
docker exec -i info-analyse-mysql mysql -uroot -proot123 info_analyse < src/main/resources/schema.sql
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

自动杀旧进程 → Maven 打包 → 启动 → 等待端口就绪。

## 数据库

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
| zhihu_comment | 知乎评论（支持父子关系，含 invest_related 分类标记） |
| zhihu_pin | 知乎想法 |
| zhihu_author | 知乎作者管理（含 auto_analyze 开关） |
| guba_post | 东方财富股吧帖子 |
| guba_comment | 股吧评论 |
| crawl_image | 爬取图片记录 |
| crawl_task | 抓取任务记录 |
| ai_analysis | AI 分析结果（investment_clue / comment_investment_clue） |

## API 接口

### 知乎相关

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/zhihu/status` | 登录状态 |
| POST | `/api/zhihu/login/qr/session` | 发起扫码登录 |
| GET | `/api/zhihu/login/qr/session/{id}` | 轮询扫码状态 |
| POST | `/api/zhihu/login/qr/session/{id}/cancel` | 取消扫码登录 |
| POST | `/api/zhihu/sync` | 同步用户动态（增量） |
| POST | `/api/zhihu/fetch` | 抓取单个链接 |
| POST | `/api/zhihu/analyze` | AI 分析单篇内容 |
| POST | `/api/zhihu/re-analyze` | 重新 AI 分析（删除旧结果后重新分析） |
| POST | `/api/zhihu/re-crawl-comments` | 同步增量评论（只新增不删除） |
| POST | `/api/zhihu/export-word` | 导出 Word 文档 |

### 作者管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/zhihu/authors` | 作者列表 |
| POST | `/api/zhihu/authors` | 添加作者 |
| PUT | `/api/zhihu/authors/{id}/auto-analyze` | 切换 AI 分析开关 |
| DELETE | `/api/zhihu/authors/{id}` | 删除作者 |

### 股吧相关

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/guba/crawl` | 抓取股吧帖子列表 |
| POST | `/api/guba/detail` | 抓取单个帖子详情及评论 |

### 内容与搜索

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/outputs` | 内容作者列表 |
| GET | `/api/outputs/{author}/files` | 作者内容列表 |
| DELETE | `/api/outputs/{path}` | 删除内容（级联删除评论和 AI 分析） |
| GET | `/api/search?q=关键词&limit=50` | 全局关键字搜索（跨 7 张表） |

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tasks` | 任务列表 |
| GET | `/api/tasks/{id}` | 任务详情（含分阶段进度） |

### 数据迁移

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/migrate` | 迁移 output/ 目录 Markdown 文件到数据库 |
| POST | `/api/migrate/classify-comments` | 批量分类历史评论（投资相关性） |

### 内容阅读器（HTML）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/view/zhihu/answer/{id}` | 查看回答 |
| GET | `/view/zhihu/article/{id}` | 查看文章 |
| GET | `/view/zhihu/pin/{id}` | 查看想法 |
| GET | `/view/guba/post/{id}` | 查看股吧帖子 |

阅读器支持 `?q=关键词` 参数，自动高亮并提供页内搜索导航。

### 日志

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logs?lines=200` | 获取最近 N 行应用日志 |

## 项目结构

```
src/main/java/com/infoanalyse/
├── InfoAnalyseApplication.java          # 启动类
├── commons/service/
│   └── WordExportService.java           # Word 导出
├── dao/
│   ├── mapper/                          # MyBatis Mapper 接口（含 SearchMapper）
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
│   │   ├── SearchController.java        # 全局搜索 API
│   │   ├── MarkdownViewController.java  # 内容阅读器（HTML 渲染）
│   │   ├── MigrationController.java     # 数据迁移 API
│   │   ├── LogController.java           # 日志 API
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
        └── DeepSeekService.java             # AI 分析服务

src/main/resources/
├── application.yml                      # 应用配置
├── schema.sql                           # 数据库 DDL
├── mapper/*.xml                         # MyBatis XML 映射（含 SearchMapper.xml）
└── static/
    ├── index.html                       # 前端单页应用
    └── page-search.js                   # 页内搜索高亮与导航

scripts/
├── dev-restart.ps1                      # 开发模式快速重启
├── deploy-docker.ps1                    # 远程 Docker 部署
└── run-local-docker.ps1                 # 本地 Docker 运行
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

### 远程服务器部署

```powershell
powershell -ExecutionPolicy Bypass -File scripts\deploy-docker.ps1 -Password "your-ssh-password"
```

部署流程：Maven 打包 → 上传 jar → 构建 Docker 镜像（基于 playwright/java） → 启动容器 → 健康检查。

需要 PuTTY（plink/pscp）。可通过参数自定义服务器 IP、端口、远程路径等。

### 本地 Docker 运行

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-local-docker.ps1
```

本地构建镜像并启动容器，挂载 `.local-docker/` 目录用于数据持久化。

## 注意事项

- 应用基于 WebFlux（Netty），不是 Tomcat
- 任务执行器为单线程（`zhihu-task-runner`），任务串行执行
- Playwright 首次运行会自动下载 Chromium 浏览器（约 1-3 分钟）
- 知乎登录状态保存在 `zhihu_cookies.json` 文件中，重启后自动加载
- 抓取间隔 3-6 秒随机延迟，避免触发反爬
- 评论分类使用 `invest_related` 字段：1=投资相关，0=无关，NULL=未分类
- AI 分析类型：`investment_clue`（内容投资线索）、`comment_investment_clue`（评论投资线索）

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

```bash
docker exec -i info-analyse-mysql mysql -uroot -proot123 info_analyse < src/main/resources/schema.sql
```

Windows CMD 下如果管道不可用：

```cmd
docker exec -i info-analyse-mysql mysql -uroot -proot123 info_analyse < src\main\resources\schema.sql
```

### Step 4: 配置 application.yml

文件路径：`src/main/resources/application.yml`

默认配置可直接使用，无需修改。如需 AI 分析功能，将 DeepSeek API Key 写入文本文件并配置路径：

```yaml
deepseek:
  api-key-file: D:/api.txt   # 文件内容仅一行 API Key
```

⚠️ JDBC URL 中字符集参数必须用 `characterEncoding=UTF-8`，不能用 `utf8mb4`。

### Step 5: 编译运行

```bash
mvn clean package -DskipTests
java -jar target/info-analyse-1.0.0.jar
```

### Step 6: 验证

浏览器访问 http://localhost:8080 ，应看到 iOS 风格的 Web 管理界面。

```bash
curl http://localhost:8080/api/zhihu/status
```

### 补充说明

1. Playwright 首次运行自动下载 Chromium，无需手动安装
2. 知乎登录通过 Web 界面扫码，登录状态保存在 `zhihu_cookies.json`
3. 应用基于 Spring WebFlux（Netty），非 Tomcat
4. 单线程任务执行器，所有爬取和分析任务串行执行
5. 开发模式重启：`powershell -ExecutionPolicy Bypass -File scripts\dev-restart.ps1`
