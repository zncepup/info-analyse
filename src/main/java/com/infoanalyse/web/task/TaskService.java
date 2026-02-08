package com.infoanalyse.web.task;

import com.infoanalyse.dao.mapper.CrawlTaskDOMapper;
import com.infoanalyse.dao.model.CrawlTaskDO;
import com.infoanalyse.dao.model.CrawlTaskDOExample;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class TaskService {
    private static final int MAX_MEMORY_TASKS = 200;

    private final ExecutorService executor;
    private final ConcurrentHashMap<String, TaskInfo> tasks;
    private final ConcurrentLinkedDeque<String> order;
    private final CrawlTaskDOMapper crawlTaskMapper;

    public TaskService(CrawlTaskDOMapper crawlTaskMapper) {
        this.crawlTaskMapper = crawlTaskMapper;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zhihu-task-runner");
            t.setDaemon(true);
            return t;
        });
        this.tasks = new ConcurrentHashMap<>();
        this.order = new ConcurrentLinkedDeque<>();
        loadHistoryTasks();
    }

    /**
     * 启动时从DB加载历史任务（最近100条）
     */
    private void loadHistoryTasks() {
        try {
            CrawlTaskDOExample example = new CrawlTaskDOExample();
            example.setOrderByClause("created_time DESC");
            List<CrawlTaskDO> dbTasks = crawlTaskMapper.selectByExampleWithBLOBs(example);
            int limit = Math.min(dbTasks.size(), 100);
            for (int i = limit - 1; i >= 0; i--) {
                CrawlTaskDO dt = dbTasks.get(i);
                TaskInfo info = fromDb(dt);
                tasks.put(info.getId(), info);
                order.addFirst(info.getId());
            }
        } catch (Exception e) {
            System.err.println("加载历史任务失败: " + e.getMessage());
        }
    }

    public TaskInfo submit(String type, String title, Map<String, Object> params, TaskRunner runner) {
        return submit(type, title, params, (task) -> runner.run());
    }

    public TaskInfo submit(String type, String title, Map<String, Object> params, ProgressTaskRunner runner) {
        String id = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo(id, type, title, params, System.currentTimeMillis());
        tasks.put(id, task);
        order.addFirst(id);
        trimOld();

        persistToDb(task);

        executor.submit(() -> {
            task.markRunning();
            updateDbStatus(task);
            try {
                String result = runner.run(task);
                task.markCompleted(result);
            } catch (Exception e) {
                task.markFailed(e.getMessage() == null ? "Task failed" : e.getMessage());
            }
            updateDbStatus(task);
        });

        return task;
    }

    public List<TaskInfo> list() {
        List<TaskInfo> result = new ArrayList<>();
        for (String id : order) {
            TaskInfo task = tasks.get(id);
            if (task != null) {
                result.add(task);
            }
        }
        return result;
    }

    public Optional<TaskInfo> find(String id) {
        return Optional.ofNullable(tasks.get(id));
    }

    private void trimOld() {
        while (order.size() > MAX_MEMORY_TASKS) {
            String id = order.pollLast();
            if (id != null) {
                tasks.remove(id);
            }
        }
    }

    private void persistToDb(TaskInfo task) {
        try {
            CrawlTaskDO record = new CrawlTaskDO();
            record.setSource(task.getType());
            record.setTaskType(task.getType());
            record.setTargetId(task.getId());
            record.setStatus(task.getStatus().name());
            record.setMessage(task.getTitle());
            record.setCreatedTime(java.time.LocalDateTime.now());
            crawlTaskMapper.insertSelective(record);
            task.setDbId(record.getId());
        } catch (Exception e) {
            System.err.println("任务持久化失败: " + e.getMessage());
        }
    }

    private void updateDbStatus(TaskInfo task) {
        try {
            if (task.getDbId() == null) return;
            CrawlTaskDO record = new CrawlTaskDO();
            record.setId(task.getDbId());
            record.setStatus(task.getStatus().name());
            record.setMessage(task.getMessage() != null ? task.getMessage() : task.getError());
            if (task.getFinishedAt() != null) {
                record.setFinishedTime(java.time.Instant.ofEpochMilli(task.getFinishedAt())
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
            crawlTaskMapper.updateByPrimaryKeySelective(record);
        } catch (Exception e) {
            System.err.println("任务状态更新失败: " + e.getMessage());
        }
    }

    private TaskInfo fromDb(CrawlTaskDO dt) {
        long createdAt = dt.getCreatedTime() != null
                ? dt.getCreatedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0;
        String taskId = dt.getTargetId() != null ? dt.getTargetId() : "db-" + dt.getId();
        TaskInfo info = new TaskInfo(taskId, dt.getTaskType(), dt.getMessage(), Map.of(), createdAt);
        info.setDbId(dt.getId());
        String status = dt.getStatus();
        if ("RUNNING".equals(status)) {
            info.markFailed("应用重启，任务中断");
        } else if ("COMPLETED".equals(status)) {
            info.markCompleted(dt.getMessage());
        } else if ("FAILED".equals(status)) {
            info.markFailed(dt.getMessage());
        }
        if (dt.getFinishedTime() != null) {
            info.setFinishedAtFromDb(dt.getFinishedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
        }
        return info;
    }

    @FunctionalInterface
    public interface TaskRunner {
        String run();
    }

    @FunctionalInterface
    public interface ProgressTaskRunner {
        String run(TaskInfo task);
    }
}
