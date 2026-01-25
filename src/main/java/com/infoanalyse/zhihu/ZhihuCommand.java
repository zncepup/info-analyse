package com.infoanalyse.zhihu;

import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuArticle;
import com.infoanalyse.zhihu.model.ZhihuComment;
import com.infoanalyse.zhihu.service.AnswerSaveService;
import com.infoanalyse.zhihu.service.DeepSeekService;
import com.infoanalyse.commons.service.WordExportService;
import com.infoanalyse.zhihu.service.ZhihuBrowserCrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 知乎相关的命令行接口
 */
@ShellComponent
public class ZhihuCommand {

    @Autowired
    private ZhihuBrowserCrawlerService zhihuBrowserCrawlerService;
    
    @Autowired
    private AnswerSaveService answerSaveService;
    
    @Autowired
    private DeepSeekService deepSeekService;
    
    @Autowired
    private WordExportService wordExportService;

    /**
     * 打开浏览器让用户登录知乎
     */
    @ShellMethod(value = "打开浏览器登录知乎", key = "zhihu-login")
    public String login() {
        try {
            System.out.println("正在打开浏览器...");
            
            zhihuBrowserCrawlerService.setHeadless(false);
            zhihuBrowserCrawlerService.openBrowserForLogin();
            
            return "浏览器已打开，请登录知乎。登录成功后执行 zhihu-save-cookies 保存登录状态。";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "打开浏览器失败: " + e.getMessage();
        }
    }
    
    /**
     * 保存登录 cookies
     */
    @ShellMethod(value = "保存知乎登录状态", key = "zhihu-save-cookies")
    public String saveCookies() {
        try {
            zhihuBrowserCrawlerService.saveCookies();
            return "登录状态已保存！下次可以直接使用 zhihu-user 抓取数据。";
        } catch (Exception e) {
            return "保存失败: " + e.getMessage();
        }
    }

    /**
     * 抓取指定用户的回答
     */
    @ShellMethod(value = "抓取知乎用户的回答", key = "zhihu-user")
    public String crawlUserAnswers(
            @ShellOption(value = "--user-id", help = "知乎用户ID") String userId,
            @ShellOption(value = "--limit", help = "抓取数量限制", defaultValue = "10") int limit,
            @ShellOption(value = "--show-browser", help = "显示浏览器窗口", defaultValue = "false") boolean showBrowser,
            @ShellOption(value = "--save", help = "保存回答为 Markdown 文件", defaultValue = "false") boolean save,
            @ShellOption(value = "--with-comments", help = "同时抓取作者参与的评论", defaultValue = "false") boolean withComments) {
        
        try {
            if (showBrowser) {
                System.out.println("将打开浏览器窗口，如需登录请在浏览器中完成...");
                zhihuBrowserCrawlerService.setHeadless(false);
            } else {
                zhihuBrowserCrawlerService.setHeadless(true);
            }
            
            System.out.println("正在使用浏览器抓取用户 " + userId + " 的回答...");
            
            List<ZhihuAnswer> answers = zhihuBrowserCrawlerService.crawlUserAnswers(userId, limit);
            
            System.out.println("抓取完成！共获取 " + answers.size() + " 个回答");
            System.out.println();
            
            // 如果需要抓取评论
            if (withComments) {
                System.out.println("正在抓取作者参与的评论...");
                
                for (ZhihuAnswer answer : answers) {
                    if (answer.getCommentCount() > 0 && answer.getAuthorId() != null) {
                        try {
                            System.out.println("  抓取回答 " + answer.getId() + " 的评论...");
                            List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlAnswerComments(
                                    answer.getId(), answer.getAuthorId());
                            answer.setComments(comments);
                            System.out.println("    获取 " + comments.size() + " 条作者互动评论");
                        } catch (Exception e) {
                            System.out.println("    评论抓取失败: " + e.getMessage());
                        }
                    }
                }
            }
            
            // 显示回答详情
            for (int i = 0; i < answers.size(); i++) {
                ZhihuAnswer answer = answers.get(i);
                System.out.printf("=== 回答 %d ===%n", i + 1);
                System.out.printf("问题: %s%n", answer.getQuestionTitle());
                System.out.printf("作者: %s%n", answer.getAuthorName());
                System.out.printf("点赞: %d | 评论: %d%n", answer.getVoteupCount(), answer.getCommentCount());
                System.out.printf("链接: %s%n", answer.getUrl());
                if (answer.getContent() != null && !answer.getContent().isEmpty()) {
                    String preview = answer.getContent().length() > 100 ? 
                        answer.getContent().substring(0, 100) + "..." : answer.getContent();
                    System.out.printf("内容预览: %s%n", preview);
                }
                if (answer.getComments() != null && !answer.getComments().isEmpty()) {
                    System.out.printf("作者互动评论: %d 条%n", answer.getComments().size());
                }
                System.out.println();
            }
            
            // 保存为文件
            if (save) {
                System.out.println("正在保存回答为 Markdown 文件...");
                
                // 使用第一个回答的作者名称作为文件夹名
                String authorFolder = answers.isEmpty() ? userId : 
                    (answers.get(0).getAuthorName() != null ? answers.get(0).getAuthorName() : userId);
                
                if (withComments) {
                    int savedCount = 0;
                    for (ZhihuAnswer answer : answers) {
                        try {
                            answerSaveService.saveAnswer(answer, authorFolder);
                            savedCount++;
                        } catch (Exception e) {
                            System.out.println("保存失败: " + answer.getId() + " - " + e.getMessage());
                        }
                    }
                    System.out.println("保存 " + savedCount + " 个文件到 output/" + authorFolder + "/ 目录（含评论）");
                } else {
                    int existingCount = answerSaveService.getSavedAnswerIds(authorFolder).size();
                    List<Path> savedFiles = answerSaveService.saveAnswers(answers, authorFolder);
                    
                    if (savedFiles.isEmpty() && existingCount > 0) {
                        System.out.println("所有回答都已保存过，无需重复保存");
                    } else {
                        System.out.println("新保存 " + savedFiles.size() + " 个文件到 output/" + authorFolder + "/ 目录");
                    }
                }
            }
            
            return "抓取成功！";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "抓取失败: " + e.getMessage();
        }
    }

