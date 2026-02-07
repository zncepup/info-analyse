package com.infoanalyse.web.controller;

import com.infoanalyse.zhihu.ZhihuCommand;
import com.infoanalyse.zhihu.service.ZhihuBrowserCrawlerService;
import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/zhihu")
public class ZhihuApiController {
    private final TaskService taskService;
    private final ZhihuCommand zhihuCommand;
    private final ZhihuBrowserCrawlerService crawlerService;
    private final Path outputDir = Path.of("output").toAbsolutePath().normalize();

    public ZhihuApiController(TaskService taskService, ZhihuCommand zhihuCommand, ZhihuBrowserCrawlerService crawlerService) {
        this.taskService = taskService;
        this.zhihuCommand = zhihuCommand;
        this.crawlerService = crawlerService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("loggedIn", crawlerService.isLoggedIn());
        data.put("hasCookies", crawlerService.hasSavedCookies());
        return data;
    }

    @GetMapping("/help")
    public Map<String, Object> help() {
        return Map.of("help", zhihuCommand.showHelp());
    }

    @PostMapping("/login")
    public TaskInfo login() {
        return taskService.submit("login", "打开登录浏览器", Map.of(), zhihuCommand::login);
    }

    @PostMapping("/save-cookies")
    public TaskInfo saveCookies() {
        return taskService.submit("save-cookies", "保存登录 cookies", Map.of(), zhihuCommand::saveCookies);
    }

    @PostMapping("/login/qr/session")
    public QrLoginResponse startQrLogin() {
        ZhihuBrowserCrawlerService.QrLoginSnapshot snapshot = crawlerService.startQrLoginSession();
        return toQrResponse(snapshot);
    }

