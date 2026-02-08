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

    // 知乎表情 [xxx] -> Unicode emoji 映射
    private static final java.util.Map<String, String> ZHIHU_EMOJI_MAP;
    static {
        ZHIHU_EMOJI_MAP = new java.util.LinkedHashMap<>();
        ZHIHU_EMOJI_MAP.put("\u5fae\u7b11", "\ud83d\ude0a"); ZHIHU_EMOJI_MAP.put("\u5927\u7b11", "\ud83d\ude04");
        ZHIHU_EMOJI_MAP.put("\u9f87\u7259", "\ud83d\ude01"); ZHIHU_EMOJI_MAP.put("\u7b11\u54ed", "\ud83d\ude02");
        ZHIHU_EMOJI_MAP.put("\u98d9\u6cea\u7b11", "\ud83e\udd23"); ZHIHU_EMOJI_MAP.put("\u5077\u7b11", "\ud83e\udd2d");
        ZHIHU_EMOJI_MAP.put("\u6342\u5634", "\ud83e\udd2d"); ZHIHU_EMOJI_MAP.put("\u659c\u773c\u7b11", "\ud83d\ude0f");
        ZHIHU_EMOJI_MAP.put("\u5bb3\u7f9e", "\ud83d\ude33"); ZHIHU_EMOJI_MAP.put("\u5410\u820c", "\ud83d\ude1b");
        ZHIHU_EMOJI_MAP.put("\u5f97\u610f", "\ud83d\ude0e"); ZHIHU_EMOJI_MAP.put("\u673a\u667a", "\ud83e\uddd0");
        ZHIHU_EMOJI_MAP.put("\u5978\u7b11", "\ud83d\ude08"); ZHIHU_EMOJI_MAP.put("\u6342\u8138", "\ud83e\udd26");
        ZHIHU_EMOJI_MAP.put("\u5927\u54ed", "\ud83d\ude2d"); ZHIHU_EMOJI_MAP.put("\u53ef\u601c", "\ud83e\udd7a");
        ZHIHU_EMOJI_MAP.put("\u59d4\u5c48", "\ud83d\ude22"); ZHIHU_EMOJI_MAP.put("\u60ca\u559c", "\ud83d\ude32");
        ZHIHU_EMOJI_MAP.put("\u60ca\u8bb6", "\ud83d\ude2e"); ZHIHU_EMOJI_MAP.put("\u60ca\u6050", "\ud83d\ude31");
        ZHIHU_EMOJI_MAP.put("\u7591\u95ee", "\ud83e\udd14"); ZHIHU_EMOJI_MAP.put("\u601d\u8003", "\ud83e\udd14");
        ZHIHU_EMOJI_MAP.put("\u8111\u7206", "\ud83e\udd2f"); ZHIHU_EMOJI_MAP.put("\u53d1\u5446", "\ud83d\ude36");
        ZHIHU_EMOJI_MAP.put("\u65e0\u8bed", "\ud83d\ude11"); ZHIHU_EMOJI_MAP.put("\u7ffb\u767d\u773c", "\ud83d\ude44");
        ZHIHU_EMOJI_MAP.put("\u6124\u6012", "\ud83d\ude21"); ZHIHU_EMOJI_MAP.put("\u751f\u6c14", "\ud83d\ude24");
        ZHIHU_EMOJI_MAP.put("\u6293\u72c2", "\ud83d\ude2b"); ZHIHU_EMOJI_MAP.put("\u96be\u8fc7", "\ud83d\ude1e");
        ZHIHU_EMOJI_MAP.put("\u5931\u671b", "\ud83d\ude14"); ZHIHU_EMOJI_MAP.put("\u5c34\u5c2c", "\ud83d\ude05");
        ZHIHU_EMOJI_MAP.put("\u5618", "\ud83e\udd2b"); ZHIHU_EMOJI_MAP.put("\u6655", "\ud83d\ude35");
        ZHIHU_EMOJI_MAP.put("\u56f0", "\ud83d\ude34"); ZHIHU_EMOJI_MAP.put("\u6253\u8138", "\ud83e\udd26");
        ZHIHU_EMOJI_MAP.put("\u8d5e", "\ud83d\udc4d"); ZHIHU_EMOJI_MAP.put("\u8d5e\u540c", "\ud83d\udc4d");
        ZHIHU_EMOJI_MAP.put("\u611f\u8c22", "\ud83d\ude4f"); ZHIHU_EMOJI_MAP.put("\u9f13\u638c", "\ud83d\udc4f");
        ZHIHU_EMOJI_MAP.put("\u63e1\u624b", "\ud83e\udd1d"); ZHIHU_EMOJI_MAP.put("\u62f3\u5934", "\u270a");
        ZHIHU_EMOJI_MAP.put("OK", "\ud83d\udc4c"); ZHIHU_EMOJI_MAP.put("\u52a0\u6cb9", "\ud83d\udcaa");
        ZHIHU_EMOJI_MAP.put("\u8e72", "\ud83e\uddce"); ZHIHU_EMOJI_MAP.put("\u62b1\u62f3", "\ud83e\udd1c");
        ZHIHU_EMOJI_MAP.put("\u62dc\u6258", "\ud83d\ude4f"); ZHIHU_EMOJI_MAP.put("\u7231", "\u2764\ufe0f");
        ZHIHU_EMOJI_MAP.put("\u5fc3\u788e", "\ud83d\udc94"); ZHIHU_EMOJI_MAP.put("\u6bd4\u5fc3", "\ud83e\udec6");
        ZHIHU_EMOJI_MAP.put("\u98de\u543b", "\ud83d\ude18"); ZHIHU_EMOJI_MAP.put("\u4eb2\u4eb2", "\ud83d\ude19");
        ZHIHU_EMOJI_MAP.put("doge", "\ud83d\udc36"); ZHIHU_EMOJI_MAP.put("\u72d7\u5934", "\ud83d\udc36");
        ZHIHU_EMOJI_MAP.put("\u5403\u74dc", "\ud83c\udf49"); ZHIHU_EMOJI_MAP.put("\u7399\u7470", "\ud83c\udf39");
        ZHIHU_EMOJI_MAP.put("\u51cb\u8c22", "\ud83e\udd40"); ZHIHU_EMOJI_MAP.put("\u592a\u9633", "\u2600\ufe0f");
        ZHIHU_EMOJI_MAP.put("\u6708\u4eae", "\ud83c\udf19"); ZHIHU_EMOJI_MAP.put("\u661f\u661f", "\u2b50");
        ZHIHU_EMOJI_MAP.put("\u5f69\u8679", "\ud83c\udf08"); ZHIHU_EMOJI_MAP.put("\u5496\u5561", "\u2615");
        ZHIHU_EMOJI_MAP.put("\u86cb\u7cd5", "\ud83c\udf82"); ZHIHU_EMOJI_MAP.put("\u793c\u7269", "\ud83c\udf81");
        ZHIHU_EMOJI_MAP.put("\u70df\u82b1", "\ud83c\udf86"); ZHIHU_EMOJI_MAP.put("\u5e86\u795d", "\ud83c\udf89");
        ZHIHU_EMOJI_MAP.put("\u7ea2\u5305", "\ud83e\udde7"); ZHIHU_EMOJI_MAP.put("\u53d1\u8d22", "\ud83d\udcb0");
        ZHIHU_EMOJI_MAP.put("\u798f", "\ud83e\udde7"); ZHIHU_EMOJI_MAP.put("\u65fa\u67f4", "\ud83d\udc15");
        ZHIHU_EMOJI_MAP.put("\u5c0f\u4e11", "\ud83e\udd21"); ZHIHU_EMOJI_MAP.put("\u9ab7\u9ac5", "\ud83d\udc80");
        ZHIHU_EMOJI_MAP.put("\u88c2\u5f00", "\ud83d\ude29"); ZHIHU_EMOJI_MAP.put("\u793e\u4f1a\u793e\u4f1a", "\ud83e\udd19");
        ZHIHU_EMOJI_MAP.put("\u597d\u7684", "\ud83d\udc4c"); ZHIHU_EMOJI_MAP.put("\u6253call", "\ud83d\udce3");
        ZHIHU_EMOJI_MAP.put("666", "\ud83e\udd19"); ZHIHU_EMOJI_MAP.put("\u4e92\u7c89", "\ud83e\udd1d");
        ZHIHU_EMOJI_MAP.put("\u8dea\u4e86", "\ud83e\uddce"); ZHIHU_EMOJI_MAP.put("\u9178", "\ud83c\udf4b");
        ZHIHU_EMOJI_MAP.put("\u6c57", "\ud83d\ude13"); ZHIHU_EMOJI_MAP.put("\u5403\u60ca", "\ud83d\ude32");
        ZHIHU_EMOJI_MAP.put("\u6d41\u6cea", "\ud83d\ude22"); ZHIHU_EMOJI_MAP.put("\u6487\u5634", "\ud83d\ude12");
        ZHIHU_EMOJI_MAP.put("\u8272", "\ud83d\ude0d"); ZHIHU_EMOJI_MAP.put("\u50b2\u6162", "\ud83d\ude24");
        ZHIHU_EMOJI_MAP.put("\u5feb\u54ed\u4e86", "\ud83e\udd79"); ZHIHU_EMOJI_MAP.put("\u8c03\u76ae", "\ud83d\ude1c");
        ZHIHU_EMOJI_MAP.put("\u9177", "\ud83d\ude0e"); ZHIHU_EMOJI_MAP.put("\u51b7\u6c57", "\ud83d\ude30");
        ZHIHU_EMOJI_MAP.put("\u62a0\u9f3b", "\ud83e\udd0f"); ZHIHU_EMOJI_MAP.put("\u563f\u54c8", "\ud83d\ude06");
        ZHIHU_EMOJI_MAP.put("\u76b1\u7709", "\ud83d\ude1f"); ZHIHU_EMOJI_MAP.put("\u8036", "\u270c\ufe0f");
        ZHIHU_EMOJI_MAP.put("\u54c8\u6b20", "\ud83e\udd71"); ZHIHU_EMOJI_MAP.put("\u594b\u6597", "\ud83d\udcaa");
        ZHIHU_EMOJI_MAP.put("\u5410", "\ud83e\udd2e"); ZHIHU_EMOJI_MAP.put("\u5634\u5507", "\ud83d\udc8b");
        ZHIHU_EMOJI_MAP.put("\u518d\u89c1", "\ud83d\udc4b"); ZHIHU_EMOJI_MAP.put("\u62b1\u62b1", "\ud83e\udd17");
        ZHIHU_EMOJI_MAP.put("\u574f\u7b11", "\ud83d\ude0f"); ZHIHU_EMOJI_MAP.put("\u767d\u773c", "\ud83d\ude44");
        ZHIHU_EMOJI_MAP.put("\u53f3\u54fc\u54fc", "\ud83d\ude24"); ZHIHU_EMOJI_MAP.put("\u5de6\u54fc\u54fc", "\ud83d\ude24");
        ZHIHU_EMOJI_MAP.put("\u53f9\u6c14", "\ud83d\ude2e\u200d\ud83d\udca8");
    }

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

        StringBuilder extra = new StringBuilder();
        appendZhihuCommentsHtml(extra, answerId, (byte) 1);
        appendAiAnalysisHtml(extra, "zhihu", answerId, "answer");
        appendActionBar(extra, "zhihu", answerId, "answer", true);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(wrapHtml(safe(answer.getQuestionTitle()), html + extra.toString()));
    }

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
        appendActionBar(extra, "zhihu", articleId, "article", true);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(wrapHtml(safe(article.getTitle()), html + extra.toString()));
    }

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
        appendActionBar(extra, "guba", postId, "post", false);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(wrapHtml(safe(post.getTitle()), html + extra.toString()));
    }

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
        appendZhihuCommentsHtml(extra, pinId, (byte) 3);
        appendAiAnalysisHtml(extra, "zhihu", pinId, "pin");
        appendActionBar(extra, "zhihu", pinId, "pin", true);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(wrapHtml(title, html + extra.toString()));
    }

    @GetMapping(value = "/view/{author}/{file:.+}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> viewLegacy(@PathVariable("author") String author, @PathVariable("file") String file) {
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

    private void appendZhihuCommentsHtml(StringBuilder html, Long targetId, byte targetType) {
        ZhihuCommentDOExample cExample = new ZhihuCommentDOExample();
        cExample.createCriteria().andTargetIdEqualTo(targetId).andTargetTypeEqualTo(targetType);
        cExample.setOrderByClause("created_time ASC");
        List<ZhihuCommentDO> comments = commentMapper.selectByExampleWithBLOBs(cExample);
        if (comments.isEmpty()) return;

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
            html.append("<span class=\"comment-reply-to\"> -> ").append(escape(replyTo)).append("</span>");
        }
        html.append("</div>");
        html.append("<div class=\"comment-content\">").append(sanitizeCommentHtml(c.getContent())).append("</div>");
        html.append("<div class=\"comment-meta\">");
        if (c.getCreatedTime() != null) html.append("<span>").append(c.getCreatedTime()).append("</span>");
        if (c.getLikeCount() != null && c.getLikeCount() > 0) html.append("<span>\ud83d\udc4d ").append(c.getLikeCount()).append("</span>");
        html.append("</div>");
        html.append("</div>");
    }

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
                html.append("<span class=\"comment-reply-to\"> -> ").append(escape(c.getReplyToUser())).append("</span>");
            }
            html.append("</div>");
            html.append("<div class=\"comment-content\">").append(sanitizeCommentHtml(c.getContent())).append("</div>");
            html.append("<div class=\"comment-meta\">");
            if (c.getPublishTime() != null) html.append("<span>").append(c.getPublishTime()).append("</span>");
            if (c.getLikeCount() != null && c.getLikeCount() > 0) html.append("<span>\ud83d\udc4d ").append(c.getLikeCount()).append("</span>");
            html.append("</div>");
            html.append("</div></div>");
        }

        html.append("</div></div>");
    }

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
            html.append(renderMarkdown(safe(a.getResult())));
            html.append("</div></div></div>");
        }
    }

    private void appendActionBar(StringBuilder html, String source, Long targetId, String targetType, boolean hasComments) {
        html.append("<div class=\"action-bar\">");
        if (hasComments) {
            html.append("<button class=\"action-btn\" onclick=\"doAction(this,'/api/zhihu/re-crawl-comments',{source:'")
                .append(source).append("',targetId:'").append(targetId).append("',targetType:'").append(targetType)
                .append("'},'重爬评论')\">重爬评论</button>");
        }
        html.append("<button class=\"action-btn\" onclick=\"doAction(this,'/api/zhihu/re-analyze',{source:'")
            .append(source).append("',targetId:'").append(targetId).append("',targetType:'").append(targetType)
            .append("'},'重新分析')\">重新分析</button>");
        html.append("<button class=\"action-btn action-btn-danger\" onclick=\"doDelete(this,'")
            .append(source).append("','").append(targetType).append("','").append(targetId)
            .append("')\">删除</button>");
        html.append("</div>");
    }

    private String renderMarkdown(String markdown) {
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }

    private String adjustImagePaths(String html, String authorDir) {
        if (authorDir == null || authorDir.isBlank()) return html;
        String safeDirName = authorDir.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "_");
        String encodedDir = java.net.URLEncoder.encode(safeDirName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
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
                    .comment-section { margin-top: 20px; border-top: 0.5px solid var(--separator); padding-top: 0; }
                    .comment-toggle {
                      display: flex; align-items: center; justify-content: space-between;
                      padding: 14px 0; cursor: pointer;
                      -webkit-tap-highlight-color: transparent; user-select: none;
                    }
                    .comment-toggle span { font-size: 17px; font-weight: 600; color: var(--label); }
                    .comment-toggle .chevron { color: var(--label-tertiary); transition: transform 0.25s cubic-bezier(0.4, 0, 0.2, 1); }
                    .comment-section.open .comment-toggle .chevron { transform: rotate(180deg); }
                    .comment-body { max-height: 0; overflow: hidden; transition: max-height 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
                    .comment-section.open .comment-body { max-height: 50000px; transition: max-height 0.5s ease-in; }
                    .comment-thread { padding: 12px 0; }
                    .comment-thread + .comment-thread { border-top: 0.5px solid var(--separator); }
                    .comment-item { margin-bottom: 2px; }
                    .comment-author { font-size: 15px; font-weight: 600; color: var(--label); margin-bottom: 4px; }
                    .comment-reply-to { font-weight: 400; color: var(--label-tertiary); font-size: 14px; }
                    .comment-content { font-size: 15px; line-height: 1.55; color: var(--label); margin-bottom: 4px; word-break: break-word; }
                    .comment-content a { color: var(--tint); text-decoration: none; }
                    .comment-content a:active { opacity: 0.6; }
                    .comment-meta { display: flex; gap: 12px; font-size: 13px; color: var(--label-tertiary); margin-bottom: 8px; }
                    .comment-replies { margin-left: 20px; padding-left: 12px; border-left: 2px solid var(--fill-tertiary); }
                    .comment-replies .comment-item { padding: 8px 0; }
                    .comment-replies .comment-item + .comment-item { border-top: 0.5px solid var(--separator); }
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
                    .action-bar {
                      margin-top: 20px; padding-top: 16px;
                      border-top: 0.5px solid var(--separator);
                      display: flex; gap: 10px; flex-wrap: wrap;
                    }
                    .action-btn {
                      flex: 1; min-width: 80px;
                      padding: 10px 0; border-radius: 10px; border: none;
                      font-size: 15px; font-weight: 500; cursor: pointer;
                      background: var(--tint); color: #fff;
                      transition: opacity 0.3s;
                    }
                    .action-btn:active { opacity: 0.5; }
                    .action-btn:disabled { opacity: 0.4; cursor: default; }
                    .action-btn-danger { background: #FF3B30; }
                    .action-toast {
                      position: fixed; bottom: calc(24px + var(--safe-bottom)); left: 50%; transform: translateX(-50%);
                      background: rgba(0,0,0,0.75); color: #fff; padding: 10px 20px;
                      border-radius: 20px; font-size: 15px; z-index: 100;
                      opacity: 0; transition: opacity 0.3s; pointer-events: none;
                    }
                    .action-toast.show { opacity: 1; }
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
                  <div id="actionToast" class="action-toast"></div>
                  <script>
                  function showActionToast(msg){
                    var t=document.getElementById('actionToast');
                    t.textContent=msg; t.classList.add('show');
                    setTimeout(function(){t.classList.remove('show');},2000);
                  }
                  function doAction(btn,url,body,label){
                    btn.disabled=true; btn.textContent=label+'...';
                    fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
                      .then(function(r){if(!r.ok) throw new Error('请求失败'); return r.json();})
                      .then(function(){showActionToast(label+'任务已提交');})
                      .catch(function(e){showActionToast(e.message);})
                      .finally(function(){btn.disabled=false; btn.textContent=label;});
                  }
                  function doDelete(btn,source,type,id){
                    if(!confirm('确定删除？关联的评论和AI分析结果也会一并删除。')) return;
                    btn.disabled=true; btn.textContent='删除中...';
                    fetch('/api/outputs/'+source+'/'+type+'/'+id,{method:'DELETE'})
                      .then(function(r){if(!r.ok) throw new Error('删除失败'); showActionToast('已删除'); setTimeout(function(){location.href='/';},800);})
                      .catch(function(e){showActionToast(e.message); btn.disabled=false; btn.textContent='删除';});
                  }
                  </script>
                </body>
                </html>
                """;

        content = replaceZhihuEmoji(content);
        return header.replace("__TITLE__", escape(title)) + content + footer;
    }

    /** 将知乎表情标记 [xxx] 替换为 Unicode emoji */
    private String replaceZhihuEmoji(String text) {
        if (text == null || text.isEmpty()) return text;
        for (var entry : ZHIHU_EMOJI_MAP.entrySet()) {
            text = text.replace("[" + entry.getKey() + "]", entry.getValue());
        }
        return text;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String sanitizeCommentHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        java.util.List<String> links = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)<a\\s+[^>]*href\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a>")
                .matcher(html);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String href = m.group(1);
            String text = m.group(2).replaceAll("<[^>]+>", "");
            String safeLink = "<a href=\"" + escape(href) + "\" target=\"_blank\" rel=\"noopener\">"
                    + escape(text) + "</a>";
            links.add(safeLink);
            m.appendReplacement(sb, "\u0000LINK" + (links.size() - 1) + "\u0000");
        }
        m.appendTail(sb);
        String result = sb.toString();
        result = result.replaceAll("(?i)<br\\s*/?>", "\n");
        result = result.replaceAll("(?i)</p>", "\n");
        result = result.replaceAll("<[^>]+>", "");
        result = result.replace("&nbsp;", " ")
                       .replace("&amp;", "&")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&quot;", "\"")
                       .replace("&#39;", "'");
        result = escape(result);
        result = result.replace("\n", "<br>");
        for (int i = 0; i < links.size(); i++) {
            result = result.replace("\u0000LINK" + i + "\u0000", links.get(i));
        }
        result = result.replaceAll("(<br>){3,}", "<br><br>");
        return result.trim();
    }
}