    /**
     * 显示帮助信息
     */
    @ShellMethod(value = "显示知乎抓取功能帮助", key = "zhihu-help")
    public String showHelp() {
        StringBuilder help = new StringBuilder();
        help.append("知乎数据抓取功能 (基于 Playwright 浏览器自动化):\n\n");
        help.append("【首次使用】\n");
        help.append("1. zhihu-login          - 打开浏览器\n");
        help.append("2. 在浏览器中登录知乎\n");
        help.append("3. zhihu-save-cookies   - 保存登录状态\n");
        help.append("4. zhihu-user ...       - 抓取数据\n\n");
        help.append("【可用命令】\n");
        help.append("zhihu-user --user-id <用户ID> [选项]  - 抓取用户回答列表\n");
        help.append("zhihu-fetch --url <链接> [选项]       - 抓取指定回答或文章\n");
        help.append("zhihu-sync --user-id <用户ID> [选项]  - 同步用户动态（增量抓取）\n");
        help.append("\n【zhihu-user 参数】\n");
        help.append("--user-id       知乎用户ID（必填）\n");
        help.append("--limit         抓取数量，默认10\n");
        help.append("--save          保存为 Markdown 文件\n");
        help.append("--with-comments 同时抓取作者参与的评论\n");
        help.append("--show-browser  显示浏览器窗口\n");
        help.append("\n【zhihu-fetch 参数】\n");
        help.append("--url           回答或文章链接（必填）\n");
        help.append("--save          保存为 Markdown 文件\n");
        help.append("--with-comments 同时抓取作者参与的评论\n");
        help.append("\n【zhihu-sync 参数】\n");
        help.append("--user-id       知乎用户ID（必填）\n");
        help.append("--limit         检查动态数量，默认50\n");
        help.append("--with-comments 同时抓取作者参与的评论\n");
        help.append("\n【支持的链接格式】\n");
        help.append("回答: https://www.zhihu.com/question/xxx/answer/yyy\n");
        help.append("文章: https://zhuanlan.zhihu.com/p/xxx\n");
        help.append("\n【示例】\n");
        help.append("zhihu-user --user-id mr-dang-77 --limit 5 --save --with-comments\n");
        help.append("zhihu-fetch --url https://zhuanlan.zhihu.com/p/123456 --save --with-comments\n");
        help.append("zhihu-sync --user-id mr-dang-77 --limit 100 --with-comments\n");
        
        return help.toString();
    }
    
