package com.infoanalyse.web.controller;

import com.infoanalyse.commons.service.WordExportService;
import com.infoanalyse.dao.mapper.*;
import com.infoanalyse.dao.model.*;
import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);

    private final TaskService taskService;
    private final WordExportService wordExportService;
    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final ZhihuPinDOMapper pinMapper;
    private final GubaPostDOMapper gubaPostMapper;
    private final ZhihuCommentDOMapper commentMapper;
    private final AiAnalysisDOMapper aiAnalysisMapper;

    public ExportController(TaskService taskService, WordExportService wordExportService,
                            ZhihuAnswerDOMapper answerMapper, ZhihuArticleDOMapper articleMapper,
                            ZhihuPinDOMapper pinMapper, GubaPostDOMapper gubaPostMapper,
                            ZhihuCommentDOMapper commentMapper, AiAnalysisDOMapper aiAnalysisMapper) {
        this.taskService = taskService;
        this.wordExportService = wordExportService;
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.pinMapper = pinMapper;
        this.gubaPostMapper = gubaPostMapper;
        this.commentMapper = commentMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
    }

    /**
     * 批量导出多篇内容为单个 Word 文件
     */
    @PostMapping("/batch")
    public TaskInfo batchExport(@RequestBody BatchExportRequest req) {
        if (req.contents == null || req.contents.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "内容列表不能为空");
        }
        boolean includeBody = req.includeBody == null || req.includeBody;
        boolean includeComments = req.includeComments != null && req.includeComments;
        boolean includeAi = req.includeAi != null && req.includeAi;

        Map<String, Object> params = Map.of("count", req.contents.size(),
                "includeBody", includeBody, "includeComments", includeComments, "includeAi", includeAi);

        return taskService.submit("batch-export", "批量导出 " + req.contents.size() + " 篇内容", params,
                (task) -> {
                    task.setTotalSteps(req.contents.size());
                    StringBuilder allContent = new StringBuilder();
                    int count = 0;
                    String firstTitle = null;

                    for (ContentRef ref : req.contents) {
                        count++;
                        task.stepStart("[" + count + "/" + req.contents.size() + "] " + ref.targetType + "/" + ref.targetId);

                        String title = loadTitle(ref.source, ref.targetType, Long.parseLong(ref.targetId));
                        if (firstTitle == null) firstTitle = title;

                        if (allContent.length() > 0) {
                            allContent.append("\n\n---\n\n");
                        }
                        allContent.append("# ").append(title != null ? title : ref.targetType + "/" + ref.targetId).append("\n\n");

                        if (includeBody) {
                            String body = loadContent(ref.source, ref.targetType, Long.parseLong(ref.targetId));
                            if (body != null && !body.isBlank()) {
                                allContent.append(body).append("\n\n");
                            }
                        }

                        if (includeComments) {
                            String comments = loadComments(ref.source, ref.targetType, Long.parseLong(ref.targetId));
                            if (comments != null && !comments.isBlank()) {
                                allContent.append("## 评论\n\n").append(comments).append("\n\n");
                            }
                        }

                        if (includeAi) {
                            String ai = loadAiAnalysis(ref.source, ref.targetType, Long.parseLong(ref.targetId));
                            if (ai != null && !ai.isBlank()) {
                                allContent.append(ai).append("\n\n");
                            }
                        }

                        task.stepDone("✓ " + (title != null ? title : ref.targetId));
                    }

                    // 生成文件名
                    String fileName;
                    if (req.contents.size() == 1 && firstTitle != null) {
                        fileName = firstTitle.replaceAll("[\\\\/:*?\"<>|]", "_");
                    } else {
                        fileName = "批量导出_" + req.contents.size() + "篇_" + System.currentTimeMillis();
                    }
                    Path outputPath = Path.of("output", "export", fileName + ".docx");

                    try {
                        wordExportService.exportContentToWord(allContent.toString(), outputPath, null);
                    } catch (Exception e) {
                        throw new RuntimeException("导出失败: " + e.getMessage(), e);
                    }

                    return "/api/export/download/" + outputPath.getFileName().toString();
                });
    }

    /**
     * 下载导出的文件
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable("filename") String filename) {
        Path file = Path.of("output", "export", filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(file));
    }

    // ===== 数据加载 =====

    private String loadTitle(String source, String targetType, Long targetId) {
        if ("zhihu".equals(source) && "answer".equals(targetType)) {
            ZhihuAnswerDOExample ex = new ZhihuAnswerDOExample();
            ex.createCriteria().andAnswerIdEqualTo(targetId);
            List<ZhihuAnswerDO> list = answerMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getQuestionTitle();
        } else if ("zhihu".equals(source) && "article".equals(targetType)) {
            ZhihuArticleDOExample ex = new ZhihuArticleDOExample();
            ex.createCriteria().andArticleIdEqualTo(targetId);
            List<ZhihuArticleDO> list = articleMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getTitle();
        } else if ("zhihu".equals(source) && "pin".equals(targetType)) {
            ZhihuPinDOExample ex = new ZhihuPinDOExample();
            ex.createCriteria().andPinIdEqualTo(targetId);
            List<ZhihuPinDO> list = pinMapper.selectByExample(ex);
            if (list.isEmpty()) return null;
            String c = list.get(0).getContent();
            return c != null && c.length() > 50 ? c.substring(0, 50) + "..." : c;
        } else if ("guba".equals(source) && "post".equals(targetType)) {
            GubaPostDOExample ex = new GubaPostDOExample();
            ex.createCriteria().andPostIdEqualTo(targetId);
            List<GubaPostDO> list = gubaPostMapper.selectByExample(ex);
            return list.isEmpty() ? null : list.get(0).getTitle();
        }
        return null;
    }

    private String loadContent(String source, String targetType, Long targetId) {
        if ("zhihu".equals(source) && "answer".equals(targetType)) {
            ZhihuAnswerDOExample ex = new ZhihuAnswerDOExample();
            ex.createCriteria().andAnswerIdEqualTo(targetId);
            List<ZhihuAnswerDO> list = answerMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("zhihu".equals(source) && "article".equals(targetType)) {
            ZhihuArticleDOExample ex = new ZhihuArticleDOExample();
            ex.createCriteria().andArticleIdEqualTo(targetId);
            List<ZhihuArticleDO> list = articleMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("zhihu".equals(source) && "pin".equals(targetType)) {
            ZhihuPinDOExample ex = new ZhihuPinDOExample();
            ex.createCriteria().andPinIdEqualTo(targetId);
            List<ZhihuPinDO> list = pinMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        } else if ("guba".equals(source) && "post".equals(targetType)) {
            GubaPostDOExample ex = new GubaPostDOExample();
            ex.createCriteria().andPostIdEqualTo(targetId);
            List<GubaPostDO> list = gubaPostMapper.selectByExampleWithBLOBs(ex);
            return list.isEmpty() ? null : list.get(0).getContent();
        }
        return null;
    }

    private String loadComments(String source, String targetType, Long targetId) {
        if (!"zhihu".equals(source)) return null;
        byte commentTargetType;
        if ("answer".equals(targetType)) commentTargetType = 1;
        else if ("article".equals(targetType)) commentTargetType = 2;
        else if ("pin".equals(targetType)) commentTargetType = 3;
        else return null;

        ZhihuCommentDOExample ex = new ZhihuCommentDOExample();
        ex.createCriteria().andTargetIdEqualTo(targetId).andTargetTypeEqualTo(commentTargetType);
        ex.setOrderByClause("created_time ASC");
        List<ZhihuCommentDO> comments = commentMapper.selectByExampleWithBLOBs(ex);
        if (comments.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (ZhihuCommentDO c : comments) {
            String author = c.getAuthorName() != null ? c.getAuthorName() : "匿名";
            String content = c.getContent() != null ? c.getContent().replaceAll("<[^>]+>", "") : "";
            sb.append("- **").append(author).append("**: ").append(content).append("\n");
        }
        return sb.toString();
    }

    private String loadAiAnalysis(String source, String targetType, Long targetId) {
        AiAnalysisDOExample ex = new AiAnalysisDOExample();
        ex.createCriteria().andSourceEqualTo(source).andTargetIdEqualTo(targetId)
                .andTargetTypeEqualTo(targetType).andStatusEqualTo("COMPLETED");
        List<AiAnalysisDO> analyses = aiAnalysisMapper.selectByExampleWithBLOBs(ex);
        if (analyses.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (AiAnalysisDO a : analyses) {
            String label = "investment_clue".equals(a.getAnalysisType()) ? "AI 分析" : "AI 评论分析";
            sb.append("## ").append(label).append("\n\n").append(a.getResult()).append("\n\n");
        }
        return sb.toString();
    }

    public static class ContentRef {
        public String source;
        public String targetId;
        public String targetType;
    }

    public static class BatchExportRequest {
        public List<ContentRef> contents;
        public Boolean includeBody;
        public Boolean includeComments;
        public Boolean includeAi;
    }
}
