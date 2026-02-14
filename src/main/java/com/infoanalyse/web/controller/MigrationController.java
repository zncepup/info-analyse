package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.*;
import com.infoanalyse.dao.model.*;
import com.infoanalyse.web.task.TaskInfo;
import com.infoanalyse.web.task.TaskService;
import com.infoanalyse.zhihu.ZhihuCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 数据迁移控制器 - 将 output/ 目录下的 Markdown 文件迁移到数据库
 */
@RestController
@RequestMapping("/api/migrate")
public class MigrationController {

    private static final Logger logger = LoggerFactory.getLogger(MigrationController.class);
    private static final Path OUTPUT_DIR = Path.of("output");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String AI_ANALYSIS_SEPARATOR = "## AI 投资线索分析";

    private final TaskService taskService;
    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final ZhihuCommentDOMapper commentMapper;
    private final GubaPostDOMapper gubaPostMapper;
    private final GubaCommentDOMapper gubaCommentMapper;
    private final AiAnalysisDOMapper aiAnalysisMapper;
    private final ZhihuCommand zhihuCommand;

    public MigrationController(TaskService taskService,
                               ZhihuAnswerDOMapper answerMapper,
                               ZhihuArticleDOMapper articleMapper,
                               ZhihuCommentDOMapper commentMapper,
                               GubaPostDOMapper gubaPostMapper,
                               GubaCommentDOMapper gubaCommentMapper,
                               AiAnalysisDOMapper aiAnalysisMapper,
                               ZhihuCommand zhihuCommand) {
        this.taskService = taskService;
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.gubaPostMapper = gubaPostMapper;
        this.gubaCommentMapper = gubaCommentMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
        this.zhihuCommand = zhihuCommand;
    }

