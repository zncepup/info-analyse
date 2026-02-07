package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.*;
import com.infoanalyse.dao.model.*;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 内容查看控制器 - 从数据库读取内容并渲染为 HTML
 */
@RestController
public class MarkdownViewController {
    private final Parser parser;
    private final HtmlRenderer renderer;
    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final GubaPostDOMapper gubaPostMapper;
    private final ZhihuCommentDOMapper commentMapper;
    private final GubaCommentDOMapper gubaCommentMapper;
    private final AiAnalysisDOMapper aiAnalysisMapper;

    public MarkdownViewController(ZhihuAnswerDOMapper answerMapper,
                                  ZhihuArticleDOMapper articleMapper,
                                  GubaPostDOMapper gubaPostMapper,
                                  ZhihuCommentDOMapper commentMapper,
                                  GubaCommentDOMapper gubaCommentMapper,
                                  AiAnalysisDOMapper aiAnalysisMapper) {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().escapeHtml(false).build();
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.gubaPostMapper = gubaPostMapper;
        this.commentMapper = commentMapper;
        this.gubaCommentMapper = gubaCommentMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
    }

    /**
     * 查看知乎回答
     */
    @GetMapping(value = "/view/zhihu/answer/{answerId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewAnswer(@PathVariable("answerId") Long answerId) {
        ZhihuAnswerDOExample example = new ZhihuAnswerDOExample();
        example.createCriteria().andAnswerIdEqualTo(answerId);
        List<ZhihuAnswerDO> list = answerMapper.selectByExampleWithBLOBs(example);
        if (list.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "回答不存在");

        ZhihuAnswerDO answer = list.get(0);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(safe(answer.getQuestionTitle())).append("\n\n");
        md.append("---\n");
        md.append("- **作者**: ").append(safe(answer.getAuthorName())).append("\n");
        md.append("- **点赞**: ").append(answer.getVoteupCount()).append("\n");
        md.append("- **评论**: ").append(answer.getCommentCount()).append("\n");
        if (answer.getUrl() != null) md.append("- **原文链接**: [链接](").append(answer.getUrl()).append(")\n");
        if (answer.getCreatedTime() != null) md.append("- **创建时间**: ").append(answer.getCreatedTime()).append("\n");
        md.append("---\n\n");
        md.append(safe(answer.getContent())).append("\n\n");

        // 评论
        appendZhihuComments(md, answerId, (byte) 1);

        // AI 分析
        appendAiAnalysis(md, "zhihu", answerId, "answer");

        String html = renderMarkdown(md.toString());
        html = adjustImagePaths(html, safe(answer.getAuthorName()));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(wrapHtml(safe(answer.getQuestionTitle()), html));
    }

    /**
     * 查看知乎文章
     */
    @GetMapping(value = "/view/zhihu/article/{articleId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewArticle(@PathVariable("articleId") Long articleId) {
        ZhihuArticleDOExample example = new ZhihuArticleDOExample();
        example.createCriteria().andArticleIdEqualTo(articleId);
        List<ZhihuArticleDO> list = articleMapper.selectByExampleWithBLOBs(example);
        if (list.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "文章不存在");

        ZhihuArticleDO article = list.get(0);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(safe(article.getTitle())).append("\n\n");
        md.append("---\n");
        md.append("- **作者**: ").append(safe(article.getAuthorName())).append("\n");
        md.append("- **点赞**: ").append(article.getVoteupCount()).append("\n");
        md.append("- **评论**: ").append(article.getCommentCount()).append("\n");
        if (article.getUrl() != null) md.append("- **原文链接**: [链接](").append(article.getUrl()).append(")\n");
        if (article.getCreatedTime() != null) md.append("- **创建时间**: ").append(article.getCreatedTime()).append("\n");
        md.append("---\n\n");
        md.append(safe(article.getContent())).append("\n\n");

        appendZhihuComments(md, articleId, (byte) 2);
        appendAiAnalysis(md, "zhihu", articleId, "article");

        String html = renderMarkdown(md.toString());
        html = adjustImagePaths(html, safe(article.getAuthorName()));
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(wrapHtml(safe(article.getTitle()), html));
    }

