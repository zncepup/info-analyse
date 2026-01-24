package com.infoanalyse.command;

import com.infoanalyse.model.ZhihuAnswer;
import com.infoanalyse.model.ZhihuComment;
import com.infoanalyse.service.ZhihuCrawlerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;

/**
 * 知乎相关的命令行接口
 */
@ShellComponent
public class ZhihuCommand {

    @Autowired
    private ZhihuCrawlerService zhihuCrawlerService;

    /**
     * 抓取指定用户的回答
     */
    @ShellMethod(value = "抓取知乎用户的回答", key = "zhihu-user")
    public String crawlUserAnswers(
            @ShellOption(value = "--user-id", help = "知乎用户ID") String userId,
            @ShellOption(value = "--limit", help = "抓取数量限制", defaultValue = "10") int limit) {
        
        try {
            System.out.println("正在抓取用户 " + userId + " 的回答...");
            
            List<ZhihuAnswer> answers = zhihuCrawlerService.crawlUserAnswers(userId, limit);
            
            System.out.println("抓取完成！共获取 " + answers.size() + " 个回答");
            
            // 显示回答摘要
            for (int i = 0; i < Math.min(answers.size(), 5); i++) {
                ZhihuAnswer answer = answers.get(i);
                System.out.printf("[%d] %s (点赞: %d, 评论: %d)%n", 
                    i + 1, answer.getQuestionTitle(), answer.getVoteupCount(), answer.getCommentCount());
            }
            
            return "抓取成功！";
            
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    /**
     * 抓取指定回答的评论
     */
    @ShellMethod(value = "抓取知乎回答的评论", key = "zhihu-comments")
    public String crawlAnswerComments(
            @ShellOption(value = "--answer-id", help = "回答ID") String answerId,
            @ShellOption(value = "--limit", help = "抓取数量限制", defaultValue = "50") int limit) {
        
        try {
            System.out.println("正在抓取回答 " + answerId + " 的评论...");
            
            List<ZhihuComment> comments = zhihuCrawlerService.crawlAnswerComments(answerId, limit);
            
            System.out.println("抓取完成！共获取 " + comments.size() + " 条评论");
            
            // 显示评论摘要
            for (int i = 0; i < Math.min(comments.size(), 5); i++) {
                ZhihuComment comment = comments.get(i);
                System.out.printf("[%d] %s: %s (点赞: %d)%n", 
                    i + 1, comment.getAuthorName(), 
                    comment.getContent().length() > 50 ? 
                        comment.getContent().substring(0, 50) + "..." : comment.getContent(),
                    comment.getLikeCount());
            }
            
            return "抓取成功！";
            
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    /**
     * 抓取回答详情（包含评论）
     */
    @ShellMethod(value = "抓取知乎回答详情（含评论）", key = "zhihu-answer")
    public String crawlAnswerDetail(
            @ShellOption(value = "--answer-id", help = "回答ID") String answerId) {
        
        try {
            System.out.println("正在抓取回答 " + answerId + " 的详细信息...");
            
            ZhihuAnswer answer = zhihuCrawlerService.crawlAnswerWithComments(answerId);
            
            System.out.println("回答信息:");
            System.out.println("问题: " + answer.getQuestionTitle());
            System.out.println("作者: " + answer.getAuthorName());
            System.out.println("点赞: " + answer.getVoteupCount());
            System.out.println("评论数: " + answer.getCommentCount());
            System.out.println("内容预览: " + 
                (answer.getContent().length() > 100 ? 
                    answer.getContent().substring(0, 100) + "..." : answer.getContent()));
            
            if (answer.getComments() != null && !answer.getComments().isEmpty()) {
                System.out.println("\n评论预览:");
                for (int i = 0; i < Math.min(answer.getComments().size(), 3); i++) {
                    ZhihuComment comment = answer.getComments().get(i);
                    System.out.printf("  %s: %s%n", comment.getAuthorName(), comment.getContent());
                }
            }
            
            return "抓取成功！";
            
        } catch (Exception e) {
            return "抓取失败: " + e.getMessage();
        }
    }

    /**
     * 显示帮助信息
     */
    @ShellMethod(value = "显示知乎抓取功能帮助", key = "zhihu-help")
    public String showHelp() {
        StringBuilder help = new StringBuilder();
        help.append("知乎数据抓取功能:\n");
        help.append("1. zhihu-user --user-id <用户ID> [--limit <数量>] - 抓取用户回答\n");
        help.append("2. zhihu-comments --answer-id <回答ID> [--limit <数量>] - 抓取回答评论\n");
        help.append("3. zhihu-answer --answer-id <回答ID> - 抓取回答详情（含评论）\n");
        help.append("\n示例:\n");
        help.append("zhihu-user --user-id excited-vczh --limit 5\n");
        help.append("zhihu-comments --answer-id 123456789 --limit 20\n");
        help.append("zhihu-answer --answer-id 123456789\n");
        
        return help.toString();
    }
}