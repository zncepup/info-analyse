package com.infoanalyse.zhihu.model;

import java.time.LocalDateTime;

/**
 * 知乎想法实体
 */
public class ZhihuPin {
    private String id;
    private String authorName;
    private String authorId;
    private String content;
    private String htmlContent;
    private int likeCount;
    private int commentCount;
    private int repinCount;
    private String url;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
    public int getRepinCount() { return repinCount; }
    public void setRepinCount(int repinCount) { this.repinCount = repinCount; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