    /**
     * 查看股吧帖子
     */
    @GetMapping(value = "/view/guba/post/{postId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewGubaPost(@PathVariable("postId") Long postId) {
        GubaPostDOExample example = new GubaPostDOExample();
        example.createCriteria().andPostIdEqualTo(postId);
        List<GubaPostDO> list = gubaPostMapper.selectByExampleWithBLOBs(example);
        if (list.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "帖子不存在");

        GubaPostDO post = list.get(0);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(safe(post.getTitle())).append("\n\n");
        md.append("---\n");
        md.append("- **作者**: ").append(safe(post.getAuthorName())).append("\n");
        if (post.getStockName() != null) md.append("- **股票**: ").append(post.getStockName()).append("(").append(post.getStockCode()).append(")\n");
        md.append("- **阅读**: ").append(post.getReadCount()).append("\n");
        md.append("- **评论**: ").append(post.getCommentCount()).append("\n");
        md.append("- **点赞**: ").append(post.getLikeCount()).append("\n");
        if (post.getUrl() != null) md.append("- **原文链接**: [链接](").append(post.getUrl()).append(")\n");
        if (post.getPublishTime() != null) md.append("- **发布时间**: ").append(post.getPublishTime()).append("\n");
        md.append("---\n\n");
        md.append(safe(post.getContent())).append("\n\n");

        // 股吧评论
        appendGubaComments(md, postId);
        appendAiAnalysis(md, "guba", postId, "post");

        String html = renderMarkdown(md.toString());
        // 股吧图片目录: output/guba_{stockCode}_{stockName}/images/
        String gubaDir = "guba_" + safe(post.getStockCode());
        if (post.getStockName() != null) gubaDir += "_" + post.getStockName();
        html = adjustImagePaths(html, gubaDir);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(wrapHtml(safe(post.getTitle()), html));
    }

    // ========== 旧路由兼容: /view/{author}/{file} ==========

    @GetMapping(value = "/view/{author}/{file:.+}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewLegacy(@PathVariable("author") String author, @PathVariable("file") String file) {
        // 尝试从文件名解析出 ID，重定向到新路由
        if (file.startsWith("article_")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("^article_(\\d+)_").matcher(file);
            if (m.find()) return viewArticle(Long.parseLong(m.group(1)));
        } else {
            int idx = file.indexOf('_');
            if (idx > 0) {
                try {
                    Long answerId = Long.parseLong(file.substring(0, idx));
                    return viewAnswer(answerId);
                } catch (NumberFormatException ignored) {}
            }
        }
        throw new ResponseStatusException(NOT_FOUND, "内容不存在");
    }

    // ========== 辅助方法 ==========

    private void appendZhihuComments(StringBuilder md, Long targetId, byte targetType) {
        ZhihuCommentDOExample cExample = new ZhihuCommentDOExample();
        cExample.createCriteria().andTargetIdEqualTo(targetId).andTargetTypeEqualTo(targetType);
        cExample.setOrderByClause("created_time ASC");
        List<ZhihuCommentDO> comments = commentMapper.selectByExampleWithBLOBs(cExample);
        if (!comments.isEmpty()) {
            md.append("## 作者互动评论\n\n");
            for (ZhihuCommentDO c : comments) {
                md.append("💬 **").append(safe(c.getAuthorName())).append("**\n\n");
                md.append("> ").append(safe(c.getContent())).append("\n\n");
                if (c.getCreatedTime() != null) md.append("*").append(c.getCreatedTime()).append("*");
                if (c.getLikeCount() != null && c.getLikeCount() > 0) md.append(" 👍").append(c.getLikeCount());
                md.append("\n\n---\n\n");
            }
        }
    }

    private void appendGubaComments(StringBuilder md, Long postId) {
        GubaCommentDOExample cExample = new GubaCommentDOExample();
        cExample.createCriteria().andPostIdEqualTo(postId);
        cExample.setOrderByClause("publish_time ASC");
        List<GubaCommentDO> comments = gubaCommentMapper.selectByExampleWithBLOBs(cExample);
        if (!comments.isEmpty()) {
            md.append("## 评论 (").append(comments.size()).append(")\n\n");
            for (GubaCommentDO c : comments) {
                md.append("💬 **").append(safe(c.getAuthorName())).append("**");
                if (c.getReplyToUser() != null) md.append(" → ").append(c.getReplyToUser());
                md.append("\n\n");
                md.append("> ").append(safe(c.getContent())).append("\n\n");
                if (c.getPublishTime() != null) md.append("*").append(c.getPublishTime()).append("*");
                if (c.getLikeCount() != null && c.getLikeCount() > 0) md.append(" 👍").append(c.getLikeCount());
                md.append("\n\n---\n\n");
            }
        }
    }