    @PostMapping
    public TaskInfo migrate() {
        return taskService.submit("migrate", "迁移文件数据到数据库", Map.of(), () -> {
            int[] counts = {0, 0, 0, 0}; // answers, articles, gubaPosts, aiAnalysis
            if (!Files.exists(OUTPUT_DIR)) {
                return "output 目录不存在";
            }
            try (Stream<Path> dirs = Files.list(OUTPUT_DIR)) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    String dirName = dir.getFileName().toString();
                    if (dirName.startsWith("guba_")) {
                        migrateGubaDir(dir, counts);
                    } else {
                        migrateZhihuDir(dir, counts);
                    }
                });
            } catch (IOException e) {
                logger.error("迁移失败", e);
                throw new RuntimeException("迁移失败: " + e.getMessage());
            }
            return String.format("迁移完成: %d 条回答, %d 篇文章, %d 条股吧帖子, %d 条AI分析",
                    counts[0], counts[1], counts[2], counts[3]);
        });
    }

    @PostMapping("/classify-comments")
    public TaskInfo classifyComments() {
        return taskService.submit("classify-comments", "批量分类历史评论（投资相关性）", Map.of(),
                (task) -> zhihuCommand.classifyAllUnclassifiedComments(task));
    }

    // ========== 知乎目录迁移 ==========

    private void migrateZhihuDir(Path dir, int[] counts) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .filter(p -> !p.getFileName().toString().equals("INDEX.md"))
                 .forEach(p -> {
                     try {
                         String fileName = p.getFileName().toString();
                         if (fileName.startsWith("article_")) {
                             if (migrateZhihuArticle(p, counts)) counts[1]++;
                         } else {
                             if (migrateZhihuAnswer(p, counts)) counts[0]++;
                         }
                     } catch (Exception e) {
                         logger.warn("迁移文件失败: {} - {}", p, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            logger.warn("读取目录失败: {}", dir);
        }
    }

    private boolean migrateZhihuAnswer(Path file, int[] counts) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();

        // 解析 answerId: 文件名格式 {answerId}_{title}.md
        int underscoreIdx = fileName.indexOf('_');
        if (underscoreIdx <= 0) return false;
        String answerIdStr = fileName.substring(0, underscoreIdx);
        Long answerId = parseLong(answerIdStr);
        if (answerId == null) return false;

        // 检查是否已存在
        ZhihuAnswerDOExample example = new ZhihuAnswerDOExample();
        example.createCriteria().andAnswerIdEqualTo(answerId);
        if (answerMapper.countByExample(example) > 0) {
            logger.debug("回答已存在，跳过: {}", answerId);
            // 但仍然检查AI分析
            extractAndSaveAiAnalysis(content, "zhihu", answerId, "answer", counts);
            return false;
        }

        // 分离AI分析和原始内容
        String[] parts = splitAiAnalysis(content);
        String mainContent = parts[0];
        String aiResult = parts[1];

        // 解析元数据
        Map<String, String> meta = parseMetadata(mainContent);
        String bodyContent = extractBody(mainContent);

        // 解析 URL 获取 questionId
        String url = meta.get("原文链接");
        Long questionId = null;
        if (url != null) {
            Matcher m = Pattern.compile("/question/(\\d+)/answer/").matcher(url);
            if (m.find()) questionId = parseLong(m.group(1));
        }

        ZhihuAnswerDO record = new ZhihuAnswerDO();
        record.setAnswerId(answerId);
        record.setQuestionId(questionId);
        record.setQuestionTitle(parseTitle(mainContent));
        record.setAuthorName(meta.get("作者"));
        record.setVoteupCount(parseInt(meta.get("点赞")));
        record.setCommentCount(parseInt(meta.get("评论")));
        record.setUrl(url);
        record.setCreatedTime(parseDateTime(meta.get("创建时间")));
        record.setUpdatedTime(parseDateTime(meta.get("更新时间")));
        record.setContent(bodyContent);
        record.setCrawlTime(LocalDateTime.now());

        answerMapper.insertSelective(record);
        logger.info("迁移知乎回答: answerId={}", answerId);

        // 迁移评论
        migrateZhihuComments(mainContent, answerId, (byte) 1);

        // 迁移AI分析
        if (aiResult != null) {
            saveAiAnalysis("zhihu", answerId, "answer", aiResult, counts);
        }
        return true;
    }

    private boolean migrateZhihuArticle(Path file, int[] counts) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();

        // 解析 articleId: 文件名格式 article_{articleId}_{title}.md
        Matcher m = Pattern.compile("^article_(\\d+)_").matcher(fileName);
        if (!m.find()) return false;
        Long articleId = parseLong(m.group(1));
        if (articleId == null) return false;

        ZhihuArticleDOExample example = new ZhihuArticleDOExample();
        example.createCriteria().andArticleIdEqualTo(articleId);
        if (articleMapper.countByExample(example) > 0) {
            extractAndSaveAiAnalysis(content, "zhihu", articleId, "article", counts);
            return false;
        }

        String[] parts = splitAiAnalysis(content);
        String mainContent = parts[0];
        String aiResult = parts[1];

        Map<String, String> meta = parseMetadata(mainContent);
        String bodyContent = extractBody(mainContent);

        ZhihuArticleDO record = new ZhihuArticleDO();
        record.setArticleId(articleId);
        record.setTitle(parseTitle(mainContent));
        record.setAuthorName(meta.get("作者"));
        record.setVoteupCount(parseInt(meta.get("点赞")));
        record.setCommentCount(parseInt(meta.get("评论")));
        record.setUrl(meta.get("原文链接"));
        record.setCreatedTime(parseDateTime(meta.get("创建时间")));
        record.setUpdatedTime(parseDateTime(meta.get("更新时间")));
        record.setContent(bodyContent);
        record.setCrawlTime(LocalDateTime.now());

        articleMapper.insertSelective(record);
        logger.info("迁移知乎文章: articleId={}", articleId);

        migrateZhihuComments(mainContent, articleId, (byte) 2);

        if (aiResult != null) {
            saveAiAnalysis("zhihu", articleId, "article", aiResult, counts);
        }
        return true;
    }

    private void migrateZhihuComments(String content, Long targetId, byte targetType) {
        // 查找评论区: "## 作者互动评论" 之后的内容
        int commentStart = content.indexOf("## 作者互动评论");
        if (commentStart < 0) return;

        String commentSection = content.substring(commentStart);
        // 匹配评论块: 💬 **作者名** 或 └─ **作者名**
        Pattern rootPattern = Pattern.compile("💬 \\*\\*(.+?)\\*\\*(.*)\\n\\n> (.+?)\\n\\n\\*(.+?)\\*(.*)\\n");
        Matcher rm = rootPattern.matcher(commentSection);
        while (rm.find()) {
            String authorName = rm.group(1).replace(" 🔖", "");
            String commentContent = rm.group(3).replace("\n> ", "\n");
            String timeStr = rm.group(4).trim();
            String likeStr = rm.group(5).trim();
            int likes = 0;
            if (likeStr.contains("👍")) {
                try { likes = Integer.parseInt(likeStr.replaceAll(".*👍(\\d+).*", "$1")); } catch (Exception ignored) {}
            }
            try {
                ZhihuCommentDO c = new ZhihuCommentDO();
                c.setCommentId(0L); // 文件中没有评论ID，用0占位
                c.setTargetId(targetId);
                c.setTargetType(targetType);
                c.setAuthorName(authorName);
                c.setContent(commentContent);
                c.setLikeCount(likes);
                c.setCreatedTime(parseDateTime(timeStr));
                c.setCrawlTime(LocalDateTime.now());
                // 文件中没有唯一评论ID，跳过去重直接插入
                // 由于 comment_id 有 UNIQUE KEY，用 hash 生成伪ID
                c.setCommentId(generatePseudoCommentId(targetId, authorName, commentContent));
                ZhihuCommentDOExample ex = new ZhihuCommentDOExample();
                ex.createCriteria().andCommentIdEqualTo(c.getCommentId());
                if (commentMapper.countByExample(ex) == 0) {
                    commentMapper.insertSelective(c);
                }
            } catch (Exception e) {
                logger.debug("迁移评论失败: {}", e.getMessage());
            }
        }
    }

    // ========== 股吧目录迁移 ==========

    private void migrateGubaDir(Path dir, int[] counts) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".md"))
                 .filter(p -> !p.getFileName().toString().equals("INDEX.md"))
                 .forEach(p -> {
                     try {
                         if (migrateGubaPost(p, dir.getFileName().toString())) counts[2]++;
                     } catch (Exception e) {
                         logger.warn("迁移股吧帖子失败: {} - {}", p, e.getMessage());
                     }
                 });
        } catch (IOException e) {
            logger.warn("读取目录失败: {}", dir);
        }
    }

    private boolean migrateGubaPost(Path file, String dirName) throws IOException {
        String content = Files.readString(file);
        String fileName = file.getFileName().toString();

        // 解析 postId: 文件名格式 {postId}_{title}.md
        int underscoreIdx = fileName.indexOf('_');
        if (underscoreIdx <= 0) return false;
        Long postId = parseLong(fileName.substring(0, underscoreIdx));
        if (postId == null) return false;

        GubaPostDOExample example = new GubaPostDOExample();
        example.createCriteria().andPostIdEqualTo(postId);
        if (gubaPostMapper.countByExample(example) > 0) {
            logger.debug("股吧帖子已存在，跳过: {}", postId);
            return false;
        }

        // 解析 stockCode: 目录名格式 guba_{stockCode} 或 guba_{stockCode}_{stockName}
        String stockCode = "";
        String stockName = null;
        Matcher dm = Pattern.compile("^guba_(\\d+)(?:_(.+))?$").matcher(dirName);
        if (dm.matches()) {
            stockCode = dm.group(1);
            stockName = dm.group(2);
        }

        Map<String, String> meta = parseMetadata(content);
        String bodyContent = extractBody(content);

        // 从元数据中提取股票信息
        String stockMeta = meta.get("股票");
        if (stockMeta != null) {
            Matcher sm = Pattern.compile("(.+?)\\((\\d+)\\)").matcher(stockMeta);
            if (sm.find()) {
                stockName = sm.group(1).trim();
                stockCode = sm.group(2);
            }
        }

        GubaPostDO record = new GubaPostDO();
        record.setPostId(postId);
        record.setStockCode(stockCode);
        record.setStockName(stockName);
        record.setTitle(parseTitle(content));
        record.setContent(bodyContent);
        record.setAuthorName(meta.get("作者"));
        record.setReadCount(parseInt(meta.get("阅读")));
        record.setCommentCount(parseInt(meta.get("评论")));
        record.setLikeCount(parseInt(meta.get("点赞")));
        record.setUrl(meta.get("原文链接"));
        record.setPublishTime(parseDateTime(meta.get("发布时间")));
        record.setCrawlTime(LocalDateTime.now());

        gubaPostMapper.insertSelective(record);
        logger.info("迁移股吧帖子: postId={}", postId);

        // 迁移评论
        migrateGubaComments(content, postId);
        return true;
    }

    private void migrateGubaComments(String content, Long postId) {
        // 查找评论区
        int commentStart = content.indexOf("## 评论 (");
        if (commentStart < 0) return;

        String commentSection = content.substring(commentStart);
        // 匹配: 💬 **作者** 或 💬 **作者** → 回复对象
        Pattern pattern = Pattern.compile("💬 \\*\\*(.+?)\\*\\*(?:\\s*→\\s*(.+?))?\\n\\n> (.+?)\\n\\n\\*(.+?)\\*(.*)\\n");
        Matcher m = pattern.matcher(commentSection);
        while (m.find()) {
            String authorName = m.group(1);
            String replyTo = m.group(2);
            String commentContent = m.group(3).replace("\n> ", "\n");
            String timeStr = m.group(4).trim();
            String likeStr = m.group(5) != null ? m.group(5).trim() : "";
            int likes = 0;
            if (likeStr.contains("👍")) {
                try { likes = Integer.parseInt(likeStr.replaceAll(".*👍(\\d+).*", "$1")); } catch (Exception ignored) {}
            }
            try {
                GubaCommentDO c = new GubaCommentDO();
                c.setCommentId(generatePseudoCommentId(postId, authorName, commentContent));
                c.setPostId(postId);
                c.setAuthorName(authorName);
                c.setContent(commentContent);
                c.setLikeCount(likes);
                c.setReplyToUser(replyTo);
                c.setPublishTime(parseDateTime(timeStr));
                c.setCrawlTime(LocalDateTime.now());

                GubaCommentDOExample ex = new GubaCommentDOExample();
                ex.createCriteria().andCommentIdEqualTo(c.getCommentId());
                if (gubaCommentMapper.countByExample(ex) == 0) {
                    gubaCommentMapper.insertSelective(c);
                }
            } catch (Exception e) {
                logger.debug("迁移股吧评论失败: {}", e.getMessage());
            }
        }
    }

    // ========== AI分析迁移 ==========

    private void extractAndSaveAiAnalysis(String content, String source, Long targetId, String targetType, int[] counts) {
        String[] parts = splitAiAnalysis(content);
        if (parts[1] != null) {
            saveAiAnalysis(source, targetId, targetType, parts[1], counts);
        }
    }

    private void saveAiAnalysis(String source, Long targetId, String targetType, String aiResult, int[] counts) {
        AiAnalysisDOExample example = new AiAnalysisDOExample();
        example.createCriteria()
                .andSourceEqualTo(source)
                .andTargetIdEqualTo(targetId)
                .andTargetTypeEqualTo(targetType)
                .andAiModelEqualTo("deepseek-reasoner")
                .andAnalysisTypeEqualTo("investment_clue");
        if (aiAnalysisMapper.countByExample(example) > 0) return;

        AiAnalysisDO record = new AiAnalysisDO();
        record.setSource(source);
        record.setTargetId(targetId);
        record.setTargetType(targetType);
        record.setAiModel("deepseek-reasoner");
        record.setAnalysisType("investment_clue");
        record.setResult(aiResult);
        record.setStatus("COMPLETED");
        record.setCreatedTime(LocalDateTime.now());

        aiAnalysisMapper.insertSelective(record);
        counts[3]++;
        logger.info("迁移AI分析: source={}, targetId={}, type={}", source, targetId, targetType);
    }

    // ========== 工具方法 ==========

    private String[] splitAiAnalysis(String content) {
        int idx = content.indexOf(AI_ANALYSIS_SEPARATOR);
        if (idx < 0) return new String[]{content, null};
        String main = content.substring(0, idx).trim();
        String ai = content.substring(idx + AI_ANALYSIS_SEPARATOR.length()).trim();
        // 去掉 "> 由 DeepSeek 自动生成" 前缀
        ai = ai.replaceFirst("^>\\s*由\\s*DeepSeek\\s*自动生成\\s*", "").trim();
        return new String[]{main, ai};
    }

    private String parseTitle(String content) {
        for (String line : content.split("\n")) {
            if (line.startsWith("# ")) {
                return line.substring(2).trim();
            }
        }
        return null;
    }

    private Map<String, String> parseMetadata(String content) {
        Map<String, String> meta = new LinkedHashMap<>();
        Pattern p = Pattern.compile("- \\*\\*(.+?)\\*\\*:\\s*(.+)");
        for (String line : content.split("\n")) {
            Matcher m = p.matcher(line.trim());
            if (m.matches()) {
                String key = m.group(1).trim();
                String value = m.group(2).trim();
                // 处理链接格式: [url](url)
                Matcher linkMatcher = Pattern.compile("\\[(.+?)]\\((.+?)\\)").matcher(value);
                if (linkMatcher.matches()) {
                    value = linkMatcher.group(2);
                }
                meta.put(key, value);
            }
        }
        return meta;
    }

    private String extractBody(String content) {
        // 正文在第二个 "---" 之后，评论区 "## 评论" 或 "## 作者互动评论" 之前
        int firstSep = content.indexOf("---");
        if (firstSep < 0) return content;
        int secondSep = content.indexOf("---", firstSep + 3);
        if (secondSep < 0) return content;
        int bodyStart = secondSep + 3;

        // 找评论区或AI分析区的开始
        int bodyEnd = content.length();
        int commentIdx = content.indexOf("\n## 评论 (");
        int authorCommentIdx = content.indexOf("\n## 作者互动评论");
        int aiIdx = content.indexOf("\n---\n\n" + AI_ANALYSIS_SEPARATOR);

        if (commentIdx > bodyStart && commentIdx < bodyEnd) bodyEnd = commentIdx;
        if (authorCommentIdx > bodyStart && authorCommentIdx < bodyEnd) bodyEnd = authorCommentIdx;
        if (aiIdx > bodyStart && aiIdx < bodyEnd) bodyEnd = aiIdx;

        return content.substring(bodyStart, bodyEnd).trim();
    }

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDateTime.parse(str.trim(), DT_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(String str) {
        if (str == null || str.isBlank()) return 0;
        try {
            return Integer.parseInt(str.trim().replaceAll("[^\\d]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private Long parseLong(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private long generatePseudoCommentId(Long targetId, String author, String content) {
        // 用 targetId + author + content前20字 生成一个伪ID
        String key = targetId + "|" + (author != null ? author : "") + "|"
                + (content != null && content.length() > 20 ? content.substring(0, 20) : content);
        return Math.abs(key.hashCode()) + 100000000L; // 加偏移避免和真实ID冲突
    }
}