    /**
     * 通过链接抓取回答或文章
     */
    @ShellMethod(value = "通过链接抓取知乎回答或文章", key = "zhihu-fetch")
    public String fetchByUrl(
            @ShellOption(value = "--url", help = "知乎回答或文章链接") String url,
            @ShellOption(value = "--save", help = "保存为 Markdown 文件", defaultValue = "false") boolean save,
            @ShellOption(value = "--with-comments", help = "同时抓取作者参与的评论", defaultValue = "false") boolean withComments) {
        
        try {
            zhihuBrowserCrawlerService.setHeadless(true);
            
            String[] parsed = zhihuBrowserCrawlerService.parseZhihuUrl(url);
            if (parsed == null) {
                return "无效的链接格式。支持的格式:\n" +
                       "- 回答: https://www.zhihu.com/question/xxx/answer/yyy\n" +
                       "- 文章: https://zhuanlan.zhihu.com/p/xxx";
            }
            
            String type = parsed[0];
            String id = parsed[1];
            
            if ("answer".equals(type)) {
                return fetchAnswer(id, save, withComments);
            } else if ("article".equals(type)) {
                return fetchArticle(id, save, withComments);
            }
            
            return "不支持的链接类型";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "抓取失败: " + e.getMessage();
        }
    }
    
    /**
     * 抓取单个回答
     */
    private String fetchAnswer(String answerId, boolean save, boolean withComments) {
        System.out.println("正在抓取回答 " + answerId + "...");
        
        ZhihuAnswer answer = zhihuBrowserCrawlerService.crawlAnswerById(answerId);
        
        System.out.println();
        System.out.println("=== 回答详情 ===");
        System.out.printf("问题: %s%n", answer.getQuestionTitle());
        System.out.printf("作者: %s%n", answer.getAuthorName());
        System.out.printf("点赞: %d | 评论: %d%n", answer.getVoteupCount(), answer.getCommentCount());
        System.out.printf("链接: %s%n", answer.getUrl());
        if (answer.getContent() != null && !answer.getContent().isEmpty()) {
            String preview = answer.getContent().length() > 200 ? 
                answer.getContent().substring(0, 200) + "..." : answer.getContent();
            System.out.printf("内容预览: %s%n", preview);
        }
        System.out.println();
        
        if (withComments && answer.getCommentCount() > 0 && answer.getAuthorId() != null) {
            System.out.println("正在抓取作者参与的评论...");
            List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlAnswerComments(
                    answer.getId(), answer.getAuthorId());
            answer.setComments(comments);
            System.out.println("获取 " + comments.size() + " 条作者互动评论");
        }
        
        if (save) {
            try {
                String authorFolder = answer.getAuthorName() != null ? answer.getAuthorName() : "unknown";
                Path savedPath = answerSaveService.saveAnswer(answer, authorFolder);
                System.out.println("已保存到: " + savedPath);
            } catch (Exception e) {
                System.out.println("保存失败: " + e.getMessage());
            }
        }
        
        return "回答抓取成功！";
    }
    
    /**
     * 抓取单个文章
     */
    private String fetchArticle(String articleId, boolean save, boolean withComments) {
        System.out.println("正在抓取文章 " + articleId + "...");
        
        ZhihuArticle article = zhihuBrowserCrawlerService.crawlArticleById(articleId);
        
        System.out.println();
        System.out.println("=== 文章详情 ===");
        System.out.printf("标题: %s%n", article.getTitle());
        System.out.printf("作者: %s%n", article.getAuthorName());
        System.out.printf("点赞: %d | 评论: %d%n", article.getVoteupCount(), article.getCommentCount());
        System.out.printf("链接: %s%n", article.getUrl());
        if (article.getContent() != null && !article.getContent().isEmpty()) {
            String preview = article.getContent().length() > 200 ? 
                article.getContent().substring(0, 200) + "..." : article.getContent();
            System.out.printf("内容预览: %s%n", preview);
        }
        System.out.println();
        
        if (withComments && article.getCommentCount() > 0 && article.getAuthorId() != null) {
            System.out.println("正在抓取作者参与的评论...");
            List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlArticleComments(
                    article.getId(), article.getAuthorId());
            article.setComments(comments);
            System.out.println("获取 " + comments.size() + " 条作者互动评论");
        }
        
        if (save) {
            try {
                String authorFolder = article.getAuthorName() != null ? article.getAuthorName() : "unknown";
                Path savedPath = answerSaveService.saveArticle(article, authorFolder);
                System.out.println("已保存到: " + savedPath);
            } catch (Exception e) {
                System.out.println("保存失败: " + e.getMessage());
            }
        }
        
        return "文章抓取成功！";
    }
    
