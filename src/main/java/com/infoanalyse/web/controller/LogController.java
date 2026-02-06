package com.infoanalyse.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {
    @GetMapping
    public Map<String, Object> getLogs(@RequestParam(name = "lines", defaultValue = "200") int lines) {
        Map<String, Object> result = new HashMap<>();
        Path logPath = Path.of("info-analyse.log").toAbsolutePath().normalize();

        if (!Files.exists(logPath)) {
            result.put("lines", List.of("log file not found: " + logPath));
            result.put("file", logPath.toString());
            return result;
        }

        try {
            List<String> allLines = Files.readAllLines(logPath);
            int start = Math.max(0, allLines.size() - Math.max(1, lines));
            result.put("lines", allLines.subList(start, allLines.size()));
            result.put("file", logPath.toString());
            return result;
        } catch (Exception e) {
            result.put("lines", List.of("failed to read log: " + e.getMessage()));
            result.put("file", logPath.toString());
            return result;
        }
    }
}
