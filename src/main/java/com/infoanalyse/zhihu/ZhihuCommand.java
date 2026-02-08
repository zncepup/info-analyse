package com.infoanalyse.zhihu;

import com.infoanalyse.dao.mapper.AiAnalysisDOMapper;
import com.infoanalyse.dao.mapper.ZhihuAnswerDOMapper;
import com.infoanalyse.dao.mapper.ZhihuArticleDOMapper;
import com.infoanalyse.dao.mapper.GubaPostDOMapper;
import com.infoanalyse.dao.mapper.ZhihuPinDOMapper;
import com.infoanalyse.dao.model.*;
import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuArticle;
import com.infoanalyse.zhihu.model.ZhihuComment;
import com.infoanalyse.zhihu.model.ZhihuPin;
import com.infoanalyse.zhihu.service.DeepSeekService;
import com.infoanalyse.zhihu.service.ZhihuDbSaveService;
import com.infoanalyse.commons.service.WordExportService;
import com.infoanalyse.zhihu.service.ZhihuBrowserCrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

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
    private ZhihuDbSaveService zhihuDbSaveService;
    
    @Autowired
    private DeepSeekService deepSeekService;
    
    @Autowired
    private WordExportService wordExportService;

    @Autowired
    private AiAnalysisDOMapper aiAnalysisMapper;

    @Autowired
    private ZhihuAnswerDOMapper answerMapper;

    @Autowired
    private ZhihuArticleDOMapper articleMapper;

    @Autowired
    private GubaPostDOMapper gubaPostMapper;

    @Autowired
    private ZhihuPinDOMapper pinMapper;

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
                System.out.println("正在保存回答到数据库...");
                
                int savedCount = 0;
                for (ZhihuAnswer answer : answers) {
                    try {
                        zhihuDbSaveService.saveAnswer(answer);
                        savedCount++;
                        // 自动AI分析
                        try {
                            analyzeContentFromDb("zhihu", Long.parseLong(answer.getId()), "answer");
                        } catch (Exception ae) {
                            System.out.println("自动分析失败: " + answer.getId() + " - " + ae.getMessage());
                        }
                    } catch (Exception e) {
                        System.out.println("保存失败: " + answer.getId() + " - " + e.getMessage());
                    }
                }
                System.out.println("已保存 " + savedCount + " 条回答到数据库");
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
            } else if ("pin".equals(type)) {
                return fetchPin(id, save);
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
                zhihuDbSaveService.saveAnswer(answer);
                System.out.println("已保存到数据库");
                // 自动AI分析
                try {
                    analyzeContentFromDb("zhihu", Long.parseLong(answer.getId()), "answer");
                } catch (Exception ae) {
                    System.out.println("自动分析失败: " + ae.getMessage());
                }
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
                zhihuDbSaveService.saveArticle(article);
                System.out.println("已保存到数据库");
                // 自动AI分析
                try {
                    analyzeContentFromDb("zhihu", Long.parseLong(article.getId()), "article");
                } catch (Exception ae) {
                    System.out.println("自动分析失败: " + ae.getMessage());
                }
            } catch (Exception e) {
                System.out.println("保存失败: " + e.getMessage());
            }
        }
        
        return "文章抓取成功！";
    }

    private String fetchPin(String pinId, boolean save) {
        System.out.println("正在抓取想法 " + pinId + "...");
        
        ZhihuPin pin = zhihuBrowserCrawlerService.crawlPinById(pinId);
        
        System.out.println();
        System.out.println("=== 想法详情 ===");
        System.out.printf("作者: %s%n", pin.getAuthorName());
        System.out.printf("点赞: %d | 评论: %d | 转发: %d%n", pin.getLikeCount(), pin.getCommentCount(), pin.getRepinCount());
        System.out.printf("链接: %s%n", pin.getUrl());
        if (pin.getContent() != null && !pin.getContent().isEmpty()) {
            String preview = pin.getContent().length() > 200 ? 
                pin.getContent().substring(0, 200) + "..." : pin.getContent();
            System.out.printf("内容预览: %s%n", preview);
        }
        System.out.println();
        
        if (save) {
            try {
                zhihuDbSaveService.savePin(pin);
                System.out.println("已保存到数据库");
                try {
                    analyzeContentFromDb("zhihu", Long.parseLong(pin.getId()), "pin");
                } catch (Exception ae) {
                    System.out.println("自动分析失败: " + ae.getMessage());
                }
            } catch (Exception e) {
                System.out.println("保存失败: " + e.getMessage());
            }
        }
        
        return "想法抓取成功！";
    }

    
    /**
     * 同步用户动态（增量抓取新内容）
     */
    @ShellMethod(value = "同步用户动态，增量抓取新的回答和文章", key = "zhihu-sync")
    public String syncUserActivities(
            @ShellOption(value = "--user-id", help = "知乎用户ID") String userId,
            @ShellOption(value = "--limit", help = "检查动态数量限制", defaultValue = "50") int limit,
            @ShellOption(value = "--with-comments", help = "同时抓取作者参与的评论", defaultValue = "false") boolean withComments) {
        return syncUserActivities(userId, limit, withComments, null);
    }

    public String syncUserActivities(String userId, int limit, boolean withComments, com.infoanalyse.web.task.TaskInfo taskInfo) {
        return syncUserActivities(userId, limit, withComments, true, taskInfo);
    }

    public String syncUserActivities(String userId, int limit, boolean withComments, boolean autoAnalyze, com.infoanalyse.web.task.TaskInfo taskInfo) {
        
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
            long pinCount = activities.stream().filter(a -> "pin".equals(a.type)).count();
            System.out.println("其中回答: " + answerCount + " 条，文章: " + articleCount + " 条，想法: " + pinCount + " 条");
            System.out.println();
            
            // 检查哪些是新的
            java.util.Set<Long> savedAnswerIds = zhihuDbSaveService.getSavedAnswerIds();
            
            List<ZhihuBrowserCrawlerService.ActivityItem> newAnswers = new java.util.ArrayList<>();
            List<ZhihuBrowserCrawlerService.ActivityItem> newArticles = new java.util.ArrayList<>();
            List<ZhihuBrowserCrawlerService.ActivityItem> newPins = new java.util.ArrayList<>();
            
            for (ZhihuBrowserCrawlerService.ActivityItem item : activities) {
                if ("answer".equals(item.type)) {
                    Long idNum = null;
                    try { idNum = Long.parseLong(item.id); } catch (Exception ignored) {}
                    if (idNum == null || !savedAnswerIds.contains(idNum)) {
                        newAnswers.add(item);
                    }
                } else if ("article".equals(item.type)) {
                    if (!zhihuDbSaveService.isArticleSaved(item.id)) {
                        newArticles.add(item);
                    }
                } else if ("pin".equals(item.type)) {
                    if (!zhihuDbSaveService.isPinSaved(item.id)) {
                        newPins.add(item);
                    }
                }
            }
            
            System.out.println("新增回答: " + newAnswers.size() + " 条");
            System.out.println("新增文章: " + newArticles.size() + " 条");
            System.out.println("新增想法: " + newPins.size() + " 条");

            // 设置进度: 每个内容项 = 1个主步骤
            int totalNew = newAnswers.size() + newArticles.size() + newPins.size();
            if (taskInfo != null) {
                taskInfo.setTotalSteps(totalNew);
                // 分阶段进度
                if (!newAnswers.isEmpty()) taskInfo.phaseInit("爬取回答", newAnswers.size());
                if (!newArticles.isEmpty()) taskInfo.phaseInit("爬取文章", newArticles.size());
                if (!newPins.isEmpty()) taskInfo.phaseInit("爬取想法", newPins.size());
                if (withComments) taskInfo.phaseInit("爬取评论", totalNew);
                if (autoAnalyze) taskInfo.phaseInit("AI分析", totalNew);
            }
            
            if (newAnswers.isEmpty() && newArticles.isEmpty() && newPins.isEmpty()) {
                System.out.println();
                System.out.println("没有新内容需要抓取");
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
                if (taskInfo != null) taskInfo.stepStart("抓取回答: " + item.title);
                
                try {
                    ZhihuAnswer answer = zhihuBrowserCrawlerService.crawlAnswerById(item.id);
                    if (taskInfo != null) taskInfo.phaseDone("爬取回答");
                    
                    if (withComments && answer.getCommentCount() > 0 && answer.getAuthorId() != null) {
                        System.out.println("  抓取评论...");
                        if (taskInfo != null) taskInfo.stepStart("抓取评论: " + item.title);
                        List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlAnswerComments(
                                answer.getId(), answer.getAuthorId());
                        answer.setComments(comments);
                        System.out.println("  获取 " + comments.size() + " 条作者互动评论");
                        if (taskInfo != null) taskInfo.phaseDone("爬取评论");
                    } else if (withComments && taskInfo != null) {
                        taskInfo.phaseSkip("爬取评论");
                    }
                    
                    zhihuDbSaveService.saveAnswer(answer);
                    savedCount++;
                    System.out.println("  ✓ 已保存");
                    
                    // 自动AI分析
                    if (autoAnalyze) {
                        try {
                            if (taskInfo != null) taskInfo.stepStart("AI分析: " + item.title);
                            analyzeContentFromDb("zhihu", Long.parseLong(answer.getId()), "answer");
                            if (taskInfo != null) taskInfo.phaseDone("AI分析");
                        } catch (Exception ae) {
                            System.out.println("  自动分析失败: " + ae.getMessage());
                            if (taskInfo != null) taskInfo.phaseFail("AI分析");
                        }
                    }
                    if (taskInfo != null) taskInfo.stepDone("✓ 回答: " + item.title);
                    
                } catch (Exception e) {
                    System.out.println("  ✗ 抓取失败: " + e.getMessage());
                    if (taskInfo != null) {
                        taskInfo.phaseFail("爬取回答");
                        if (withComments) taskInfo.phaseSkip("爬取评论");
                        if (autoAnalyze) taskInfo.phaseSkip("AI分析");
                        taskInfo.stepDone("✗ 回答失败: " + item.title);
                    }
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
                if (taskInfo != null) taskInfo.stepStart("抓取文章: " + item.title);
                
                try {
                    ZhihuArticle article = zhihuBrowserCrawlerService.crawlArticleById(item.id);
                    if (taskInfo != null) taskInfo.phaseDone("爬取文章");
                    
                    if (withComments && article.getCommentCount() > 0 && article.getAuthorId() != null) {
                        System.out.println("  抓取评论...");
                        if (taskInfo != null) taskInfo.stepStart("抓取评论: " + item.title);
                        List<ZhihuComment> comments = zhihuBrowserCrawlerService.crawlArticleComments(
                                article.getId(), article.getAuthorId());
                        article.setComments(comments);
                        System.out.println("  获取 " + comments.size() + " 条作者互动评论");
                        if (taskInfo != null) taskInfo.phaseDone("爬取评论");
                    } else if (withComments && taskInfo != null) {
                        taskInfo.phaseSkip("爬取评论");
                    }
                    
                    zhihuDbSaveService.saveArticle(article);
                    savedCount++;
                    System.out.println("  ✓ 已保存");
                    
                    // 自动AI分析
                    if (autoAnalyze) {
                        try {
                            if (taskInfo != null) taskInfo.stepStart("AI分析: " + item.title);
                            analyzeContentFromDb("zhihu", Long.parseLong(article.getId()), "article");
                            if (taskInfo != null) taskInfo.phaseDone("AI分析");
                        } catch (Exception ae) {
                            System.out.println("  自动分析失败: " + ae.getMessage());
                            if (taskInfo != null) taskInfo.phaseFail("AI分析");
                        }
                    }
                    if (taskInfo != null) taskInfo.stepDone("✓ 文章: " + item.title);
                    
                } catch (Exception e) {
                    System.out.println("  ✗ 抓取失败: " + e.getMessage());
                    if (taskInfo != null) {
                        taskInfo.phaseFail("爬取文章");
                        if (withComments) taskInfo.phaseSkip("爬取评论");
                        if (autoAnalyze) taskInfo.phaseSkip("AI分析");
                        taskInfo.stepDone("✗ 文章失败: " + item.title);
                    }
                }
                
                // 随机延迟 3-6 秒，避免被反爬
                int delay = 3000 + random.nextInt(3000);
                System.out.println("  等待 " + (delay / 1000.0) + " 秒...");
                Thread.sleep(delay);
            }
            
            // 抓取新想法
            for (int i = 0; i < newPins.size(); i++) {
                ZhihuBrowserCrawlerService.ActivityItem item = newPins.get(i);
                System.out.println();
                System.out.println("[" + (i + 1) + "/" + newPins.size() + "] 抓取想法: " + item.title);
                if (taskInfo != null) taskInfo.stepStart("抓取想法: " + item.title);
                
                try {
                    ZhihuPin pin = zhihuBrowserCrawlerService.crawlPinById(item.id);
                    if (taskInfo != null) taskInfo.phaseDone("爬取想法");
                    
                    zhihuDbSaveService.savePin(pin);
                    savedCount++;
                    System.out.println("  ✓ 已保存");
                    if (withComments && taskInfo != null) taskInfo.phaseSkip("爬取评论");
                    
                    // 自动AI分析
                    if (autoAnalyze) {
                        try {
                            if (taskInfo != null) taskInfo.stepStart("AI分析: " + item.title);
                            analyzeContentFromDb("zhihu", Long.parseLong(pin.getId()), "pin");
                            if (taskInfo != null) taskInfo.phaseDone("AI分析");
                        } catch (Exception ae) {
                            System.out.println("  自动分析失败: " + ae.getMessage());
                            if (taskInfo != null) taskInfo.phaseFail("AI分析");
                        }
                    }
                    if (taskInfo != null) taskInfo.stepDone("✓ 想法: " + item.title);
                } catch (Exception e) {
                    System.out.println("  ✗ 抓取失败: " + e.getMessage());
                    if (taskInfo != null) {
                        taskInfo.phaseFail("爬取想法");
                        if (withComments) taskInfo.phaseSkip("爬取评论");
                        if (autoAnalyze) taskInfo.phaseSkip("AI分析");
                        taskInfo.stepDone("✗ 想法失败: " + item.title);
                    }
                }
                
                int delay = 3000 + random.nextInt(3000);
                System.out.println("  等待 " + (delay / 1000.0) + " 秒...");
                Thread.sleep(delay);
            }
            
            System.out.println();
            System.out.println("=== 同步完成 ===");
            System.out.println("成功保存: " + savedCount + " 条内容");
            
            return "同步完成！新增 " + savedCount + " 条内容";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "同步失败: " + e.getMessage();
        }
    }
    
    /**
     * 分析内容提炼投资线索（从数据库读取）
     * @param source 来源: zhihu / guba
     * @param targetId 业务ID
     * @param targetType 类型: answer / article / post
     */
    public String analyzeContentFromDb(String source, Long targetId, String targetType) {
        try {
            if (!deepSeekService.isAvailable()) {
                return "DeepSeek API 未配置，请检查 api-key-file 配置";
            }

            // 检查DB中是否已有分析结果
            AiAnalysisDOExample example = new AiAnalysisDOExample();
            example.createCriteria()
                    .andSourceEqualTo(source)
                    .andTargetIdEqualTo(targetId)
                    .andTargetTypeEqualTo(targetType)
                    .andAiModelEqualTo("deepseek-reasoner")
                    .andAnalysisTypeEqualTo("investment_clue");
            if (aiAnalysisMapper.countByExample(example) > 0) {
                return "该内容已有 AI 分析结果，跳过";
            }

            // 从DB读取内容和标题
            String content;
            String title;
            if ("zhihu".equals(source) && "answer".equals(targetType)) {
                ZhihuAnswerDOExample aEx = new ZhihuAnswerDOExample();
                aEx.createCriteria().andAnswerIdEqualTo(targetId);
                List<ZhihuAnswerDO> list = answerMapper.selectByExampleWithBLOBs(aEx);
                if (list.isEmpty()) return "回答不存在: " + targetId;
                content = list.get(0).getContent();
                title = list.get(0).getQuestionTitle();
            } else if ("zhihu".equals(source) && "article".equals(targetType)) {
                ZhihuArticleDOExample aEx = new ZhihuArticleDOExample();
                aEx.createCriteria().andArticleIdEqualTo(targetId);
                List<ZhihuArticleDO> list = articleMapper.selectByExampleWithBLOBs(aEx);
                if (list.isEmpty()) return "文章不存在: " + targetId;
                content = list.get(0).getContent();
                title = list.get(0).getTitle();
            } else if ("guba".equals(source) && "post".equals(targetType)) {
                GubaPostDOExample pEx = new GubaPostDOExample();
                pEx.createCriteria().andPostIdEqualTo(targetId);
                List<GubaPostDO> list = gubaPostMapper.selectByExampleWithBLOBs(pEx);
                if (list.isEmpty()) return "帖子不存在: " + targetId;
                content = list.get(0).getContent();
                title = list.get(0).getTitle();
            } else {
                return "不支持的来源/类型: " + source + "/" + targetType;
            }

            if (content == null || content.isBlank()) {
                return "内容为空，跳过";
            }
            if (title == null) title = "未知标题";

            System.out.println("正在分析: " + title);
            System.out.println("调用 DeepSeek API...");

            String analysis = deepSeekService.extractInvestmentClues(content, title);

            System.out.println();
            System.out.println("=== 投资线索分析 ===");
            System.out.println();
            System.out.println(analysis);

            // 保存到数据库
            AiAnalysisDO record = new AiAnalysisDO();
            record.setSource(source);
            record.setTargetId(targetId);
            record.setTargetType(targetType);
            record.setAiModel("deepseek-reasoner");
            record.setAnalysisType("investment_clue");
            record.setResult(analysis);
            record.setStatus("COMPLETED");
            record.setCreatedTime(java.time.LocalDateTime.now());
            aiAnalysisMapper.insertSelective(record);

            System.out.println("分析结果已保存到数据库");

            return "分析完成";

        } catch (Exception e) {
            e.printStackTrace();
            return "分析失败: " + e.getMessage();
        }
    }

    /**
     * Shell 命令兼容: 通过文件路径或 source/type/id 格式分析
     */
    @ShellMethod(value = "分析文章提炼投资线索", key = "zhihu-analyze")
    public String analyzeContent(
            @ShellOption(value = {"--file", "-f"}, help = "要分析的文件路径或 source/type/id 格式") String filePath) {
        // 先尝试 source/type/id 格式 (如 zhihu/answer/12345 或 guba/post/67890)
        String[] parts = filePath.split("/");
        if (parts.length == 3) {
            try {
                Long targetId = Long.parseLong(parts[2]);
                return analyzeContentFromDb(parts[0], targetId, parts[1]);
            } catch (NumberFormatException ignored) {}
        }
        // 回退到文件名解析
        String fileName = Path.of(filePath).getFileName().toString();
        String source = "zhihu";
        String targetType;
        Long targetId;
        if (fileName.startsWith("article_")) {
            targetType = "article";
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^article_(\\d+)_").matcher(fileName);
            if (!m.find()) return "无法解析文章ID: " + fileName;
            targetId = Long.parseLong(m.group(1));
        } else {
            targetType = "answer";
            int idx = fileName.indexOf('_');
            if (idx <= 0) return "无法解析回答ID: " + fileName;
            try { targetId = Long.parseLong(fileName.substring(0, idx)); }
            catch (NumberFormatException e) { return "无法解析ID: " + fileName; }
        }
        return analyzeContentFromDb(source, targetId, targetType);
    }
    
    /**
     * 导出内容为 Word 文档（从数据库读取）
     */
    @ShellMethod(value = "导出内容为 Word 文档", key = "export-word")
    public String exportWord(
            @ShellOption(value = {"--file", "-f"}, help = "source/type/id 格式，如 zhihu/answer/12345") String target) {
        
        try {
            String[] parts = parseTarget(target);
            if (parts == null) return "格式错误，请使用 source/type/id 格式，如 zhihu/answer/12345";
            
            String source = parts[0];
            String targetType = parts[1];
            Long targetId = Long.parseLong(parts[2]);
            
            String content = loadContentFromDb(source, targetId, targetType);
            if (content == null) return "内容不存在: " + target;
            
            String title = loadTitleFromDb(source, targetId, targetType);
            if (title == null) title = targetType + "_" + targetId;
            
            // 拼接AI分析结果
            content = appendAiAnalysisText(content, source, targetId, targetType);
            
            String safeTitle = title.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path outputPath = Path.of("output", "word", safeTitle + ".docx");
            
            System.out.println("正在导出: " + title);
            wordExportService.exportContentToWord(content, outputPath, null);
            System.out.println("导出成功: " + outputPath);
            
            return "导出完成";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "导出失败: " + e.getMessage();
        }
    }
    
    // ========== 辅助方法 ==========

    private String[] parseTarget(String target) {
        // 支持 zhihu/answer/12345 或旧的文件路径格式
        String[] parts = target.split("/");
        if (parts.length == 3) {
            try {
                Long.parseLong(parts[2]);
                return parts;
            } catch (NumberFormatException ignored) {}
        }
        // 尝试从文件名解析
        String fileName = Path.of(target).getFileName().toString();
        if (fileName.startsWith("article_")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^article_(\\d+)_").matcher(fileName);
            if (m.find()) return new String[]{"zhihu", "article", m.group(1)};
        } else {
            int idx = fileName.indexOf('_');
            if (idx > 0) {
                try {
                    Long.parseLong(fileName.substring(0, idx));
                    return new String[]{"zhihu", "answer", fileName.substring(0, idx)};
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String loadContentFromDb(String source, Long targetId, String targetType) {
        if ("zhihu".equals(source) && "answer".equals(targetType)) {
            ZhihuAnswerDOExample ex = new ZhihuAnswerDOExample();
            ex.createCriteria().andAnswerIdEqualTo(targetId);
            List<ZhihuAnswerDO> list = answerMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("zhihu".equals(source) && "article".equals(targetType)) {
            ZhihuArticleDOExample ex = new ZhihuArticleDOExample();
            ex.createCriteria().andArticleIdEqualTo(targetId);
            List<ZhihuArticleDO> list = articleMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("guba".equals(source) && "post".equals(targetType)) {
            GubaPostDOExample ex = new GubaPostDOExample();
            ex.createCriteria().andPostIdEqualTo(targetId);
            List<GubaPostDO> list = gubaPostMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("zhihu".equals(source) && "pin".equals(targetType)) {
            ZhihuPinDOExample ex = new ZhihuPinDOExample();
            ex.createCriteria().andPinIdEqualTo(targetId);
            List<ZhihuPinDO> list = pinMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        }
        return null;
    }

    private String loadTitleFromDb(String source, Long targetId, String targetType) {
        if ("zhihu".equals(source) && "answer".equals(targetType)) {
            ZhihuAnswerDOExample ex = new ZhihuAnswerDOExample();
            ex.createCriteria().andAnswerIdEqualTo(targetId);
            List<ZhihuAnswerDO> list = answerMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getQuestionTitle();
        } else if ("zhihu".equals(source) && "article".equals(targetType)) {
            ZhihuArticleDOExample ex = new ZhihuArticleDOExample();
            ex.createCriteria().andArticleIdEqualTo(targetId);
            List<ZhihuArticleDO> list = articleMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getTitle();
        } else if ("guba".equals(source) && "post".equals(targetType)) {
            GubaPostDOExample ex = new GubaPostDOExample();
            ex.createCriteria().andPostIdEqualTo(targetId);
            List<GubaPostDO> list = gubaPostMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getTitle();
        } else if ("zhihu".equals(source) && "pin".equals(targetType)) {
            ZhihuPinDOExample ex = new ZhihuPinDOExample();
            ex.createCriteria().andPinIdEqualTo(targetId);
            List<ZhihuPinDO> list = pinMapper.selectByExample(ex);
            if (list.isEmpty()) return null;
            String content = list.get(0).getContent();
            return content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content;
        }
        return null;
    }

    private String appendAiAnalysisText(String content, String source, Long targetId, String targetType) {
        AiAnalysisDOExample ex = new AiAnalysisDOExample();
        ex.createCriteria()
                .andSourceEqualTo(source)
                .andTargetIdEqualTo(targetId)
                .andTargetTypeEqualTo(targetType)
                .andStatusEqualTo("COMPLETED");
        List<AiAnalysisDO> analyses = aiAnalysisMapper.selectByExampleWithBLOBs(ex);
        if (analyses.isEmpty()) return content;
        
        StringBuilder sb = new StringBuilder(content);
        for (AiAnalysisDO a : analyses) {
            sb.append("\n\n## AI 分析 (").append(a.getAiModel()).append(")\n\n");
            sb.append(a.getResult());
        }
        return sb.toString();
    }
}