    /**
     * 同步用户动态（增量抓取新内容）
     */
    @ShellMethod(value = "同步用户动态，增量抓取新的回答和文章", key = "zhihu-sync")
    public String syncUserActivities(
            @ShellOption(value = "--user-id", help = "知乎用户ID") String userId,
            @ShellOption(value = "--limit", help = "检查动态数量限制", defaultValue = "50") int limit,
            @ShellOption(value = "--with-comments", help = "同时抓取作者参与的评论", defaultValue = "false") boolean withComments) {
        
        try {
            zhihuBrowserCrawlerService.setHeadless(true);
            
            System.out.println("正在获取用户 " + userId + " 的动态...");
            
            // 获取用户动态列表
            List<ZhihuBrowserCrawlerService.ActivityItem> activities = 
                zhihuBrowserCrawlerService.crawlUserActivities(userId, limit);
            
            if (activities.isEmpty()) {
                return "未获取到任何动态";
            }
            
            // 获取作者名称
            String authorName = activities.get(0).authorName;
            if (authorName == null || authorName.isEmpty()) {
                authorName = userId;
            }
            
            System.out.println();
            System.out.println("=== 动态概览 ===");
            System.out.println("作者: " + authorName);
            System.out.println("获取动态: " + activities.size() + " 条");
            
            long answerCount = activities.stream().filter(a -> "answer".equals(a.type)).count();
            long articleCount = activities.stream().filter(a -> "article".equals(a.type)).count();
            System.out.println("其中回答: " + answerCount + " 条，文章: " + articleCount + " 条");
            System.out.println();
            
            // 检查哪些是新的
            java.util.Set<String> savedAnswerIds = answerSaveService.getSavedAnswerIds(authorName);
            
            List<ZhihuBrowserCrawlerService.ActivityItem> newAnswers = new java.util.ArrayList<>();
            List<ZhihuBrowserCrawlerService.ActivityItem> newArticles = new java.util.ArrayList<>();
            
            for (ZhihuBrowserCrawlerService.ActivityItem item : activities) {
                if ("answer".equals(item.type)) {
                    if (!savedAnswerIds.contains(item.id)) {
                        newAnswers.add(item);
                    }
                } else if ("article".equals(item.type)) {
                    if (!answerSaveService.isArticleSaved(item.id, authorName)) {
                        newArticles.add(item);
                    }
                }
            }
            
            System.out.println("新增回答: " + newAnswers.size() + " 条");
            System.out.println("新增文章: " + newArticles.size() + " 条");
            
            if (newAnswers.isEmpty() && newArticles.isEmpty()) {
                System.out.println();
                System.out.println("没有新内容需要抓取");
                
                // 更新索引
                try {
                    answerSaveService.updateAuthorIndex(authorName, userId);
                    System.out.println("索引已更新");
                } catch (Exception e) {
                    System.out.println("更新索引失败: " + e.getMessage());
                }
                
                return "同步完成，无新内容";
            }
            
            System.out.println();
            System.out.println("=== 开始抓取新内容 ===");
            
            int savedCount = 0;
            java.util.Random random = new java.util.Random();
            
            // 抓取新回答
            for (int i = 0; i < newAnswers.size(); i++) {
                ZhihuBrowserCrawlerService.ActivityItem item = newAnswers.get(i);
                System.out.println();
                System.out.println("[" + (i + 1) + "/" + newAnswers.size() + "] 抓取回答: " + item.title);
                
                try {
                    ZhihuAnswer answer = zhihuBrowserCrawlerService.crawlAnswerById(item.id);
                    
                    if (withComments && answer.getCommentCount() > 0 && answer.getAuthorId() != null) {
                        System.out.println("  抓取评论...");
                        List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlAnswerComments(
                                answer.getId(), answer.getAuthorId());
                        answer.setComments(comments);
                        System.out.println("  获取 " + comments.size() + " 条作者互动评论");
                    }
                    
                    answerSaveService.saveAnswer(answer, authorName);
                    savedCount++;
                    System.out.println("  ✓ 已保存");
                    
                } catch (Exception e) {
                    System.out.println("  ✗ 抓取失败: " + e.getMessage());
                }
                
                // 随机延迟 3-6 秒，避免被反爬
                int delay = 3000 + random.nextInt(3000);
                System.out.println("  等待 " + (delay / 1000.0) + " 秒...");
                Thread.sleep(delay);
            }
            
            // 抓取新文章
            for (int i = 0; i < newArticles.size(); i++) {
                ZhihuBrowserCrawlerService.ActivityItem item = newArticles.get(i);
                System.out.println();
                System.out.println("[" + (i + 1) + "/" + newArticles.size() + "] 抓取文章: " + item.title);
                
                try {
                    ZhihuArticle article = zhihuBrowserCrawlerService.crawlArticleById(item.id);
                    
                    if (withComments && article.getCommentCount() > 0 && article.getAuthorId() != null) {
                        System.out.println("  抓取评论...");
                        List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlArticleComments(
                                article.getId(), article.getAuthorId());
                        article.setComments(comments);
                        System.out.println("  获取 " + comments.size() + " 条作者互动评论");
                    }
                    
                    answerSaveService.saveArticle(article, authorName);
                    savedCount++;
                    System.out.println("  ✓ 已保存");
                    
                } catch (Exception e) {
                    System.out.println("  ✗ 抓取失败: " + e.getMessage());
                }
                
                // 随机延迟 3-6 秒，避免被反爬
                int delay = 3000 + random.nextInt(3000);
                System.out.println("  等待 " + (delay / 1000.0) + " 秒...");
                Thread.sleep(delay);
            }
            
            System.out.println();
            System.out.println("=== 同步完成 ===");
            System.out.println("成功保存: " + savedCount + " 条内容");
            
            // 更新索引
            try {
                java.nio.file.Path indexPath = answerSaveService.updateAuthorIndex(authorName, userId);
                System.out.println("索引已更新: " + indexPath);
            } catch (Exception e) {
                System.out.println("更新索引失败: " + e.getMessage());
            }
            
            return "同步完成！新增 " + savedCount + " 条内容";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "同步失败: " + e.getMessage();
        }
    }
    
