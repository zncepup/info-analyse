package com.infoanalyse.eastmoney.service;

import com.infoanalyse.eastmoney.model.GubaComment;
import com.infoanalyse.eastmoney.model.GubaPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 股吧帖子保存服务 - 将帖子和评论保存为 Markdown 文件
 */
@Service
public class GubaPostSaveService {

    private static final Logger logger = LoggerFactory.getLogger(GubaPostSaveService.class);
    private static final String OUTPUT_DIR = "output";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 保存单个帖子为 Markdown 文件
     */
    public Path savePost(GubaPost post) throws IOException {
        String dirName = "guba_" + post.getStockCode();
        if (post.getStockName() != null && !post.getStockName().isEmpty()) {
            dirName = "guba_" + post.getStockCode() + "_" + sanitize(post.getStockName());
        }
        Path dir = Path.of(OUTPUT_DIR, dirName);
        Files.createDirectories(dir);

        String safeTitle = sanitize(post.getTitle());
        if (safeTitle.length() > 50) {
            safeTitle = safeTitle.substring(0, 50);
        }
        String fileName = post.getPostId() + "_" + safeTitle + ".md";
        Path mdFile = dir.resolve(fileName);

        String markdown = convertToMarkdown(post);
        Files.writeString(mdFile, markdown);
        logger.info("已保存股吧帖子到: {}", mdFile);
        return mdFile;
    }

    /**
     * 批量保存帖子
     */
    public List<Path> savePosts(List<GubaPost> posts) {
        List<Path> saved = new ArrayList<>();
        for (GubaPost post : posts) {
            try {
                saved.add(savePost(post));
            } catch (Exception e) {
                logger.warn("保存帖子失败: {} - {}", post.getPostId(), e.getMessage());
            }
        }
        return saved;
    }

    /**
     * 生成股票股吧索引文件
     */
    public Path updateIndex(String stockCode, String stockName) throws IOException {
        String dirName = "guba_" + stockCode;
        if (stockName != null && !stockName.isEmpty()) {
            dirName = "guba_" + stockCode + "_" + sanitize(stockName);
        }
        Path dir = Path.of(OUTPUT_DIR, dirName);
        if (!Files.exists(dir)) {
            return null;
        }

        List<String> mdFiles = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".md") && !p.getFileName().toString().equals("INDEX.md"))
                    .sorted()
                    .forEach(p -> mdFiles.add(p.getFileName().toString()));
        }

        StringBuilder md = new StringBuilder();
        md.append("# ").append(stockName != null ? stockName : stockCode).append(" 股吧帖子索引\n\n");
        md.append("---\n\n");
        md.append("- **股票代码**: ").append(stockCode).append("\n");
        md.append("- **帖子数量**: ").append(mdFiles.size()).append("\n");
        md.append("- **更新时间**: ").append(java.time.LocalDateTime.now().format(DATE_FORMAT)).append("\n");
        md.append("\n---\n\n");

        for (String file : mdFiles) {
            String title = file.replaceFirst("^\\d+_", "").replace(".md", "").replace("_", " ");
            md.append("- [").append(title).append("](").append(file).append(")\n");
        }

        Path indexFile = dir.resolve("INDEX.md");
        Files.writeString(indexFile, md.toString());
        logger.info("已更新股吧索引: {}", indexFile);
        return indexFile;
    }

    private String convertToMarkdown(GubaPost post) {
        StringBuilder md = new StringBuilder();

        md.append("# ").append(post.getTitle() != null ? post.getTitle() : "无标题").append("\n\n");
        md.append("---\n\n");
        md.append("- **股票**: ").append(post.getStockName() != null ? post.getStockName() : "").append("(").append(post.getStockCode()).append(")\n");
        md.append("- **作者**: ").append(post.getAuthorName() != null ? post.getAuthorName() : "匿名").append("\n");
        md.append("- **阅读**: ").append(post.getReadCount()).append("\n");
        md.append("- **评论**: ").append(post.getCommentCount()).append("\n");
        md.append("- **点赞**: ").append(post.getLikeCount()).append("\n");
        if (post.getPublishTime() != null) {
            md.append("- **发布时间**: ").append(post.getPublishTime().format(DATE_FORMAT)).append("\n");
        }
        if (post.getUrl() != null) {
            md.append("- **原文链接**: [").append(post.getUrl()).append("](").append(post.getUrl()).append(")\n");
        }
        md.append("\n---\n\n");

        // 正文
        if (post.getContent() != null && !post.getContent().isEmpty()) {
            md.append(post.getContent()).append("\n");
        }

        // 评论
        if (post.getComments() != null && !post.getComments().isEmpty()) {
            md.append("\n---\n\n");
            md.append("## 评论 (").append(post.getComments().size()).append(")\n\n");

            for (GubaComment comment : post.getComments()) {
                String author = comment.getAuthorName() != null ? comment.getAuthorName() : "匿名";
                String content = comment.getContent() != null ? comment.getContent() : "";
                String time = comment.getPublishTime() != null ? comment.getPublishTime().format(DATE_FORMAT) : "";
                String likes = comment.getLikeCount() > 0 ? " 👍" + comment.getLikeCount() : "";

                if (comment.getReplyToUser() != null && !comment.getReplyToUser().isEmpty()) {
                    md.append("💬 **").append(author).append("** → ").append(comment.getReplyToUser()).append("\n\n");
                } else {
                    md.append("💬 **").append(author).append("**\n\n");
                }
                md.append("> ").append(content.replace("\n", "\n> ")).append("\n\n");
                md.append("*").append(time).append("*").append(likes).append("\n\n");
                md.append("---\n\n");
            }
        }

        return md.toString();
    }

    private String sanitize(String name) {
        if (name == null) return "untitled";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .trim();
    }
}
