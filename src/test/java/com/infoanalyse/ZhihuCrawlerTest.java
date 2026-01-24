package com.infoanalyse;

import com.infoanalyse.model.ZhihuAnswer;
import com.infoanalyse.service.ZhihuCrawlerService;

import java.util.List;

/**
 * 知乎爬虫测试
 */
public class ZhihuCrawlerTest {
    
    public static void main(String[] args) {
        System.out.println("=== 知乎数据抓取测试 ===\n");
        
        ZhihuCrawlerService service = new ZhihuCrawlerService();
        
        try {
            // 测试抓取用户回答
            String userId = "mr-dang-77";
            int limit = 3;
            
            System.out.println("正在抓取用户 " + userId + " 的回答（限制 " + limit + " 条）...\n");
            
            List<ZhihuAnswer> answers = service.crawlUserAnswers(userId, limit);
            
            System.out.println("抓取成功！共获取 " + answers.size() + " 条回答\n");
            System.out.println("=".repeat(80));
            
            for (int i = 0; i < answers.size(); i++) {
                ZhihuAnswer answer = answers.get(i);
                System.out.println("\n【回答 " + (i + 1) + "】");
                System.out.println("问题: " + answer.getQuestionTitle());
                System.out.println("作者: " + answer.getAuthorName());
                System.out.println("点赞: " + answer.getVoteupCount() + " | 评论: " + answer.getCommentCount());
                System.out.println("创建时间: " + answer.getCreatedTime());
                System.out.println("URL: " + answer.getUrl());
                System.out.println("内容预览: " + 
                    (answer.getContent().length() > 100 ? 
                        answer.getContent().substring(0, 100) + "..." : 
                        answer.getContent()));
                System.out.println("-".repeat(80));
            }
            
        } catch (Exception e) {
            System.err.println("抓取失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
