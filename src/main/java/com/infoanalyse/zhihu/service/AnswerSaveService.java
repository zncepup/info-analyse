package com.infoanalyse.zhihu.service;

import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuComment;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 回答保存服务 - 将回答保存为 Markdown 文件，包含图片
 */
@Service
public class AnswerSaveService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnswerSaveService.class);
    private static final String OUTPUT_DIR = "output";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final HttpClient httpClient;
    
    public AnswerSaveService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
    
    /**
     * 检查回答是否已保存
     */
    public boolean isAnswerSaved(String answerId, String userId) {
        Path userDir = Path.of(OUTPUT_DIR, userId);
        if (!Files.exists(userDir)) {
            return false;
        }
        try {
            return Files.list(userDir)
                    .anyMatch(p -> p.getFileName().toString().startsWith(answerId + "_"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取已保存的回答 ID 集合
     */
    public Set<String> getSavedAnswerIds(String userId) {
        Set<String> savedIds = new HashSet<>();
        Path userDir = Path.of(OUTPUT_DIR, userId);
        
        if (!Files.exists(userDir)) {
            return savedIds;
        }
        
        try {
            Files.list(userDir)
                    .filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        String fileName = p.getFileName().toString();
                        int underscoreIndex = fileName.indexOf('_');
                        if (underscoreIndex > 0) {
                            savedIds.add(fileName.substring(0, underscoreIndex));
                        }
                    });
        } catch (IOException e) {
            logger.warn("读取已保存文件列表失败: {}", e.getMessage());
        }
        
        return savedIds;
    }
    
    /**
     * 保存单个回答为 Markdown 文件
     */
    public Path saveAnswer(ZhihuAnswer answer, String userId) throws IOException {
        // 创建输出目录: output/<user-id>/
        Path userDir = Path.of(OUTPUT_DIR, userId);
        Files.createDirectories(userDir);
        
        // 创建图片目录: output/<user-id>/images/
        Path imagesDir = userDir.resolve("images");
        Files.createDirectories(imagesDir);
        
        // 生成文件名（使用问题标题，清理非法字符）
        String safeTitle = sanitizeFileName(answer.getQuestionTitle());
        if (safeTitle.length() > 50) {
            safeTitle = safeTitle.substring(0, 50);
        }
        String fileName = answer.getId() + "_" + safeTitle + ".md";
        Path mdFile = userDir.resolve(fileName);
        
        // 转换 HTML 为 Markdown，同时下载图片
        String markdown = convertToMarkdown(answer, imagesDir, answer.getId());
        
        // 写入文件
        Files.writeString(mdFile, markdown);
        logger.info("已保存回答到: {}", mdFile);
        
        return mdFile;
    }

    /**
     * 批量保存回答（跳过已保存的）
     */
    public List<Path> saveAnswers(List<ZhihuAnswer> answers, String userId) {
        List<Path> savedFiles = new ArrayList<>();
        Set<String> existingIds = getSavedAnswerIds(userId);
        int skipped = 0;
        
        for (ZhihuAnswer answer : answers) {
            if (existingIds.contains(answer.getId())) {
                skipped++;
                logger.debug("跳过已保存的回答: {}", answer.getId());
                continue;
            }
            
            try {
                Path file = saveAnswer(answer, userId);
                savedFiles.add(file);
            } catch (Exception e) {
                logger.error("保存回答失败: {} - {}", answer.getId(), e.getMessage());
            }
        }
        
        if (skipped > 0) {
            logger.info("跳过 {} 个已保存的回答", skipped);
        }
        
        return savedFiles;
    }
    
    /**
     * 将 HTML 内容转换为 Markdown
     */
    private String convertToMarkdown(ZhihuAnswer answer, Path imagesDir, String answerId) {
        StringBuilder md = new StringBuilder();
        
        // 添加元信息头
        md.append("# ").append(answer.getQuestionTitle()).append("\n\n");
        md.append("---\n\n");
        md.append("- **作者**: ").append(answer.getAuthorName() != null ? answer.getAuthorName() : "未知").append("\n");
        md.append("- **点赞**: ").append(answer.getVoteupCount()).append("\n");
        md.append("- **评论**: ").append(answer.getCommentCount()).append("\n");
        if (answer.getCreatedTime() != null) {
            md.append("- **创建时间**: ").append(answer.getCreatedTime().format(DATE_FORMAT)).append("\n");
        }
        if (answer.getUpdatedTime() != null) {
            md.append("- **更新时间**: ").append(answer.getUpdatedTime().format(DATE_FORMAT)).append("\n");
        }
        md.append("- **原文链接**: [").append(answer.getUrl()).append("](").append(answer.getUrl()).append(")\n");
        md.append("\n---\n\n");
        
        // 转换正文内容
        String htmlContent = answer.getHtmlContent();
        if (htmlContent != null && !htmlContent.isEmpty()) {
            md.append(htmlToMarkdown(htmlContent, imagesDir, answerId));
        } else if (answer.getContent() != null) {
            md.append(answer.getContent());
        }
        
        // 添加评论部分（体现父子关系）
        if (answer.getComments() != null && !answer.getComments().isEmpty()) {
            md.append("\n\n---\n\n");
            md.append("## 作者互动评论\n\n");
            
            // 按父子关系分组评论
            java.util.Map<String, List<ZhihuComment>> childrenMap = new java.util.HashMap<>();
            List<ZhihuComment> rootComments = new ArrayList<>();
            
            for (ZhihuComment comment : answer.getComments()) {
                if (comment.getParentCommentId() == null) {
                    rootComments.add(comment);
                } else {
                    childrenMap.computeIfAbsent(comment.getParentCommentId(), k -> new ArrayList<>())
                              .add(comment);
                }
            }
            
            // 根评论按时间排序
            rootComments.sort((a, b) -> {
                if (a.getCreatedTime() == null) return 1;
                if (b.getCreatedTime() == null) return -1;
                return a.getCreatedTime().compareTo(b.getCreatedTime());
            });
            
            // 子评论也按时间排序
            for (List<ZhihuComment> children : childrenMap.values()) {
                children.sort((a, b) -> {
                    if (a.getCreatedTime() == null) return 1;
                    if (b.getCreatedTime() == null) return -1;
                    return a.getCreatedTime().compareTo(b.getCreatedTime());
                });
            }
            
            // 输出评论（带层级）
            for (ZhihuComment rootComment : rootComments) {
                appendComment(md, rootComment, answer.getAuthorId(), false);
                
                List<ZhihuComment> children = childrenMap.get(rootComment.getId());
                if (children != null) {
                    for (ZhihuComment child : children) {
                        appendComment(md, child, answer.getAuthorId(), true);
                    }
                }
                
                md.append("---\n\n");
            }
            
            // 处理没有父评论的子评论
            List<ZhihuComment> orphanComments = new ArrayList<>();
            for (ZhihuComment comment : answer.getComments()) {
                if (comment.getParentCommentId() != null && 
                    !rootComments.stream().anyMatch(r -> r.getId().equals(comment.getParentCommentId())) &&
                    !childrenMap.values().stream().flatMap(List::stream).anyMatch(c -> c.getId().equals(comment.getId()))) {
                    orphanComments.add(comment);
                }
            }
            
            if (!orphanComments.isEmpty()) {
                orphanComments.sort((a, b) -> {
                    if (a.getCreatedTime() == null) return 1;
                    if (b.getCreatedTime() == null) return -1;
                    return a.getCreatedTime().compareTo(b.getCreatedTime());
                });
                
                for (ZhihuComment comment : orphanComments) {
                    appendComment(md, comment, answer.getAuthorId(), false);
                    md.append("---\n\n");
                }
            }
        }
        
        return md.toString();
    }

    /**
     * HTML 转 Markdown（处理图片、链接、格式等）
     */
    private String htmlToMarkdown(String html, Path imagesDir, String answerId) {
        Document doc = Jsoup.parse(html);
        StringBuilder result = new StringBuilder();
        
        // 处理图片 - 下载并替换为本地路径
        Elements images = doc.select("img");
        int imgIndex = 0;
        for (Element img : images) {
            String src = img.attr("data-original");
            if (src.isEmpty()) {
                src = img.attr("data-actualsrc");
            }
            if (src.isEmpty()) {
                src = img.attr("src");
            }
            
            if (!src.isEmpty() && !src.startsWith("data:")) {
                imgIndex++;
                String localPath = downloadImage(src, imagesDir, answerId, imgIndex);
                if (localPath != null) {
                    img.attr("data-local-path", localPath);
                }
            }
        }
        
        // 递归处理节点
        processNode(doc.body(), result, imagesDir, answerId);
        
        return result.toString().trim();
    }
    
    /**
     * 递归处理 HTML 节点
     */
    private void processNode(Element element, StringBuilder result, Path imagesDir, String answerId) {
        for (var node : element.childNodes()) {
            if (node instanceof org.jsoup.nodes.TextNode textNode) {
                result.append(textNode.text());
            } else if (node instanceof Element child) {
                String tagName = child.tagName().toLowerCase();
                
                switch (tagName) {
                    case "p" -> {
                        processNode(child, result, imagesDir, answerId);
                        result.append("\n\n");
                    }
                    case "br" -> result.append("\n");
                    case "strong", "b" -> {
                        result.append("**");
                        processNode(child, result, imagesDir, answerId);
                        result.append("**");
                    }
                    case "em", "i" -> {
                        result.append("*");
                        processNode(child, result, imagesDir, answerId);
                        result.append("*");
                    }
                    case "a" -> {
                        String href = child.attr("href");
                        result.append("[");
                        processNode(child, result, imagesDir, answerId);
                        result.append("](").append(href).append(")");
                    }
                    case "img" -> {
                        String localPath = child.attr("data-local-path");
                        String alt = child.attr("alt");
                        if (alt.isEmpty()) alt = "图片";
                        if (localPath != null && !localPath.isEmpty()) {
                            result.append("![").append(alt).append("](").append(localPath).append(")\n\n");
                        }
                    }
                    case "figure" -> {
                        Element figImg = child.selectFirst("img");
                        if (figImg != null) {
                            String localPath = figImg.attr("data-local-path");
                            String alt = figImg.attr("alt");
                            if (alt.isEmpty()) alt = "图片";
                            if (localPath != null && !localPath.isEmpty()) {
                                result.append("![").append(alt).append("](").append(localPath).append(")\n\n");
                            }
                        } else {
                            processNode(child, result, imagesDir, answerId);
                        }
                    }
                    case "blockquote" -> {
                        result.append("> ");
                        String quote = child.text().replace("\n", "\n> ");
                        result.append(quote).append("\n\n");
                    }
                    case "code" -> {
                        result.append("`").append(child.text()).append("`");
                    }
                    case "pre" -> {
                        result.append("```\n").append(child.text()).append("\n```\n\n");
                    }
                    case "ul" -> {
                        for (Element li : child.select("> li")) {
                            result.append("- ");
                            processNode(li, result, imagesDir, answerId);
                            result.append("\n");
                        }
                        result.append("\n");
                    }
                    case "ol" -> {
                        int idx = 1;
                        for (Element li : child.select("> li")) {
                            result.append(idx++).append(". ");
                            processNode(li, result, imagesDir, answerId);
                            result.append("\n");
                        }
                        result.append("\n");
                    }
                    case "h1" -> {
                        result.append("## ");
                        processNode(child, result, imagesDir, answerId);
                        result.append("\n\n");
                    }
                    case "h2" -> {
                        result.append("### ");
                        processNode(child, result, imagesDir, answerId);
                        result.append("\n\n");
                    }
                    case "h3", "h4", "h5", "h6" -> {
                        result.append("#### ");
                        processNode(child, result, imagesDir, answerId);
                        result.append("\n\n");
                    }
                    case "hr" -> result.append("\n---\n\n");
                    case "div", "span" -> processNode(child, result, imagesDir, answerId);
                    default -> processNode(child, result, imagesDir, answerId);
                }
            }
        }
    }

    /**
     * 下载图片到本地
     */
    private String downloadImage(String imageUrl, Path imagesDir, String answerId, int index) {
        try {
            if (imageUrl.startsWith("//")) {
                imageUrl = "https:" + imageUrl;
            }
            
            String extension = getImageExtension(imageUrl);
            String fileName = answerId + "_" + index + extension;
            Path imagePath = imagesDir.resolve(fileName);
            
            if (Files.exists(imagePath)) {
                logger.debug("图片已存在，跳过: {}", fileName);
                return "images/" + fileName;
            }
            
            logger.info("下载图片: {}", imageUrl);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://www.zhihu.com/")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            
            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    Files.copy(is, imagePath);
                }
                logger.debug("图片下载成功: {}", fileName);
                return "images/" + fileName;
            } else {
                logger.warn("图片下载失败，状态码: {} - {}", response.statusCode(), imageUrl);
                return null;
            }
            
        } catch (Exception e) {
            logger.warn("图片下载异常: {} - {}", imageUrl, e.getMessage());
            return null;
        }
    }
    
    /**
     * 从 URL 获取图片扩展名
     */
    private String getImageExtension(String url) {
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            url = url.substring(0, queryIndex);
        }
        
        if (url.endsWith(".jpg") || url.endsWith(".jpeg")) return ".jpg";
        if (url.endsWith(".png")) return ".png";
        if (url.endsWith(".gif")) return ".gif";
        if (url.endsWith(".webp")) return ".webp";
        
        if (url.contains("zhimg.com")) {
            return ".jpg";
        }
        
        return ".jpg";
    }
    
    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "untitled";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                   .replaceAll("\\s+", "_")
                   .trim();
    }
    
    /**
     * 输出单条评论到 Markdown
     */
    private void appendComment(StringBuilder md, ZhihuComment comment, String answerAuthorId, boolean isChild) {
        boolean isAuthor = answerAuthorId != null && answerAuthorId.equals(comment.getAuthorId());
        String authorMark = isAuthor ? " 🔖" : "";
        
        String content = comment.getContent();
        if (content != null) {
            content = content.replaceAll("<[^>]+>", "").trim();
        }
        
        String timeStr = "";
        if (comment.getCreatedTime() != null) {
            timeStr = comment.getCreatedTime().format(DATE_FORMAT);
        }
        
        String likeStr = comment.getLikeCount() > 0 ? " 👍" + comment.getLikeCount() : "";
        
        if (isChild) {
            md.append("│\n");
            md.append("└─ **").append(comment.getAuthorName()).append("**").append(authorMark);
            if (comment.getReplyToAuthor() != null && !comment.getReplyToAuthor().isEmpty()) {
                md.append(" → ").append(comment.getReplyToAuthor());
            }
            md.append(": ").append(content != null ? content : "").append("\n");
            md.append("   *").append(timeStr).append("*").append(likeStr).append("\n\n");
        } else {
            md.append("💬 **").append(comment.getAuthorName()).append("**").append(authorMark).append("\n\n");
            md.append("> ").append(content != null ? content.replace("\n", "\n> ") : "").append("\n\n");
            md.append("*").append(timeStr).append("*").append(likeStr).append("\n\n");
        }
    }
}