    @GetMapping("/login/qr/session/{sessionId}")
    public QrLoginResponse getQrLogin(@PathVariable("sessionId") String sessionId) {
        try {
            ZhihuBrowserCrawlerService.QrLoginSnapshot snapshot = crawlerService.getQrLoginSession(sessionId);
            return toQrResponse(snapshot);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/login/qr/session/{sessionId}/cancel")
    public ResponseEntity<Void> cancelQrLogin(@PathVariable("sessionId") String sessionId) {
        try {
            crawlerService.cancelQrLoginSession(sessionId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/user")
    public TaskInfo crawlUser(@RequestBody UserRequest request) {
        require(request.userId, "userId is required");
        int limit = request.limit == null ? 10 : request.limit;
        boolean showBrowser = request.showBrowser != null && request.showBrowser;
        boolean save = request.save != null && request.save;
        boolean withComments = request.withComments != null && request.withComments;

        Map<String, Object> params = new HashMap<>();
        params.put("userId", request.userId);
        params.put("limit", limit);
        params.put("showBrowser", showBrowser);
        params.put("save", save);
        params.put("withComments", withComments);

        return taskService.submit("zhihu-user", "抓取用户回答", params,
                () -> {
                    long startTime = System.currentTimeMillis();
                    String result = zhihuCommand.crawlUserAnswers(request.userId, limit, showBrowser, save, withComments);
                    String analysis = save ? autoAnalyzeNewFiles(startTime) : "自动分析: 已关闭";
                    return result + " | " + analysis;
                });
    }

    @PostMapping("/fetch")
    public TaskInfo fetchByUrl(@RequestBody FetchRequest request) {
        require(request.url, "url is required");
        boolean save = request.save != null && request.save;
        boolean withComments = request.withComments != null && request.withComments;

        Map<String, Object> params = new HashMap<>();
        params.put("url", request.url);
        params.put("save", save);
        params.put("withComments", withComments);

        return taskService.submit("zhihu-fetch", "抓取链接内容", params,
                () -> {
                    long startTime = System.currentTimeMillis();
                    String result = zhihuCommand.fetchByUrl(request.url, save, withComments);
                    String analysis = save ? autoAnalyzeNewFiles(startTime) : "自动分析: 已关闭";
                    return result + " | " + analysis;
                });
    }

    @PostMapping("/sync")
    public TaskInfo sync(@RequestBody SyncRequest request) {
        require(request.userId, "userId is required");
        int limit = request.limit == null ? 50 : request.limit;
        boolean withComments = request.withComments != null && request.withComments;

        Map<String, Object> params = new HashMap<>();
        params.put("userId", request.userId);
        params.put("limit", limit);
        params.put("withComments", withComments);

        return taskService.submit("zhihu-sync", "同步用户动态", params,
                () -> {
                    long startTime = System.currentTimeMillis();
                    String result = zhihuCommand.syncUserActivities(request.userId, limit, withComments);
                    String analysis = autoAnalyzeNewFiles(startTime);
                    return result + " | " + analysis;
                });
    }

    @PostMapping("/analyze")
    public TaskInfo analyze(@RequestBody AnalyzeRequest request) {
        require(request.file, "file is required");
        Map<String, Object> params = Map.of("file", request.file);
        return taskService.submit("zhihu-analyze", "分析单篇文章", params,
                () -> zhihuCommand.analyzeContent(request.file));
    }

    @PostMapping("/analyze-all")
    public TaskInfo analyzeAll(@RequestBody AnalyzeAllRequest request) {
        require(request.author, "author is required");
        int delay = request.delay == null ? 3 : request.delay;
        Map<String, Object> params = new HashMap<>();
        params.put("author", request.author);
        params.put("delay", delay);
        return taskService.submit("zhihu-analyze-all", "批量分析作者内容", params,
                () -> zhihuCommand.analyzeAll(request.author, delay));
    }

    @PostMapping("/export-word")
    public TaskInfo exportWord(@RequestBody ExportRequest request) {
        require(request.file, "file is required");
        Map<String, Object> params = Map.of("file", request.file);
        return taskService.submit("export-word", "导出 Word", params,
                () -> zhihuCommand.exportWord(request.file));
    }

    @PostMapping("/export-word-all")
    public TaskInfo exportWordAll(@RequestBody ExportAllRequest request) {
        require(request.author, "author is required");
        Map<String, Object> params = Map.of("author", request.author);
        return taskService.submit("export-word-all", "批量导出 Word", params,
                () -> zhihuCommand.exportWordAll(request.author));
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private String autoAnalyzeNewFiles(long sinceMillis) {
        long threshold = Math.max(0, sinceMillis - 2000);
        java.util.List<Path> newFiles = findMarkdownFilesSince(threshold);
        if (newFiles.isEmpty()) {
            return "自动分析: 无新增内容";
        }
        int analyzed = 0;
        int failed = 0;
        for (Path file : newFiles) {
            try {
                String result = zhihuCommand.analyzeContent(file.toString());
                if (result != null && result.contains("完成")) {
                    analyzed++;
                }
            } catch (Exception e) {
                failed++;
            }
        }
        if (failed > 0) {
            return "自动分析: 已分析 " + analyzed + " 篇，失败 " + failed + " 篇";
        }
        return "自动分析: 已分析 " + analyzed + " 篇";
    }

    private java.util.List<Path> findMarkdownFilesSince(long sinceMillis) {
        if (!Files.exists(outputDir)) {
            return java.util.List.of();
        }
        try (Stream<Path> stream = Files.walk(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return !name.equalsIgnoreCase("INDEX.md") && !name.endsWith("_analysis.md");
                    })
                    .filter(path -> lastModifiedSafe(path) >= sinceMillis)
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe))
                    .toList();
        } catch (IOException e) {
            return java.util.List.of();
        }
    }

    private long lastModifiedSafe(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private QrLoginResponse toQrResponse(ZhihuBrowserCrawlerService.QrLoginSnapshot snapshot) {
        return new QrLoginResponse(
                snapshot.sessionId(),
                snapshot.status().name(),
                snapshot.qrImage(),
                snapshot.message(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.expiresAt()
        );
    }

    public static class UserRequest {
        public String userId;
        public Integer limit;
        public Boolean showBrowser;
        public Boolean save;
        public Boolean withComments;
    }

    public static class FetchRequest {
        public String url;
        public Boolean save;
        public Boolean withComments;
    }

    public static class SyncRequest {
        public String userId;
        public Integer limit;
        public Boolean withComments;
    }

    public static class AnalyzeRequest {
        public String file;
    }

    public static class AnalyzeAllRequest {
        public String author;
        public Integer delay;
    }

    public static class ExportRequest {
        public String file;
    }

    public static class ExportAllRequest {
        public String author;
    }

    public record QrLoginResponse(
            String sessionId,
            String status,
            String qrImage,
            String message,
            long createdAt,
            long updatedAt,
            long expiresAt
    ) {
    }
}
