package com.infoanalyse.zhihu;

import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuArticle;
import com.infoanalyse.zhihu.model.ZhihuComment;
import com.infoanalyse.zhihu.service.AnswerSaveService;
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
    private AnswerSaveService answerSaveService;

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
        help.append("\n【支持的链接格式】\n");
        help.append("回答: https://www.zhihu.com/question/xxx/answer/yyy\n");
        help.append("文章: https://zhuanlan.zhihu.com/p/xxx\n");
        help.append("\n【示例】\n");
        help.append("zhihu-user --user-id mr-dang-77 --limit 5 --save --with-comments\n");
        help.append("zhihu-fetch --url https://zhuanlan.zhihu.com/p/123456 --save --with-comments\n");
        
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
}
