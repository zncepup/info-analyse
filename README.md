# 投资信息分析系统 - 知乎数据抓取

基于 Spring Boot 和 Playwright 的知乎数据抓取命令行工具。

## 功能特性

- ✅ 使用 Playwright 浏览器自动化技术抓取知乎数据
- ✅ 支持抓取用户回答列表
- ✅ 交互式命令行界面（Spring Shell）
- 🚧 评论抓取功能（开发中）
- 🚧 数据持久化（开发中）

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

或者使用提供的脚本：

```bash
run-test.bat
```

### 3. 使用命令

启动后会进入交互式命令行界面：

```
shell:> zhihu-help
```

查看所有可用命令。

## 可用命令

### 1. 查看帮助

```bash
zhihu-help
```

### 2. 抓取用户回答

```bash
zhihu-user --user-id mr-dang-77 --limit 5
```

参数说明：
- `--user-id`: 知乎用户ID（必填）
- `--limit`: 抓取数量限制（可选，默认10）

### 3. 抓取回答评论（开发中）

```bash
zhihu-comments --answer-id <回答ID> --limit 50
```

### 4. 退出应用

```bash
exit
```

## 首次运行说明

首次运行时，Playwright 会自动下载 Chromium 浏览器（约 150MB），这可能需要几分钟时间。下载完成后会自动开始抓取。

如果遇到浏览器下载问题，可以手动安装：

```bash
mvn exec:java -Dexec.mainClass="com.microsoft.playwright.CLI" -Dexec.args="install" -Dexec.classpathScope=compile
```

## 示例输出

```
shell:> zhihu-user --user-id mr-dang-77 --limit 3
正在使用浏览器抓取用户 mr-dang-77 的回答...
抓取完成！共获取 3 个回答

=== 回答 1 ===
问题: 如何看待某某投资机会？
作者: 党先生
点赞: 1234 | 评论: 56
链接: https://www.zhihu.com/question/xxx/answer/yyy
内容预览: 这是一个很好的问题...

...
```

## 项目结构

```
src/main/java/com/infoanalyse/
├── InfoAnalyseApplication.java          # 主应用入口
├── command/
│   └── ZhihuCommand.java                # 命令行接口
├── model/
│   ├── ZhihuAnswer.java                 # 回答数据模型
│   └── ZhihuComment.java                # 评论数据模型
└── service/
    ├── ZhihuBrowserCrawlerService.java  # Playwright 爬虫服务（当前使用）
    └── ZhihuCrawlerService.java         # API 爬虫服务（已废弃）
```

## 开发计划

- [x] 基础项目结构
- [x] 用户回答抓取功能
- [ ] 评论抓取功能
- [ ] 数据持久化（JSON/数据库）
- [ ] 数据分析功能
- [ ] 投资机会识别

## 注意事项

1. 本工具仅供学习研究使用
2. 请遵守知乎的使用条款和 robots.txt
3. 建议合理控制抓取频率，避免对服务器造成压力
4. 抓取的数据仅供个人学习使用，请勿用于商业用途

## 故障排除

### 问题：浏览器下载失败

解决方案：
1. 检查网络连接
2. 尝试使用代理
3. 手动下载浏览器（参见上文）

### 问题：抓取失败或数据为空

可能原因：
1. 知乎页面结构变化（需要更新选择器）
2. 网络问题
3. 用户ID不存在

解决方案：
1. 检查日志输出
2. 确认用户ID正确
3. 尝试减少 `--limit` 参数

## 许可证

MIT License

## 作者

zhangpeng (1138139812@qq.com)
