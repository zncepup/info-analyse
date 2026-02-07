package com.infoanalyse.eastmoney.model;

import java.time.LocalDateTime;

/**
 * 东方财富股吧评论实体
 */
public class GubaComment {
    private String commentId;
    private String postId;
    private String authorName;
    private String authorId;
    private String content;
    private int likeCount;
    private LocalDateTime publishTime;
    private String replyToUser;
    private String replyToCommentId;

    public GubaComment() {}

    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getLikeCount() { return likeCount; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }

    public String getReplyToUser() { return replyToUser; }
    public void setReplyToUser(String replyToUser) { this.replyToUser = replyToUser; }

    public String getReplyToCommentId() { return replyToCommentId; }
    public void setReplyToCommentId(String replyToCommentId) { this.replyToCommentId = replyToCommentId; }
}
