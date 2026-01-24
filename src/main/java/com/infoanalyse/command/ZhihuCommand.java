package com.infoanalyse.command;

import com.infoanalyse.model.ZhihuAnswer;
import com.infoanalyse.model.ZhihuComment;
import com.infoanalyse.service.AnswerSaveService;
import com.infoanalyse.service.ZhihuBrowserCrawlerService;
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
            @ShellOption(value = "--show-browser", help = "显示浏览器窗口（用于手动登录）", defaultValue = "false") boolean showBrowser,
            @ShellOption(value = "--save", help = "保存回答为 Markdown 文件（包含图片）", defaultValue = "false") boolean save,
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
                
                // 如果带评论，强制覆盖保存
                if (withComments) {
                    int savedCount = 0;
                    for (ZhihuAnswer answer : answers) {
                        try {
                            answerSaveService.saveAnswer(answer, userId);
                            savedCount++;
                        } catch (Exception e) {
                            System.out.println("保存失败: " + answer.getId() + " - " + e.getMessage());
                        }
                    }
                    System.out.println("保存 " + savedCount + " 个文件到 output/" + userId + "/ 目录（含评论）");
                } else {
                    // 不带评论，跳过已保存的
                    int existingCount = answerSaveService.getSavedAnswerIds(userId).size();
                    List<Path> savedFiles = answerSaveService.saveAnswers(answers, userId);
                    
                    if (savedFiles.isEmpty() && existingCount > 0) {
                        System.out.println("所有回答都已保存过，无需重复保存");
                    } else {
                        System.out.println("新保存 " + savedFiles.size() + " 个文件到 output/" + userId + "/ 目录");
                        if (existingCount > 0) {
                            System.out.println("（跳过 " + (answers.size() - savedFiles.size()) + " 个已保存的回答）");
                        }
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
     * 抓取指定回答的评论（暂未实现）
     */
    @ShellMethod(value = "抓取知乎回答的评论", key = "zhihu-comments")
    public String crawlAnswerComments(
            @ShellOption(value = "--answer-id", help = "回答ID") String answerId,
            @ShellOption(value = "--limit", help = "抓取数量限制", defaultValue = "50") int limit) {
        
        return "评论抓取功能暂未实现，敬请期待！";
    }

    /**
     * 抓取回答详情（包含评论）（暂未实现）
     */
    @ShellMethod(value = "抓取知乎回答详情（含评论）", key = "zhihu-answer")
    public String crawlAnswerDetail(
            @ShellOption(value = "--answer-id", help = "回答ID") String answerId) {
        
        return "回答详情抓取功能暂未实现，敬请期待！";
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
        help.append("【已保存登录状态后】\n");
        help.append("直接使用 zhihu-user 即可抓取数据\n\n");
        help.append("【可用命令】\n");
        help.append("zhihu-login                                    - 打开浏览器登录\n");
        help.append("zhihu-save-cookies                             - 保存登录状态\n");
        help.append("zhihu-user --user-id <用户ID> [选项]           - 抓取用户回答\n");
        help.append("\n【参数说明】\n");
        help.append("--user-id       知乎用户ID（必填）\n");
        help.append("--limit         抓取数量，默认10\n");
        help.append("--save          保存为 Markdown 文件（包含图片）\n");
        help.append("--with-comments 同时抓取作者参与的评论（需配合 --save）\n");
        help.append("--show-browser  显示浏览器窗口\n");
        help.append("\n【示例】\n");
        help.append("zhihu-user --user-id mr-dang-77 --limit 5\n");
        help.append("zhihu-user --user-id mr-dang-77 --limit 5 --save\n");
        help.append("zhihu-user --user-id mr-dang-77 --limit 5 --save --with-comments\n");
        
        return help.toString();
    }
}