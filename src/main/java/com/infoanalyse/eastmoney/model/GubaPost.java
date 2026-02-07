package com.infoanalyse.eastmoney.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 东方财富股吧帖子实体
 */
public class GubaPost {
    private String postId;
    private String stockCode;
    private String stockName;
    private String title;
    private String content;
    private String htmlContent;
    private String authorName;
    private String authorId;
    private int readCount;
    private int commentCount;
    private int likeCount;
    private LocalDateTime publishTime;
    private String url;
    private List<GubaComment> comments;

    public GubaPost() {}

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public int getReadCount() { return readCount; }
    public void setReadCount(int readCount) { this.readCount = readCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<GubaComment> getComments() { return comments; }
    public void setComments(List<GubaComment> comments) { this.comments = comments; }
}
