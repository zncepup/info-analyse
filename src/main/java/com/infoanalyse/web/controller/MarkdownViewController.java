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
    private final ZhihuPinDOMapper pinMapper;

    public MarkdownViewController(ZhihuAnswerDOMapper answerMapper,
                                  ZhihuArticleDOMapper articleMapper,
                                  GubaPostDOMapper gubaPostMapper,
                                  ZhihuCommentDOMapper commentMapper,
                                  GubaCommentDOMapper gubaCommentMapper,
                                  AiAnalysisDOMapper aiAnalysisMapper,
                                  ZhihuPinDOMapper pinMapper) {
        this.parser = Parser.builder().build();
        this.renderer = HtmlRenderer.builder().escapeHtml(false).build();
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.gubaPostMapper = gubaPostMapper;
        this.commentMapper = commentMapper;
        this.gubaCommentMapper = gubaCommentMapper;
        this.aiAnalysisMapper = aiAnalysisMapper;
        this.pinMapper = pinMapper;
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

            String html = renderMarkdown(md.toString());
            html = adjustImagePaths(html, safe(answer.getAuthorName()));

            // Append interactive HTML sections
            StringBuilder extra = new StringBuilder();
            appendZhihuCommentsHtml(extra, answerId, (byte) 1);
            appendAiAnalysisHtml(extra, "zhihu", answerId, "answer");

            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(wrapHtml(safe(answer.getQuestionTitle()), html + extra.toString()));
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

            String html = renderMarkdown(md.toString());
            html = adjustImagePaths(html, safe(article.getAuthorName()));

            StringBuilder extra = new StringBuilder();
            appendZhihuCommentsHtml(extra, articleId, (byte) 2);
            appendAiAnalysisHtml(extra, "zhihu", articleId, "article");

            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(wrapHtml(safe(article.getTitle()), html + extra.toString()));
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

            String html = renderMarkdown(md.toString());
            String gubaDir = "guba_" + safe(post.getStockCode());
            if (post.getStockName() != null) gubaDir += "_" + post.getStockName();
            html = adjustImagePaths(html, gubaDir);

            StringBuilder extra = new StringBuilder();
            appendGubaCommentsHtml(extra, postId);
            appendAiAnalysisHtml(extra, "guba", postId, "post");

            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(wrapHtml(safe(post.getTitle()), html + extra.toString()));
        }

    /**
     * 查看知乎想法
     */
    @GetMapping(value = "/view/zhihu/pin/{pinId}", produces = MediaType.TEXT_HTML_VALUE)
        public ResponseEntity<String> viewPin(@PathVariable("pinId") Long pinId) {
            ZhihuPinDOExample example = new ZhihuPinDOExample();
            example.createCriteria().andPinIdEqualTo(pinId);
            List<ZhihuPinDO> list = pinMapper.selectByExampleWithBLOBs(example);
            if (list.isEmpty()) throw new ResponseStatusException(NOT_FOUND, "想法不存在");

            ZhihuPinDO pin = list.get(0);
            String title = pin.getContent() != null && pin.getContent().length() > 50
                    ? pin.getContent().substring(0, 50) + "..." : "想法";
            StringBuilder md = new StringBuilder();
            md.append("# 想法\n\n");
            md.append("---\n");
            md.append("- **作者**: ").append(safe(pin.getAuthorName())).append("\n");
            md.append("- **点赞**: ").append(pin.getLikeCount()).append("\n");
            md.append("- **评论**: ").append(pin.getCommentCount()).append("\n");
            md.append("- **转发**: ").append(pin.getRepinCount()).append("\n");
            if (pin.getUrl() != null) md.append("- **原文链接**: [链接](").append(pin.getUrl()).append(")\n");
            if (pin.getCreatedTime() != null) md.append("- **创建时间**: ").append(pin.getCreatedTime()).append("\n");
            md.append("---\n\n");
            md.append(safe(pin.getContent())).append("\n\n");

            String html = renderMarkdown(md.toString());

            StringBuilder extra = new StringBuilder();
            appendAiAnalysisHtml(extra, "zhihu", pinId, "pin");

            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(wrapHtml(title, html + extra.toString()));
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




    /**
         * 输出知乎评论为可折叠 HTML
         */
        private void appendZhihuCommentsHtml(StringBuilder html, Long targetId, byte targetType) {
            ZhihuCommentDOExample cExample = new ZhihuCommentDOExample();
            cExample.createCriteria().andTargetIdEqualTo(targetId).andTargetTypeEqualTo(targetType);
            cExample.setOrderByClause("created_time ASC");
            List<ZhihuCommentDO> comments = commentMapper.selectByExampleWithBLOBs(cExample);
            if (comments.isEmpty()) return;

            // Build parent-children structure
            java.util.Map<Long, ZhihuCommentDO> commentMap = new java.util.LinkedHashMap<>();
            for (ZhihuCommentDO c : comments) commentMap.put(c.getCommentId(), c);

            java.util.List<ZhihuCommentDO> roots = new java.util.ArrayList<>();
            java.util.Map<Long, java.util.List<ZhihuCommentDO>> childrenMap = new java.util.LinkedHashMap<>();
            for (ZhihuCommentDO c : comments) {
                if (c.getParentCommentId() == null) {
                    roots.add(c);
                } else {
                    childrenMap.computeIfAbsent(c.getParentCommentId(), k -> new java.util.ArrayList<>()).add(c);
                }
            }
            // Orphan children (parent not in result set)
            for (ZhihuCommentDO c : comments) {
                if (c.getParentCommentId() != null && !commentMap.containsKey(c.getParentCommentId())) {
                    roots.add(c);
                }
            }

            html.append("<div class=\"comment-section\">");
            html.append("<div class=\"comment-toggle\" onclick=\"this.parentElement.classList.toggle('open')\">");
            html.append("<span>作者互动评论 (").append(comments.size()).append(")</span>");
            html.append("<svg class=\"chevron\" width=\"12\" height=\"8\" viewBox=\"0 0 12 8\" fill=\"none\"><path d=\"M1 1.5L6 6.5L11 1.5\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>");
            html.append("</div>");
            html.append("<div class=\"comment-body\">");

            for (ZhihuCommentDO root : roots) {
                html.append("<div class=\"comment-thread\">");
                appendSingleCommentHtml(html, root, null);
                java.util.List<ZhihuCommentDO> children = childrenMap.get(root.getCommentId());
                if (children != null && !children.isEmpty()) {
                    html.append("<div class=\"comment-replies\">");
                    for (ZhihuCommentDO child : children) {
                        appendSingleCommentHtml(html, child, child.getReplyToAuthor());
                    }
                    html.append("</div>");
                }
                html.append("</div>");
            }

            html.append("</div></div>");
        }

        private void appendSingleCommentHtml(StringBuilder html, ZhihuCommentDO c, String replyTo) {
            html.append("<div class=\"comment-item\">");
            html.append("<div class=\"comment-author\">").append(escape(safe(c.getAuthorName())));
            if (replyTo != null && !replyTo.isEmpty()) {
                html.append("<span class=\"comment-reply-to\"> → ").append(escape(replyTo)).append("</span>");
            }
            html.append("</div>");
            html.append("<div class=\"comment-content\">").append(sanitizeCommentHtml(c.getContent())).append("</div>");
            html.append("<div class=\"comment-meta\">");
            if (c.getCreatedTime() != null) html.append("<span>").append(c.getCreatedTime()).append("</span>");
            if (c.getLikeCount() != null && c.getLikeCount() > 0) html.append("<span>👍 ").append(c.getLikeCount()).append("</span>");
            html.append("</div>");
            html.append("</div>");
        }

        /**
         * 输出股吧评论为可折叠 HTML
         */
        private void appendGubaCommentsHtml(StringBuilder html, Long postId) {
            GubaCommentDOExample cExample = new GubaCommentDOExample();
            cExample.createCriteria().andPostIdEqualTo(postId);
            cExample.setOrderByClause("publish_time ASC");
            List<GubaCommentDO> comments = gubaCommentMapper.selectByExampleWithBLOBs(cExample);
            if (comments.isEmpty()) return;

            html.append("<div class=\"comment-section\">");
            html.append("<div class=\"comment-toggle\" onclick=\"this.parentElement.classList.toggle('open')\">");
            html.append("<span>评论 (").append(comments.size()).append(")</span>");
            html.append("<svg class=\"chevron\" width=\"12\" height=\"8\" viewBox=\"0 0 12 8\" fill=\"none\"><path d=\"M1 1.5L6 6.5L11 1.5\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>");
            html.append("</div>");
            html.append("<div class=\"comment-body\">");

            for (GubaCommentDO c : comments) {
                html.append("<div class=\"comment-thread\"><div class=\"comment-item\">");
                html.append("<div class=\"comment-author\">").append(escape(safe(c.getAuthorName())));
                if (c.getReplyToUser() != null) {
                    html.append("<span class=\"comment-reply-to\"> → ").append(escape(c.getReplyToUser())).append("</span>");
                }
                html.append("</div>");
                html.append("<div class=\"comment-content\">").append(sanitizeCommentHtml(c.getContent())).append("</div>");
                html.append("<div class=\"comment-meta\">");
                if (c.getPublishTime() != null) html.append("<span>").append(c.getPublishTime()).append("</span>");
                if (c.getLikeCount() != null && c.getLikeCount() > 0) html.append("<span>👍 ").append(c.getLikeCount()).append("</span>");
                html.append("</div>");
                html.append("</div></div>");
            }

            html.append("</div></div>");
        }

        /**
         * 输出 AI 分析为可折叠 HTML
         */
        private void appendAiAnalysisHtml(StringBuilder html, String source, Long targetId, String targetType) {
            AiAnalysisDOExample aExample = new AiAnalysisDOExample();
            aExample.createCriteria()
                    .andSourceEqualTo(source)
                    .andTargetIdEqualTo(targetId)
                    .andTargetTypeEqualTo(targetType)
                    .andStatusEqualTo("COMPLETED");
            List<AiAnalysisDO> analyses = aiAnalysisMapper.selectByExampleWithBLOBs(aExample);
            if (analyses.isEmpty()) return;

            for (AiAnalysisDO a : analyses) {
                html.append("<div class=\"comment-section open\">");
                html.append("<div class=\"comment-toggle\" onclick=\"this.parentElement.classList.toggle('open')\">");
                html.append("<span>AI 分析 (").append(escape(safe(a.getAiModel()))).append(")</span>");
                html.append("<svg class=\"chevron\" width=\"12\" height=\"8\" viewBox=\"0 0 12 8\" fill=\"none\"><path d=\"M1 1.5L6 6.5L11 1.5\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>");
                html.append("</div>");
                html.append("<div class=\"comment-body\"><div class=\"ai-content\">");
                // Render AI result as markdown
                html.append(renderMarkdown(safe(a.getResult())));
                html.append("</div></div></div>");
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
                      <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
                      <meta name="apple-mobile-web-app-capable" content="yes">
                      <title>__TITLE__</title>
                      <style>
                        :root {
                          --system-bg: #F2F2F7;
                          --grouped-bg: #FFFFFF;
                          --label: #000000;
                          --label-secondary: #3C3C43;
                          --label-tertiary: #3C3C4399;
                          --separator: #3C3C4336;
                          --tint: #007AFF;
                          --fill-tertiary: #7676801F;
                          --safe-bottom: env(safe-area-inset-bottom, 0px);
                        }
                        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
                        html { -webkit-text-size-adjust: 100%; scroll-behavior: smooth; }
                        body {
                          font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", "SF Pro Display",
                                       "PingFang SC", "Helvetica Neue", "Microsoft YaHei", sans-serif;
                          font-size: 17px; line-height: 1.58;
                          color: var(--label);
                          background: var(--system-bg);
                          -webkit-font-smoothing: antialiased;
                          -webkit-tap-highlight-color: transparent;
                        }
                        .reader-nav {
                          position: sticky; top: 0; z-index: 20;
                          background: rgba(249,249,249,0.94);
                          -webkit-backdrop-filter: saturate(180%) blur(20px);
                          backdrop-filter: saturate(180%) blur(20px);
                          border-bottom: 0.5px solid var(--separator);
                        }
                        .reader-nav-inner {
                          max-width: 700px; margin: 0 auto;
                          display: flex; align-items: center;
                          height: 44px; padding: 0 16px; gap: 12px;
                        }
                        .back-btn {
                          display: inline-flex; align-items: center; gap: 4px;
                          color: var(--tint); text-decoration: none;
                          font-size: 17px; font-weight: 400; padding: 4px 0;
                        }
                        .back-btn:active { opacity: 0.5; }
                        .back-btn svg { flex-shrink: 0; }
                        .nav-title-text {
                          flex: 1; text-align: center;
                          font-size: 17px; font-weight: 600; color: var(--label);
                          overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
                          margin-right: 60px;
                        }
                        .reader-main {
                          max-width: 700px; margin: 0 auto;
                          padding: 16px 16px calc(32px + var(--safe-bottom));
                        }
                        .reader-card {
                          background: var(--grouped-bg);
                          border-radius: 12px; padding: 20px 18px; overflow: hidden;
                        }
                        .reader-card h1 { font-size: 22px; font-weight: 700; line-height: 1.3; margin-bottom: 16px; letter-spacing: -0.02em; }
                        .reader-card h2 { font-size: 19px; font-weight: 700; line-height: 1.35; margin-top: 28px; margin-bottom: 10px; padding-top: 20px; border-top: 0.5px solid var(--separator); }
                        .reader-card h2:first-child { border-top: none; padding-top: 0; margin-top: 0; }
                        .reader-card h3 { font-size: 17px; font-weight: 600; line-height: 1.4; margin-top: 22px; margin-bottom: 8px; }
                        .reader-card h4 { font-size: 15px; font-weight: 600; line-height: 1.4; margin-top: 18px; margin-bottom: 6px; color: var(--label-secondary); }
                        .reader-card p { margin-bottom: 14px; font-size: 17px; line-height: 1.65; }
                        .reader-card ul, .reader-card ol { margin-bottom: 14px; padding-left: 22px; }
                        .reader-card li { margin-bottom: 6px; font-size: 17px; line-height: 1.58; }
                        .reader-card img { max-width: 100%; height: auto; border-radius: 10px; margin: 12px 0; display: block; }
                        .reader-card a { color: var(--tint); text-decoration: none; }
                        .reader-card a:active { opacity: 0.5; }
                        .reader-card blockquote { border-left: 3px solid var(--tint); padding: 2px 0 2px 14px; margin: 14px 0; color: var(--label-secondary); font-size: 16px; }
                        .reader-card pre { overflow-x: auto; -webkit-overflow-scrolling: touch; background: #1C1C1E; color: #F5F5F7; padding: 14px 16px; border-radius: 10px; font-size: 14px; line-height: 1.5; margin: 14px 0; }
                        .reader-card code { background: var(--fill-tertiary); padding: 2px 6px; border-radius: 5px; font-size: 15px; }
                        .reader-card pre code { background: none; padding: 0; border-radius: 0; font-size: inherit; }
                        .reader-card hr { border: none; border-top: 0.5px solid var(--separator); margin: 24px 0; }
                        .reader-card table { width: 100%; border-collapse: collapse; margin: 14px 0; font-size: 15px; }
                        .reader-card th, .reader-card td { padding: 8px 10px; text-align: left; border-bottom: 0.5px solid var(--separator); }
                        .reader-card th { font-weight: 600; font-size: 13px; color: var(--label-tertiary); text-transform: uppercase; }

                        /* Collapsible comment/AI sections */
                        .comment-section {
                          margin-top: 20px; border-top: 0.5px solid var(--separator); padding-top: 0;
                        }
                        .comment-toggle {
                          display: flex; align-items: center; justify-content: space-between;
                          padding: 14px 0; cursor: pointer;
                          -webkit-tap-highlight-color: transparent;
                          user-select: none;
                        }
                        .comment-toggle span {
                          font-size: 17px; font-weight: 600; color: var(--label);
                        }
                        .comment-toggle .chevron {
                          color: var(--label-tertiary);
                          transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1);
                        }
                        .comment-section.open .comment-toggle .chevron {
                          transform: rotate(180deg);
                        }
                        .comment-body {
                          max-height: 0; overflow: hidden;
                          transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1);
                        }
                        .comment-section.open .comment-body {
                          max-height: 50000px;
                          transition: max-height 0.5s ease-in;
                        }

                        /* Comment thread */
                        .comment-thread {
                          padding: 12px 0;
                        }
                        .comment-thread + .comment-thread {
                          border-top: 0.5px solid var(--separator);
                        }
                        .comment-item { margin-bottom: 2px; }
                        .comment-author {
                          font-size: 15px; font-weight: 600; color: var(--label);
                          margin-bottom: 4px;
                        }
                        .comment-reply-to {
                          font-weight: 400; color: var(--label-tertiary); font-size: 14px;
                        }
                        .comment-content {
                          font-size: 15px; line-height: 1.55; color: var(--label);
                          margin-bottom: 4px; word-break: break-word;
                        }
                        .comment-content a {
                          color: var(--tint); text-decoration: none;
                        }
                        .comment-content a:active {
                          opacity: 0.6;
                        }
                        .comment-meta {
                          display: flex; gap: 12px;
                          font-size: 13px; color: var(--label-tertiary);
                          margin-bottom: 8px;
                        }

                        /* Replies (indented) */
                        .comment-replies {
                          margin-left: 20px;
                          padding-left: 12px;
                          border-left: 2px solid var(--fill-tertiary);
                        }
                        .comment-replies .comment-item {
                          padding: 8px 0;
                        }
                        .comment-replies .comment-item + .comment-item {
                          border-top: 0.5px solid var(--separator);
                        }

                        /* AI analysis content */
                        .ai-content { padding-bottom: 8px; }
                        .ai-content p { font-size: 15px; line-height: 1.6; margin-bottom: 10px; }
                        .ai-content h1, .ai-content h2, .ai-content h3 { margin-top: 16px; margin-bottom: 8px; }
                        .ai-content h2 { font-size: 17px; border-top: none; padding-top: 0; }
                        .ai-content h3 { font-size: 15px; }
                        .ai-content ul, .ai-content ol { margin-bottom: 10px; padding-left: 20px; }
                        .ai-content li { font-size: 15px; line-height: 1.55; margin-bottom: 4px; }

                        @media (max-width: 500px) {
                          .reader-card { padding: 16px 14px; border-radius: 10px; }
                          .reader-card h1 { font-size: 20px; }
                          .reader-card h2 { font-size: 18px; }
                          .reader-card p, .reader-card li { font-size: 16px; }
                          .comment-replies { margin-left: 12px; padding-left: 10px; }
                        }
                      </style>
                    </head>
                    <body>
                      <nav class="reader-nav">
                        <div class="reader-nav-inner">
                          <a class="back-btn" href="/">
                            <svg width="10" height="18" viewBox="0 0 10 18" fill="none"><path d="M9 1L1 9l8 8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            返回
                          </a>
                          <div class="nav-title-text">__TITLE__</div>
                        </div>
                      </nav>
                      <main class="reader-main">
                        <div class="reader-card">
                    """;

            String footer = """
                        </div>
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

    /**
     * 清理评论 HTML：保留 &lt;a&gt; 超链接，去除其余标签，转换换行
     * 返回可直接嵌入页面的安全 HTML
     */
    private String sanitizeCommentHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        // 1. 提取并暂存 <a> 标签，用占位符替代
        java.util.List<String> links = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)<a\\s+[^>]*href\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>")
                .matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String href = m.group(1);
            String text = m.group(2).replaceAll("<[^>]+>", ""); // 链接文字去内嵌标签
            String safeLink = "<a href=\"" + escape(href) + "\" target=\"_blank\" rel=\"noopener\">"
                    + escape(text) + "</a>";
            links.add(safeLink);
            m.appendReplacement(sb, "\u0000LINK" + (links.size() - 1) + "\u0000");
        }
        m.appendTail(sb);
        String result = sb.toString();
        // 2. <br> → 换行
        result = result.replaceAll("(?i)<br\\s*/?>", "\n");
        // 3. </p> → 换行
        result = result.replaceAll("(?i)</p>", "\n");
        // 4. 去除所有剩余标签
        result = result.replaceAll("<[^>]+>", "");
        // 5. 转换 HTML 实体
        result = result.replace("&nbsp;", " ")
                       .replace("&amp;", "&")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&quot;", "\"")
                       .replace("&#39;", "'");
        // 6. 对纯文本部分做 escape 防 XSS
        result = escape(result);
        // 7. 换行转 <br>
        result = result.replace("\n", "<br>");
        // 8. 还原 <a> 链接
        for (int i = 0; i < links.size(); i++) {
            result = result.replace("\u0000LINK" + i + "\u0000", links.get(i));
        }
        // 9. 清理多余连续 <br>
        result = result.replaceAll("(<br>){3,}", "<br><br>");
        return result.trim();
    }
}
