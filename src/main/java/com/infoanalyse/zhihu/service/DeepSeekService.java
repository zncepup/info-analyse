package com.infoanalyse.zhihu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
public class DeepSeekService {
    
    private static final Logger logger = LoggerFactory.getLogger(DeepSeekService.class);
    
    @Value("${deepseek.api-key-file:}")
    private String apiKeyFile;
    
    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String baseUrl;
    
    @Value("${deepseek.model:deepseek-reasoner}")
    private String model;
    
    private String apiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    
    @PostConstruct
    public void init() {
        try {
            if (apiKeyFile != null && !apiKeyFile.isEmpty()) {
                Path keyPath = Path.of(apiKeyFile);
                if (Files.exists(keyPath)) {
                    apiKey = Files.readString(keyPath).trim();
                    logger.info("DeepSeek API Key 已加载");
                }
            }
        } catch (Exception e) {
            logger.warn("加载 DeepSeek API Key 失败: {}", e.getMessage());
        }
    }
    
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 提炼投资线索
     */
    public String extractInvestmentClues(String content, String title) {
        if (!isAvailable()) {
            return "DeepSeek API 未配置";
        }
        
        String systemPrompt = """
            你是一个专业的投资分析助手。请从用户提供的文章中提炼出投资相关的关键信息。
            
            请按以下格式输出：
            
            ## 核心观点
            - 列出文章的核心投资观点
            
            ## 提及的标的
            - 列出文章中提到的具体股票、基金、行业等投资标的
            - 如果有具体代码或名称请标注
            
            ## 市场判断
            - 作者对市场走势的判断
            - 看多/看空的理由
            
            ## 风险提示
            - 文章中提到的风险点
            
            ## 操作建议
            - 如果有具体的买卖建议请提取
            
            注意：
            1. 只提取文章中明确提到的信息，不要推测
            2. 保持客观，不添加个人观点
            3. 如果某个部分没有相关信息，可以写"无"
            """;
        
        String userPrompt = "请分析以下文章：\n\n标题：" + title + "\n\n内容：\n" + content;
        
        try {
            return chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            logger.error("调用 DeepSeek API 失败", e);
            return "分析失败: " + e.getMessage();
        }
    }
    
    /**
     * 调用 DeepSeek Chat API
     */
    public String chat(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);
        
        ArrayNode messages = requestBody.putArray("messages");
        
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);
        
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);
        
        String requestJson = objectMapper.writeValueAsString(requestBody);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(120))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new RuntimeException("API 调用失败: " + response.statusCode() + " - " + response.body());
        }
        
        JsonNode responseJson = objectMapper.readTree(response.body());
        JsonNode choices = responseJson.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            JsonNode message = choices.get(0).get("message");
            if (message != null && message.has("content")) {
                return message.get("content").asText();
            }
        }
        
        return "无法解析响应";
    }
}
