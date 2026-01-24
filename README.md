# 投资信息分析系统 - 知乎数据抓取

基于 Spring Boot 和 Playwright 的知乎数据抓取命令行工具。

## 功能特性

- ✅ 使用 Playwright 浏览器自动化技术抓取知乎数据
- ✅ 支持抓取用户回答列表
- ✅ 支持通过链接抓取单个回答或文章
- ✅ 抓取作者参与的评论（父子关系展示）
- ✅ 保存为 Markdown 文件（含图片下载）
- ✅ Cookies 持久化登录
- ✅ 交互式命令行界面（Spring Shell）

## 技术栈

- Java 17
- Spring Boot 3.2.1
- Spring Shell 3.2.1
- Playwright 1.40.0（浏览器自动化）
- Maven 3.9.12

## 快速开始

### 1. 编译项目

```bash
mvn clean package -DskipTests
```

### 2. 运行应用

```bash
java -jar target/info-analyse-1.0.0.jar
```

### 3. 首次登录

```bash
shell:> zhihu-login
# 在打开的浏览器中登录知乎
shell:> zhihu-save-cookies
```

## 可用命令

### 查看帮助

```bash
zhihu-help
```

### 抓取用户回答

```bash
zhihu-user --user-id mr-dang-77 --limit 5 --save --with-comments
```

参数说明：
- `--user-id`: 知乎用户ID（必填）
- `--limit`: 抓取数量限制（默认10）
- `--save`: 保存为 Markdown 文件
- `--with-comments`: 同时抓取作者参与的评论
- `--show-browser`: 显示浏览器窗口

### 通过链接抓取回答或文章

```bash
# 抓取回答
zhihu-fetch --url https://www.zhihu.com/question/xxx/answer/yyy --save --with-comments

# 抓取文章
zhihu-fetch --url https://zhuanlan.zhihu.com/p/xxx --save --with-comments
```

参数说明：
- `--url`: 知乎回答或文章链接（必填）
- `--save`: 保存为 Markdown 文件
- `--with-comments`: 同时抓取作者参与的评论

### 退出应用

```bash
exit
```

## 输出文件

保存的文件位于 `output/<作者名>/` 目录：

```
output/
└── MR_Dang/
    ├── images/                    # 图片目录
    │   ├── 123456_1.jpg
    │   └── 123456_2.jpg
    ├── 123456_问题标题.md         # 回答文件
    └── article_789_文章标题.md    # 文章文件
```

Markdown 文件包含：
- 元信息（作者、点赞、评论数、时间、链接）
- 正文内容（图片已下载到本地）
- 作者互动评论（按父子关系展示）

## 项目结构

```
src/main/java/com/infoanalyse/
├── InfoAnalyseApplication.java
└── zhihu/
    ├── ZhihuCommand.java                    # 命令行接口
    ├── model/
    │   ├── ZhihuAnswer.java                 # 回答模型
    │   ├── ZhihuArticle.java                # 文章模型
    │   └── ZhihuComment.java                # 评论模型
    └── service/
        ├── ZhihuBrowserCrawlerService.java  # Playwright 爬虫服务
        └── AnswerSaveService.java           # 文件保存服务
```

## 注意事项

1. 本工具仅供学习研究使用
2. 请遵守知乎的使用条款
3. 建议合理控制抓取频率
4. 抓取的数据仅供个人学习使用

## 许可证

MIT License

## 作者

zhangpeng (1138139812@qq.com)
