package com.infoanalyse.zhihu.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知乎回答实体
 */
public class ZhihuAnswer {
    private String id;
    private String questionId;
    private String questionTitle;
    private String authorName;
    private String authorId;
    private String content;
    private String htmlContent;
    private int voteupCount;
    private int commentCount;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private String url;
    private List<ZhihuComment> comments;

    public ZhihuAnswer() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getQuestionId() { return questionId; }
    public void setQuestionId(String questionId) { this.questionId = questionId; }

    public String getQuestionTitle() { return questionTitle; }
    public void setQuestionTitle(String questionTitle) { this.questionTitle = questionTitle; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public int getVoteupCount() { return voteupCount; }
    public void setVoteupCount(int voteupCount) { this.voteupCount = voteupCount; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }

    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public List<ZhihuComment> getComments() { return comments; }
    public void setComments(List<ZhihuComment> comments) { this.comments = comments; }
}
