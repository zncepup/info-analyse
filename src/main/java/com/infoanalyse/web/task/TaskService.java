package com.infoanalyse.web.task;

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
    private static final int MAX_TASKS = 200;

    private final ExecutorService executor;
    private final ConcurrentHashMap<String, TaskInfo> tasks;
    private final ConcurrentLinkedDeque<String> order;

    public TaskService() {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "zhihu-task-runner");
            t.setDaemon(true);
            return t;
        });
        this.tasks = new ConcurrentHashMap<>();
        this.order = new ConcurrentLinkedDeque<>();
    }

    public TaskInfo submit(String type, String title, Map<String, Object> params, TaskRunner runner) {
        String id = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo(id, type, title, params, System.currentTimeMillis());
        tasks.put(id, task);
        order.addFirst(id);
        trimOld();

        executor.submit(() -> {
            task.markRunning();
            try {
                String result = runner.run();
                task.markCompleted(result);
            } catch (Exception e) {
                task.markFailed(e.getMessage() == null ? "Task failed" : e.getMessage());
            }
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
        while (order.size() > MAX_TASKS) {
            String id = order.pollLast();
            if (id != null) {
                tasks.remove(id);
            }
        }
    }

    @FunctionalInterface
    public interface TaskRunner {
        String run();
    }
}
