package com.infoanalyse.web.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // 进度跟踪
    private volatile int totalSteps;
    private volatile int completedSteps;
    private volatile String currentStep;
    private final List<String> completedItems = Collections.synchronizedList(new ArrayList<>());

    // 分阶段进度: key=阶段名, value=PhaseProgress
    private final ConcurrentHashMap<String, PhaseProgress> phases = new ConcurrentHashMap<>();
    // 保持阶段插入顺序
    private final List<String> phaseOrder = Collections.synchronizedList(new ArrayList<>());

    public TaskInfo(String id, String type, String title, Map<String, Object> params, long createdAt) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(params);
        this.createdAt = createdAt;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public Map<String, Object> getParams() { return params; }
    public long getCreatedAt() { return createdAt; }
    public TaskStatus getStatus() { return status; }
    public int getProgress() { return progress; }
    public String getMessage() { return message; }
    public String getError() { return error; }
    public Long getStartedAt() { return startedAt; }
    public Long getFinishedAt() { return finishedAt; }
    public Long getDbId() { return dbId; }
    public void setDbId(Long dbId) { this.dbId = dbId; }

    public void setFinishedAtFromDb(long millis) {
        this.finishedAt = millis;
    }

    public void markRunning() {
        status = TaskStatus.RUNNING;
        startedAt = System.currentTimeMillis();
        progress = 5;
    }

    public void markCompleted(String message) {
        status = TaskStatus.COMPLETED;
        progress = 100;
        this.message = message;
        this.currentStep = null;
        finishedAt = System.currentTimeMillis();
    }

    public void markFailed(String error) {
        status = TaskStatus.FAILED;
        progress = 100;
        this.error = error;
        this.currentStep = null;
        finishedAt = System.currentTimeMillis();
    }

    // ===== 总步骤跟踪 =====
    public void setTotalSteps(int total) { this.totalSteps = total; }

    public void stepStart(String description) { this.currentStep = description; }

    public void stepDone(String description) {
        this.completedSteps++;
        this.completedItems.add(description);
        this.currentStep = null;
        recalcProgress();
    }

    public int getTotalSteps() { return totalSteps; }
    public int getCompletedSteps() { return completedSteps; }
    public String getCurrentStep() { return currentStep; }
    public List<String> getCompletedItems() { return Collections.unmodifiableList(completedItems); }

    // ===== 分阶段进度 =====

    /**
     * 初始化一个阶段的总数
     */
    public void phaseInit(String phase, int total) {
        phases.put(phase, new PhaseProgress(total));
        if (!phaseOrder.contains(phase)) {
            phaseOrder.add(phase);
        }
    }

    /**
     * 标记一个阶段完成一项
     */
    public void phaseDone(String phase) {
        PhaseProgress p = phases.get(phase);
        if (p != null) p.done++;
    }

    /**
     * 标记一个阶段跳过一项
     */
    public void phaseSkip(String phase) {
        PhaseProgress p = phases.get(phase);
        if (p != null) p.skipped++;
    }

    /**
     * 标记一个阶段失败一项
     */
    public void phaseFail(String phase) {
        PhaseProgress p = phases.get(phase);
        if (p != null) p.failed++;
    }

    /**
     * 返回有序的阶段进度 map，供 JSON 序列化
     */
    public Map<String, PhaseProgress> getPhases() {
        Map<String, PhaseProgress> ordered = new LinkedHashMap<>();
        for (String key : phaseOrder) {
            PhaseProgress p = phases.get(key);
            if (p != null) ordered.put(key, p);
        }
        return ordered;
    }

    /**
     * 预估剩余时间（毫秒），基于已完成步骤的平均耗时
     */
    public Long getEstimatedRemainingMs() {
        if (status != TaskStatus.RUNNING || completedSteps == 0 || startedAt == null || totalSteps <= completedSteps) {
            return null;
        }
        long elapsed = System.currentTimeMillis() - startedAt;
        double avgPerStep = (double) elapsed / completedSteps;
        int remaining = totalSteps - completedSteps;
        return (long)(avgPerStep * remaining);
    }

    private void recalcProgress() {
        if (totalSteps > 0) {
            this.progress = Math.min(95, 5 + (int)(90.0 * completedSteps / totalSteps));
        }
    }

    /**
     * 阶段进度数据
     */
    public static class PhaseProgress {
        public volatile int total;
        public volatile int done;
        public volatile int skipped;
        public volatile int failed;

        public PhaseProgress() {}
        public PhaseProgress(int total) { this.total = total; }

        public int getTotal() { return total; }
        public int getDone() { return done; }
        public int getSkipped() { return skipped; }
        public int getFailed() { return failed; }
        public int getRemaining() { return total - done - skipped - failed; }
    }
}
