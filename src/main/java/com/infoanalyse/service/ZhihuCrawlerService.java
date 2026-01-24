package com.infoanalyse.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infoanalyse.model.ZhihuAnswer;
import com.infoanalyse.model.ZhihuComment;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 知乎数据抓取服务
 */
@Service
public class ZhihuCrawlerService {
    
    private static final Logger logger = LoggerFactory.getLogger(ZhihuCrawlerService.class);
    
    private static final String API_BASE_URL = "https://www.zhihu.com/api/v4";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    public ZhihuCrawlerService() {
        this.webClient = WebClient.builder()
                .baseUrl(API_BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 根据用户ID抓取该用户的所有回答
     * 
     * @param userId 知乎用户ID
     * @param limit 抓取数量限制
     * @return 回答列表
     */
    public List<ZhihuAnswer> crawlUserAnswers(String userId, int limit) {
        logger.info("开始抓取用户 {} 的回答，限制数量: {}", userId, limit);
        
        List<ZhihuAnswer> allAnswers = new ArrayList<>();
        int offset = 0;
        int batchSize = Math.min(20, limit); // 知乎API单次最多返回20条
        
        try {
            while (allAnswers.size() < limit) {
                String url = String.format("/members/%s/answers?limit=%d&offset=%d&sort_by=created&include=data[*].is_normal,suggest_edit,comment_count,content,voteup_count,created_time,updated_time,question", 
                        userId, batchSize, offset);
                
                logger.debug("请求URL: {}", url);
                
                String response = webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                
                if (response == null || response.isEmpty()) {
                    logger.warn("响应为空");
                    break;
                }
                
                JsonNode root = objectMapper.readTree(response);
                JsonNode dataArray = root.get("data");
                
                if (dataArray == null || !dataArray.isArray() || dataArray.size() == 0) {
                    logger.info("没有更多数据");
                    break;
                }
                
                for (JsonNode answerNode : dataArray) {
                    ZhihuAnswer answer = parseAnswer(answerNode);
                    allAnswers.add(answer);
                    
                    if (allAnswers.size() >= limit) {
                        break;
                    }
                }
                
                // 检查是否还有下一页
                JsonNode paging = root.get("paging");
                if (paging != null && paging.get("is_end").asBoolean()) {
                    logger.info("已到最后一页");
                    break;
                }
                
                offset += batchSize;
                
                // 添加延迟避免请求过快
                Thread.sleep(1000);
            }
            
            logger.info("成功抓取 {} 条回答", allAnswers.size());
            return allAnswers;
            
        } catch (Exception e) {
            logger.error("抓取用户回答失败", e);
            throw new RuntimeException("抓取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据回答ID抓取该回答的所有评论
     * 
     * @param answerId 回答ID
     * @param limit 抓取数量限制
     * @return 评论列表
     */
    public List<ZhihuComment> crawlAnswerComments(String answerId, int limit) {
        logger.info("开始抓取回答 {} 的评论，限制数量: {}", answerId, limit);
        
        // TODO: 实现评论抓取逻辑
        // 知乎评论API可能需要登录或有其他限制
        
        throw new UnsupportedOperationException("评论抓取功能开发中...");
    }

    /**
     * 抓取指定回答的详细信息（包含评论）
     * 
     * @param answerId 回答ID
     * @return 完整的回答信息
     */
    public ZhihuAnswer crawlAnswerWithComments(String answerId) {
        logger.info("开始抓取回答 {} 的详细信息", answerId);
        
        try {
            String url = String.format("/answers/%s?include=content,voteup_count,comment_count,created_time,updated_time,question", answerId);
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            if (response == null || response.isEmpty()) {
                throw new RuntimeException("响应为空");
            }
            
            JsonNode answerNode = objectMapper.readTree(response);
            ZhihuAnswer answer = parseAnswer(answerNode);
            
            // TODO: 抓取评论
            // answer.setComments(crawlAnswerComments(answerId, 100));
            
            return answer;
            
        } catch (Exception e) {
            logger.error("抓取回答详情失败", e);
            throw new RuntimeException("抓取失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析回答JSON数据
     */
    private ZhihuAnswer parseAnswer(JsonNode node) {
        ZhihuAnswer answer = new ZhihuAnswer();
        
        answer.setId(node.get("id").asText());
        answer.setVoteupCount(node.get("voteup_count").asInt());
        answer.setCommentCount(node.get("comment_count").asInt());
        
        // 解析问题信息
        JsonNode question = node.get("question");
        if (question != null) {
            answer.setQuestionId(question.get("id").asText());
            answer.setQuestionTitle(question.get("title").asText());
        }
        
        // 解析作者信息
        JsonNode author = node.get("author");
        if (author != null) {
            answer.setAuthorId(author.get("id").asText());
            answer.setAuthorName(author.get("name").asText());
        }
        
        // 解析内容（去除HTML标签）
        String htmlContent = node.get("content").asText();
        String plainText = Jsoup.parse(htmlContent).text();
        answer.setContent(plainText);
        
        // 解析时间
        long createdTimestamp = node.get("created_time").asLong();
        answer.setCreatedTime(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(createdTimestamp), 
                ZoneId.systemDefault()));
        
        if (node.has("updated_time")) {
            long updatedTimestamp = node.get("updated_time").asLong();
            answer.setUpdatedTime(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(updatedTimestamp), 
                    ZoneId.systemDefault()));
        }
        
        // 构建URL
        answer.setUrl(String.format("https://www.zhihu.com/question/%s/answer/%s", 
                answer.getQuestionId(), answer.getId()));
        
        return answer;
    }
}