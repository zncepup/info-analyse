package com.infoanalyse.web.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Collections;
import java.util.Map;

public class TaskInfo {
    private final String id;
    private final String type;
    private final String title;
    private final Map<String, Object> params;
    private final long createdAt;

    private volatile TaskStatus status;
    private volatile int progress;
    private volatile String message;
    private volatile String error;
    private volatile Long startedAt;
    private volatile Long finishedAt;
    @JsonIgnore
    private volatile Long dbId;

    public TaskInfo(String id, String type, String title, Map<String, Object> params, long createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(params);
        this.createdAt = createdAt;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public int getProgress() {
        return progress;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public Long getDbId() {
        return dbId;
    }

    public void setDbId(Long dbId) {
        this.dbId = dbId;
    }

    public void setFinishedAtFromDb(long millis) {
        this.finishedAt = millis;
    }

    public void markRunning() {
        status = TaskStatus.RUNNING;
        startedAt = System.currentTimeMillis();
        progress = 10;
    }

    public void markCompleted(String message) {
        status = TaskStatus.COMPLETED;
        progress = 100;
        this.message = message;
        finishedAt = System.currentTimeMillis();
    }

    public void markFailed(String error) {
        status = TaskStatus.FAILED;
        progress = 100;
        this.error = error;
        finishedAt = System.currentTimeMillis();
    }
}
