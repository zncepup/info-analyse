package com.infoanalyse.zhihu.model;

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
    private String parentCommentId;
    private String replyCommentId;
    private String replyToAuthor;

    public ZhihuComment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAnswerId() { return answerId; }
    public void setAnswerId(String answerId) { this.answerId = answerId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public String getParentCommentId() { return parentCommentId; }
    public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }

    public String getReplyCommentId() { return replyCommentId; }
    public void setReplyCommentId(String replyCommentId) { this.replyCommentId = replyCommentId; }

    public String getReplyToAuthor() { return replyToAuthor; }
    public void setReplyToAuthor(String replyToAuthor) { this.replyToAuthor = replyToAuthor; }
}
