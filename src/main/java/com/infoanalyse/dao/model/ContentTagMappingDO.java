package com.infoanalyse.dao.model;

import java.util.Date;

public class ContentTagMappingDO {
    private Long id;
    private Long tagId;
    private String source;
    private Long targetId;
    private String targetType;
    private Date createdTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Date getCreatedTime() { return createdTime; }
    public void setCreatedTime(Date createdTime) { this.createdTime = createdTime; }
}
