package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.*;
import com.infoanalyse.dao.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 内容列表控制器 - 从数据库读取
 */
@RestController
@RequestMapping("/api/outputs")
public class OutputController {

    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final GubaPostDOMapper gubaPostMapper;
    private final AiAnalysisDOMapper aiAnalysisMapper;

    public OutputController(ZhihuAnswerDOMapper answerMapper,
                            ZhihuArticleDOMapper articleMapper,
                            GubaPostDOMapper gubaPostMapper,
                            AiAnalysisDOMapper aiAnalysisMapper) {
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.gubaPostMapper = gubaPostMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
    }

    /**
     * 列出所有作者（按来源分组）
     */
    @GetMapping
    public List<AuthorInfo> listAuthors() {
        List<AuthorInfo> authors = new ArrayList<>();

        // 知乎作者: 从回答和文章中提取 distinct author
        Map<String, long[]> zhihuAuthors = new LinkedHashMap<>();

        // 回答按作者分组
        List<ZhihuAnswerDO> answers = answerMapper.selectByExample(new ZhihuAnswerDOExample());
        for (ZhihuAnswerDO a : answers) {
            String author = a.getAuthorName() != null ? a.getAuthorName() : "unknown";
            zhihuAuthors.computeIfAbsent(author, k -> new long[]{0, 0, 0}); // [answerCount, articleCount, lastCrawlTime]
            zhihuAuthors.get(author)[0]++;
            long ct = a.getCrawlTime() != null ? toEpochMilli(a.getCrawlTime()) : 0;
            if (ct > zhihuAuthors.get(author)[2]) zhihuAuthors.get(author)[2] = ct;
        }

        // 文章按作者分组
        List<ZhihuArticleDO> articles = articleMapper.selectByExample(new ZhihuArticleDOExample());
        for (ZhihuArticleDO a : articles) {
            String author = a.getAuthorName() != null ? a.getAuthorName() : "unknown";
            zhihuAuthors.computeIfAbsent(author, k -> new long[]{0, 0, 0});
            zhihuAuthors.get(author)[1]++;
            long ct = a.getCrawlTime() != null ? toEpochMilli(a.getCrawlTime()) : 0;
            if (ct > zhihuAuthors.get(author)[2]) zhihuAuthors.get(author)[2] = ct;
        }

        for (Map.Entry<String, long[]> entry : zhihuAuthors.entrySet()) {
            long[] counts = entry.getValue();
            int total = (int) (counts[0] + counts[1]);
            authors.add(new AuthorInfo(entry.getKey(), total, 0, counts[2]));
        }

        authors.sort(Comparator.comparingLong(AuthorInfo::lastModified).reversed());
        return authors;
    }