    /**
     * 分析文章提炼投资线索
     */
    @ShellMethod(value = "分析文章提炼投资线索", key = "zhihu-analyze")
    public String analyzeContent(
            @ShellOption(value = {"--file", "-f"}, help = "要分析的 Markdown 文件路径") String filePath) {
        
        try {
            if (!deepSeekService.isAvailable()) {
                return "DeepSeek API 未配置，请检查 api-key-file 配置";
            }
            
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            System.out.println("正在读取文件...");
            String content = Files.readString(path);
            
            // 检查是否已经分析过
            if (content.contains("## AI 投资线索分析")) {
                return "该文件已包含 AI 分析结果，跳过";
            }
            
            // 提取标题（第一行 # 开头的内容）
            String title = "未知标题";
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("# ")) {
                    title = line.substring(2).trim();
                    break;
                }
            }
            
            System.out.println("正在分析: " + title);
            System.out.println("调用 DeepSeek API...");
            
            String analysis = deepSeekService.extractInvestmentClues(content, title);
            
            System.out.println();
            System.out.println("=== 投资线索分析 ===");
            System.out.println();
            System.out.println(analysis);
            
            // 将分析结果追加到原文档末尾
            String analysisSection = "\n\n---\n\n## AI 投资线索分析\n\n> 由 DeepSeek 自动生成\n\n" + analysis;
            Files.writeString(path, content + analysisSection);
            System.out.println();
            System.out.println("分析结果已追加到原文档: " + path);
            
