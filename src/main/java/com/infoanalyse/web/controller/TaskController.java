package com.infoanalyse.web.controller;

import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public List<TaskInfo> list() {
        return taskService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskInfo> get(@PathVariable("id") String id) {
        return taskService.find(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