    /**
     * 列出某个作者/分组下的所有内容
     */
    @GetMapping("/{author}/files")
    public List<FileInfo> listFiles(@PathVariable("author") String author) {
        List<FileInfo> files = new ArrayList<>();

        if (author.startsWith("guba_")) {
            // 股吧分组: 解析 stockCode
            String stockCode = extractStockCode(author);
            GubaPostDOExample example = new GubaPostDOExample();
            example.createCriteria().andStockCodeEqualTo(stockCode);
            example.setOrderByClause("crawl_time DESC");
            List<GubaPostDO> posts = gubaPostMapper.selectByExampleWithBLOBs(example);
            for (GubaPostDO p : posts) {
                boolean analyzed = isAnalyzed("guba", p.getPostId(), "post");
                long modified = p.getCrawlTime() != null ? toEpochMilli(p.getCrawlTime()) : 0;
                files.add(new FileInfo(
                        p.getTitle() != null ? p.getTitle() : "帖子" + p.getPostId(),
                        "guba/post/" + p.getPostId(),
                        p.getContent() != null ? p.getContent().length() : 0,
                        modified, "guba_post",
                        "/view/guba/post/" + p.getPostId(), null, analyzed));
            }
        } else {
            // 知乎作者
            ZhihuAnswerDOExample aExample = new ZhihuAnswerDOExample();
            aExample.createCriteria().andAuthorNameEqualTo(author);
            aExample.setOrderByClause("crawl_time DESC");
            List<ZhihuAnswerDO> answers = answerMapper.selectByExampleWithBLOBs(aExample);
            for (ZhihuAnswerDO a : answers) {
                boolean analyzed = isAnalyzed("zhihu", a.getAnswerId(), "answer");
                long modified = a.getCrawlTime() != null ? toEpochMilli(a.getCrawlTime()) : 0;
                files.add(new FileInfo(
                        a.getQuestionTitle() != null ? a.getQuestionTitle() : "回答" + a.getAnswerId(),
                        "zhihu/answer/" + a.getAnswerId(),
                        a.getContent() != null ? a.getContent().length() : 0,
                        modified, "answer",
                        "/view/zhihu/answer/" + a.getAnswerId(), null, analyzed));
            }

            ZhihuArticleDOExample artExample = new ZhihuArticleDOExample();
            artExample.createCriteria().andAuthorNameEqualTo(author);
            artExample.setOrderByClause("crawl_time DESC");
            List<ZhihuArticleDO> arts = articleMapper.selectByExampleWithBLOBs(artExample);
            for (ZhihuArticleDO a : arts) {
                boolean analyzed = isAnalyzed("zhihu", a.getArticleId(), "article");
                long modified = a.getCrawlTime() != null ? toEpochMilli(a.getCrawlTime()) : 0;
                files.add(new FileInfo(
                        a.getTitle() != null ? a.getTitle() : "文章" + a.getArticleId(),
                        "zhihu/article/" + a.getArticleId(),
                        a.getContent() != null ? a.getContent().length() : 0,
                        modified, "article",
                        "/view/zhihu/article/" + a.getArticleId(), null, analyzed));
            }

            files.sort(Comparator.comparingLong(FileInfo::lastModified).reversed());
        }

        return files;
    }

    /**
     * 列出所有已导出的 Word 文件（仍从文件系统读取）
     */
    @GetMapping("/exports")
    public List<FileInfo> listExports() {
        // Word 导出文件仍在文件系统，保留原逻辑
        java.nio.file.Path outputDir = java.nio.file.Path.of("output").toAbsolutePath().normalize();
        if (!java.nio.file.Files.exists(outputDir)) return List.of();

        List<FileInfo> files = new ArrayList<>();
        try (var stream = java.nio.file.Files.walk(outputDir)) {
            stream.filter(java.nio.file.Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".docx"))
                    .forEach(p -> {
                        try {
                            java.nio.file.Path authorDir = p.getParent();
                            while (authorDir != null && authorDir.getParent() != null
                                    && !authorDir.getParent().equals(outputDir)) {
                                authorDir = authorDir.getParent();
                            }
                            if (authorDir == null || authorDir.getParent() == null) return;
                            String authorName = authorDir.getFileName().toString();
                            String relative = authorDir.relativize(p).toString().replace("\\", "/");
                            String name = p.getFileName().toString();
                            long size = java.nio.file.Files.size(p);
                            long modified = java.nio.file.Files.getLastModifiedTime(p).toMillis();
                            String downloadUrl = "/output/" + authorName + "/" + relative;
                            files.add(new FileInfo(name, relative, size, modified, "docx", null, downloadUrl, false));
                        } catch (java.io.IOException ignored) {}
                    });
        } catch (java.io.IOException ignored) {}

        files.sort(Comparator.comparingLong(FileInfo::lastModified).reversed());
        return files;
    }

    private boolean isAnalyzed(String source, Long targetId, String targetType) {
        AiAnalysisDOExample example = new AiAnalysisDOExample();
        example.createCriteria()
                .andSourceEqualTo(source)
                .andTargetIdEqualTo(targetId)
                .andTargetTypeEqualTo(targetType)
                .andStatusEqualTo("COMPLETED");
        return aiAnalysisMapper.countByExample(example) > 0;
    }

    private String extractStockCode(String groupName) {
        // guba_600519_贵州茅台 -> 600519
        String[] parts = groupName.split("_", 3);
        return parts.length >= 2 ? parts[1] : "";
    }

    private long toEpochMilli(java.time.LocalDateTime ldt) {
        return ldt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public record AuthorInfo(String name, int mdCount, int docCount, long lastModified) {}

    public record FileInfo(String name, String relativePath, long size, long lastModified, String type,
                           String viewUrl, String downloadUrl, boolean analyzed) {}
}
