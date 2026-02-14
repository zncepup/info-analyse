package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.ZhihuAuthorDOMapper;
import com.infoanalyse.dao.model.ZhihuAuthorDO;
import com.infoanalyse.zhihu.ZhihuCommand;
import com.infoanalyse.zhihu.service.ZhihuBrowserCrawlerService;
import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/zhihu")
public class ZhihuApiController {
    private final TaskService taskService;
    private final ZhihuCommand zhihuCommand;
    private final ZhihuBrowserCrawlerService crawlerService;
    private final ZhihuAuthorDOMapper authorMapper;

    public ZhihuApiController(TaskService taskService, ZhihuCommand zhihuCommand,
                              ZhihuBrowserCrawlerService crawlerService,
                              ZhihuAuthorDOMapper authorMapper) {
        this.taskService = taskService;
        this.zhihuCommand = zhihuCommand;
        this.crawlerService = crawlerService;
        this.authorMapper = authorMapper;
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
                () -> zhihuCommand.crawlUserAnswers(request.userId, limit, showBrowser, save, withComments));
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
                (task) -> {
                    task.setTotalSteps(withComments ? 3 : 2);
                    task.stepStart("解析链接");
                    String result = zhihuCommand.fetchByUrl(request.url, save, withComments);
                    return result;
                });
    }

    @PostMapping("/sync")
    public TaskInfo sync(@RequestBody SyncRequest request) {
        require(request.userId, "userId is required");
        int limit = request.limit == null ? 50 : request.limit;
        boolean withComments = request.withComments != null && request.withComments;

        // 从作者设置读取 autoAnalyze
        boolean autoAnalyze = true;
        ZhihuAuthorDO author = authorMapper.selectByUserId(request.userId);
        if (author != null && author.getAutoAnalyze() != null) {
            autoAnalyze = author.getAutoAnalyze();
        }

        Map<String, Object> params = new HashMap<>();
        params.put("userId", request.userId);
        params.put("limit", limit);
        params.put("withComments", withComments);
        params.put("autoAnalyze", autoAnalyze);

        final boolean finalAutoAnalyze = autoAnalyze;
        return taskService.submit("zhihu-sync", "同步用户动态", params,
                (task) -> zhihuCommand.syncUserActivities(request.userId, limit, withComments, finalAutoAnalyze, task));
    }

    @PostMapping("/analyze")
    public TaskInfo analyze(@RequestBody AnalyzeRequest request) {
        require(request.file, "file is required");
        Map<String, Object> params = Map.of("file", request.file);
        return taskService.submit("zhihu-analyze", "分析单篇文章", params,
                () -> zhihuCommand.analyzeContent(request.file));
    }

    @PostMapping("/re-crawl-comments")
    public TaskInfo reCrawlComments(@RequestBody TargetRequest request) {
        require(request.source, "source is required");
        require(request.targetType, "targetType is required");
        require(request.targetId, "targetId is required");
        Long targetId = Long.parseLong(request.targetId);
        Map<String, Object> params = Map.of("source", request.source, "targetId", targetId, "targetType", request.targetType);
        return taskService.submit("re-crawl-comments", "同步增量评论", params,
                () -> zhihuCommand.reCrawlComments(request.source, targetId, request.targetType));
    }

    @PostMapping("/re-analyze")
    public TaskInfo reAnalyze(@RequestBody TargetRequest request) {
        require(request.source, "source is required");
        require(request.targetType, "targetType is required");
        require(request.targetId, "targetId is required");
        Long targetId = Long.parseLong(request.targetId);
        Map<String, Object> params = Map.of("source", request.source, "targetId", targetId, "targetType", request.targetType);
        return taskService.submit("re-analyze", "重新AI分析", params,
                () -> zhihuCommand.reAnalyze(request.source, targetId, request.targetType));
    }

    @PostMapping("/export-word")
    public TaskInfo exportWord(@RequestBody ExportRequest request) {
        require(request.file, "file is required");
        Map<String, Object> params = Map.of("file", request.file);
        return taskService.submit("export-word", "导出 Word", params,
                () -> zhihuCommand.exportWord(request.file));
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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

    public static class TargetRequest {
        public String source;
        public String targetId;
        public String targetType;
    }

    public static class ExportRequest {
        public String file;
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