    private void appendAiAnalysis(StringBuilder md, String source, Long targetId, String targetType) {
        AiAnalysisDOExample aExample = new AiAnalysisDOExample();
        aExample.createCriteria()
                .andSourceEqualTo(source)
                .andTargetIdEqualTo(targetId)
                .andTargetTypeEqualTo(targetType)
                .andStatusEqualTo("COMPLETED");
        List<AiAnalysisDO> analyses = aiAnalysisMapper.selectByExampleWithBLOBs(aExample);
        for (AiAnalysisDO a : analyses) {
            md.append("## AI 分析 (").append(safe(a.getAiModel())).append(" - ").append(safe(a.getAnalysisType())).append(")\n\n");
            md.append(safe(a.getResult())).append("\n\n");
        }
    }

    private String renderMarkdown(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    /**
     * 将 HTML 中的相对图片路径调整为可访问的绝对路径
     * images/xxx.jpg -> /output/{author}/images/xxx.jpg
     */
    private String adjustImagePaths(String html, String authorDir) {
        if (authorDir == null || authorDir.isBlank()) return html;
        // 文件系统目录名: 空格和特殊字符替换为下划线
        String safeDirName = authorDir.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
        String encodedDir = java.net.URLEncoder.encode(safeDirName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        // 替换所有非绝对路径的 img src
        html = html.replaceAll("src=\"(?!http|/|data:)([^\"]+)\"", "src=\"/output/" + encodedDir + "/$1\"");
        return html;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String wrapHtml(String title, String content) {
        String header = """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>阅读 - __TITLE__</title>
                  <style>
                    :root {
                      color-scheme: light;
                      --bg: #f4f5f1;
                      --ink: #0c1f23;
                      --muted: #516068;
                      --card: #ffffff;
                      --accent: #0f766e;
                    }
                    body {
                      margin: 0;
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                      color: var(--ink);
                      background: var(--bg);
                    }
                    header {
                      position: sticky;
                      top: 0;
                      background: rgba(255, 255, 255, 0.9);
                      backdrop-filter: blur(8px);
                      border-bottom: 1px solid rgba(12, 31, 35, 0.08);
                      padding: 12px 16px;
                      display: flex;
                      align-items: center;
                      gap: 12px;
                      z-index: 10;
                    }
                    header a {
                      color: var(--accent);
                      font-weight: 600;
                      text-decoration: none;
                    }
                    main {
                      max-width: 860px;
                      margin: 0 auto;
                      padding: 20px 16px 48px;
                    }
                    article {
                      background: var(--card);
                      border-radius: 16px;
                      padding: 22px;
                      box-shadow: 0 14px 40px rgba(10, 27, 31, 0.08);
                    }
                    h1, h2, h3, h4 {
                      font-family: "Fraunces", "Noto Serif SC", serif;
                    }
                    img {
                      max-width: 100%;
                      height: auto;
                      border-radius: 10px;
                    }
                    pre {
                      overflow: auto;
                      background: #0c1f23;
                      color: #e4ecef;
                      padding: 12px 14px;
                      border-radius: 12px;
                    }
                    code {
                      background: rgba(12, 31, 35, 0.08);
                      padding: 0 6px;
                      border-radius: 6px;
                    }
                    blockquote {
                      border-left: 4px solid rgba(15, 118, 110, 0.4);
                      padding-left: 12px;
                      color: var(--muted);
                    }
                    a { color: var(--accent); }
                  </style>
                </head>
                <body>
                  <header>
                    <a href="/">返回首页</a>
                    <span>内容浏览</span>
                  </header>
                  <main>
                    <article>
                """;

        String footer = """
                    </article>
                  </main>
                </body>
                </html>
                """;

        return header.replace("__TITLE__", escape(title)) + content + footer;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
