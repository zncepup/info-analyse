package com.infoanalyse.web.controller;

import com.infoanalyse.eastmoney.model.GubaPost;
import com.infoanalyse.eastmoney.service.GubaCrawlerService;
import com.infoanalyse.eastmoney.service.GubaDbSaveService;
import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guba")
public class GubaApiController {

    private final TaskService taskService;
    private final GubaCrawlerService crawlerService;
    private final GubaDbSaveService dbSaveService;

    public GubaApiController(TaskService taskService, GubaCrawlerService crawlerService, GubaDbSaveService dbSaveService) {
        this.taskService = taskService;
        this.crawlerService = crawlerService;
        this.dbSaveService = dbSaveService;
    }

    /**
     * 抓取股吧帖子列表
     */
    @PostMapping("/crawl")
    public TaskInfo crawlPosts(@RequestBody CrawlRequest request) {
        require(request.stockCode, "stockCode is required");
        int pages = request.pages != null ? request.pages : 1;
        boolean withComments = request.withComments != null && request.withComments;
        boolean save = request.save != null && request.save;

        Map<String, Object> params = new HashMap<>();
        params.put("stockCode", request.stockCode);
        params.put("pages", pages);
        params.put("withComments", withComments);
        params.put("save", save);

        final int finalPages = pages;
        return taskService.submit("guba-crawl", "抓取股吧帖子", params, () -> {
            List<GubaPost> posts = crawlerService.crawlPostsWithComments(request.stockCode, finalPages, withComments);
            if (save && !posts.isEmpty()) {
                int savedCount = dbSaveService.savePosts(posts);
                return "抓取完成: " + posts.size() + " 条帖子, 已保存 " + savedCount + " 条到数据库";
            }
            return "抓取完成: " + posts.size() + " 条帖子";
        });
    }

    /**
     * 抓取单个帖子详情及评论
     */
    @PostMapping("/detail")
    public TaskInfo crawlDetail(@RequestBody DetailRequest request) {
        require(request.stockCode, "stockCode is required");
        require(request.postId, "postId is required");
        boolean save = request.save != null && request.save;

        Map<String, Object> params = new HashMap<>();
        params.put("stockCode", request.stockCode);
        params.put("postId", request.postId);
        params.put("save", save);

        return taskService.submit("guba-detail", "抓取帖子详情", params, () -> {
            GubaPost post = crawlerService.crawlPostDetail(request.stockCode, request.postId);
            if (save) {
                dbSaveService.savePost(post);
                return "抓取完成: " + (post.getComments() != null ? post.getComments().size() : 0) + " 条评论, 已保存到数据库";
            }
            return "抓取完成: " + (post.getComments() != null ? post.getComments().size() : 0) + " 条评论";
        });
    }

    private void require(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    public static class CrawlRequest {
        public String stockCode;
        public Integer pages;
        public Boolean withComments;
        public Boolean save;
    }

    public static class DetailRequest {
        public String stockCode;
        public String postId;
        public Boolean save;
    }
}
