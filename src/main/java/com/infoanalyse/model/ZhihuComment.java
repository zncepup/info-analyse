package com.infoanalyse.model;

import java.time.LocalDateTime;

/**
 * 知乎评论实体
 */
public class ZhihuComment {
    private String id;
    private String answerId;
    private String authorName;
    private String authorId;
    private String content;
    private int likeCount;
    private LocalDateTime createdTime;
    private String parentCommentId; // 父评论ID，用于回复
    private String replyToAuthor;   // 回复给谁

    // 构造函数
    public ZhihuComment() {}

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnswerId() {
        return answerId;
    }

    public void setAnswerId(String answerId) {
        this.answerId = answerId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(int likeCount) {
        this.likeCount = likeCount;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public String getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(String parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public String getReplyToAuthor() {
        return replyToAuthor;
    }

    public void setReplyToAuthor(String replyToAuthor) {
        this.replyToAuthor = replyToAuthor;
    }

    @Override
    public String toString() {
        return String.format("ZhihuComment{id='%s', authorName='%s', content='%s', likeCount=%d}", 
                id, authorName, content, likeCount);
    }
}