            return "分析完成";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "分析失败: " + e.getMessage();
        }
    }
    
    /**
     * 批量分析作者所有文章
     */
    @ShellMethod(value = "批量分析作者所有文章", key = "zhihu-analyze-all")
    public String analyzeAll(
            @ShellOption(value = {"--author", "-a"}, help = "作者文件夹名称") String authorName,
            @ShellOption(value = {"--delay"}, defaultValue = "3", help = "每篇文章分析间隔(秒)") int delay) {
        
        try {
            if (!deepSeekService.isAvailable()) {
                return "DeepSeek API 未配置，请检查 api-key-file 配置";
            }
            
            Path authorDir = Path.of("output", authorName);
            if (!Files.exists(authorDir)) {
                return "作者目录不存在: " + authorDir;
            }
            
            // 获取所有 md 文件（排除 INDEX.md 和已分析的文件）
            java.util.List<Path> mdFiles = Files.list(authorDir)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("INDEX.md"))
                    .filter(p -> !p.getFileName().toString().endsWith("_analysis.md"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            
            System.out.println("找到 " + mdFiles.size() + " 个文件待分析");
            System.out.println();
            
            int analyzed = 0;
            int skipped = 0;
            int failed = 0;
            
            for (int i = 0; i < mdFiles.size(); i++) {
                Path file = mdFiles.get(i);
                String fileName = file.getFileName().toString();
                System.out.println("[" + (i + 1) + "/" + mdFiles.size() + "] " + fileName);
                
                try {
                    String content = Files.readString(file);
                    
                    // 检查是否已分析
                    if (content.contains("## AI 投资线索分析")) {
                        System.out.println("  已分析，跳过");
                        skipped++;
                        continue;
                    }
                    
                    // 提取标题
                    String title = "未知标题";
                    for (String line : content.split("\n")) {
                        if (line.startsWith("# ")) {
                            title = line.substring(2).trim();
                            break;
                        }
                    }
                    
                    System.out.println("  分析中: " + title);
                    String analysis = deepSeekService.extractInvestmentClues(content, title);
                    
                    // 追加到原文档
                    String analysisSection = "\n\n---\n\n## AI 投资线索分析\n\n> 由 DeepSeek 自动生成\n\n" + analysis;
                    Files.writeString(file, content + analysisSection);
                    
                    System.out.println("  ✓ 完成");
                    analyzed++;
                    
                    // 延迟避免 API 限流
                    if (i < mdFiles.size() - 1) {
                        Thread.sleep(delay * 1000L);
                    }
                    
                } catch (Exception e) {
                    System.out.println("  ✗ 失败: " + e.getMessage());
                    failed++;
                }
            }
            
            System.out.println();
            System.out.println("=== 批量分析完成 ===");
            System.out.println("成功: " + analyzed + ", 跳过: " + skipped + ", 失败: " + failed);
            
            return "批量分析完成";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "批量分析失败: " + e.getMessage();
        }
    }
    
    /**
     * 导出 Markdown 为 Word 文档
     */
    @ShellMethod(value = "导出 Markdown 为 Word 文档", key = "export-word")
    public String exportWord(
            @ShellOption(value = {"--file", "-f"}, help = "要导出的 Markdown 文件路径") String filePath) {
        
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                return "文件不存在: " + filePath;
            }
            
            System.out.println("正在导出: " + path.getFileName());
            Path outputPath = wordExportService.exportToWord(path);
            System.out.println("导出成功: " + outputPath);
            
            return "导出完成";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "导出失败: " + e.getMessage();
        }
    }
    
    /**
     * 批量导出作者所有文章为 Word
     */
    @ShellMethod(value = "批量导出作者所有文章为 Word", key = "export-word-all")
    public String exportWordAll(
            @ShellOption(value = {"--author", "-a"}, help = "作者文件夹名称") String authorName) {
        
        try {
            Path authorDir = Path.of("output", authorName);
            if (!Files.exists(authorDir)) {
                return "作者目录不存在: " + authorDir;
            }
            
            // 获取所有 md 文件（排除 INDEX.md）
            java.util.List<Path> mdFiles = Files.list(authorDir)
                    .filter(p -> p.toString().endsWith(".md"))
                    .filter(p -> !p.getFileName().toString().equals("INDEX.md"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
            
            System.out.println("找到 " + mdFiles.size() + " 个文件待导出");
            System.out.println();
            
            int success = 0;
            int failed = 0;
            
            for (int i = 0; i < mdFiles.size(); i++) {
                Path file = mdFiles.get(i);
                String fileName = file.getFileName().toString();
                System.out.print("[" + (i + 1) + "/" + mdFiles.size() + "] " + fileName + " ... ");
                
                try {
                    wordExportService.exportToWord(file);
                    System.out.println("✓");
                    success++;
                } catch (Exception e) {
                    System.out.println("✗ " + e.getMessage());
                    failed++;
                }
            }
            
            System.out.println();
            System.out.println("=== 批量导出完成 ===");
            System.out.println("成功: " + success + ", 失败: " + failed);
            
            return "批量导出完成";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "批量导出失败: " + e.getMessage();
        }
    }
}
