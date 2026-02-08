package com.infoanalyse.zhihu.service;

import com.infoanalyse.dao.mapper.ZhihuAnswerDOMapper;
import com.infoanalyse.dao.mapper.ZhihuArticleDOMapper;
import com.infoanalyse.dao.mapper.ZhihuCommentDOMapper;
import com.infoanalyse.dao.mapper.ZhihuPinDOMapper;
import com.infoanalyse.dao.model.*;
import com.infoanalyse.zhihu.model.ZhihuAnswer;
import com.infoanalyse.zhihu.model.ZhihuArticle;
import com.infoanalyse.zhihu.model.ZhihuComment;
import com.infoanalyse.zhihu.model.ZhihuPin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知乎内容数据库保存服务 - 替代文件存储
 */
@Service
public class ZhihuDbSaveService {

    private static final Logger logger = LoggerFactory.getLogger(ZhihuDbSaveService.class);

    private final ZhihuAnswerDOMapper answerMapper;
    private final ZhihuArticleDOMapper articleMapper;
    private final ZhihuCommentDOMapper commentMapper;
    private final ZhihuPinDOMapper pinMapper;

    public ZhihuDbSaveService(ZhihuAnswerDOMapper answerMapper,
                              ZhihuArticleDOMapper articleMapper,
                              ZhihuCommentDOMapper commentMapper,
                              ZhihuPinDOMapper pinMapper) {
        this.answerMapper = answerMapper;
        this.articleMapper = articleMapper;
        this.commentMapper = commentMapper;
        this.pinMapper = pinMapper;
    }

    /**
     * 保存回答及其评论到数据库
     */
    public void saveAnswer(ZhihuAnswer answer) {
        LocalDateTime now = LocalDateTime.now();
        Long answerId = parseLong(answer.getId());
        if (answerId == null) return;

        ZhihuAnswerDOExample example = new ZhihuAnswerDOExample();
        example.createCriteria().andAnswerIdEqualTo(answerId);
        List<ZhihuAnswerDO> existing = answerMapper.selectByExample(example);

        ZhihuAnswerDO record = new ZhihuAnswerDO();
        record.setAnswerId(answerId);
        record.setQuestionId(parseLong(answer.getQuestionId()));
        record.setQuestionTitle(answer.getQuestionTitle());
        record.setAuthorName(answer.getAuthorName());
        record.setAuthorId(answer.getAuthorId());
        record.setContent(answer.getContent());
        record.setHtmlContent(answer.getHtmlContent());
        record.setVoteupCount(answer.getVoteupCount());
        record.setCommentCount(answer.getCommentCount());
        record.setUrl(answer.getUrl());
        record.setCreatedTime(answer.getCreatedTime());
        record.setUpdatedTime(answer.getUpdatedTime());
        record.setCrawlTime(now);

        if (existing.isEmpty()) {
            answerMapper.insertSelective(record);
            logger.info("新增知乎回答: answerId={}", answerId);
        } else {
            record.setId(existing.get(0).getId());
            answerMapper.updateByPrimaryKeySelective(record);
            logger.info("更新知乎回答: answerId={}", answerId);
        }

        // 保存评论
        if (answer.getComments() != null) {
            for (ZhihuComment comment : answer.getComments()) {
                saveComment(comment, answerId, (byte) 1, now);
            }
            logger.info("保存 {} 条评论 (answerId={})", answer.getComments().size(), answerId);
        }
    }

    /**
     * 保存文章及其评论到数据库
     */
    public void saveArticle(ZhihuArticle article) {
        LocalDateTime now = LocalDateTime.now();
        Long articleId = parseLong(article.getId());
        if (articleId == null) return;

        ZhihuArticleDOExample example = new ZhihuArticleDOExample();
        example.createCriteria().andArticleIdEqualTo(articleId);
        List<ZhihuArticleDO> existing = articleMapper.selectByExample(example);

        ZhihuArticleDO record = new ZhihuArticleDO();
        record.setArticleId(articleId);
        record.setTitle(article.getTitle());
        record.setAuthorName(article.getAuthorName());
        record.setAuthorId(article.getAuthorId());
        record.setContent(article.getContent());
        record.setHtmlContent(article.getHtmlContent());
        record.setVoteupCount(article.getVoteupCount());
        record.setCommentCount(article.getCommentCount());
        record.setUrl(article.getUrl());
        record.setCreatedTime(article.getCreatedTime());
        record.setUpdatedTime(article.getUpdatedTime());
        record.setCrawlTime(now);

        if (existing.isEmpty()) {
            articleMapper.insertSelective(record);
            logger.info("新增知乎文章: articleId={}", articleId);
        } else {
            record.setId(existing.get(0).getId());
            articleMapper.updateByPrimaryKeySelective(record);
            logger.info("更新知乎文章: articleId={}", articleId);
        }

        // 保存评论
        if (article.getComments() != null) {
            for (ZhihuComment comment : article.getComments()) {
                saveComment(comment, articleId, (byte) 2, now);
            }
            logger.info("保存 {} 条评论 (articleId={})", article.getComments().size(), articleId);
        }
    }

    /**
     * 保存想法到数据库
     */
    public void savePin(ZhihuPin pin) {
        LocalDateTime now = LocalDateTime.now();
        Long pinId = parseLong(pin.getId());
        if (pinId == null) return;

        ZhihuPinDOExample example = new ZhihuPinDOExample();
        example.createCriteria().andPinIdEqualTo(pinId);
        List<ZhihuPinDO> existing = pinMapper.selectByExample(example);

        ZhihuPinDO record = new ZhihuPinDO();
        record.setPinId(pinId);
        record.setAuthorName(pin.getAuthorName());
        record.setAuthorId(pin.getAuthorId());
        record.setContent(pin.getContent());
        record.setHtmlContent(pin.getHtmlContent());
        record.setLikeCount(pin.getLikeCount());
        record.setCommentCount(pin.getCommentCount());
        record.setRepinCount(pin.getRepinCount());
        record.setUrl(pin.getUrl());
        record.setCreatedTime(pin.getCreatedTime());
        record.setUpdatedTime(pin.getUpdatedTime());
        record.setCrawlTime(now);

        if (existing.isEmpty()) {
            pinMapper.insertSelective(record);
            logger.info("新增知乎想法: pinId={}", pinId);
        } else {
            record.setId(existing.get(0).getId());
            pinMapper.updateByPrimaryKeySelective(record);
            logger.info("更新知乎想法: pinId={}", pinId);
        }

        // 保存评论
        if (pin.getComments() != null) {
            for (ZhihuComment comment : pin.getComments()) {
                saveComment(comment, pinId, (byte) 3, now);
            }
            logger.info("保存 {} 条评论 (pinId={})", pin.getComments().size(), pinId);
        }
    }

    /**
     * 检查想法是否已保存
     */
    public boolean isPinSaved(String pinId) {
        Long id = parseLong(pinId);
        if (id == null) return false;
        ZhihuPinDOExample example = new ZhihuPinDOExample();
        example.createCriteria().andPinIdEqualTo(id);
        return pinMapper.countByExample(example) > 0;
    }

    /**
     * 批量保存回答（跳过已存在的）
     */
    public int saveAnswers(List<ZhihuAnswer> answers) {
        Set<Long> existingIds = getSavedAnswerIds();
        int saved = 0;
        int skipped = 0;
        for (ZhihuAnswer answer : answers) {
            Long id = parseLong(answer.getId());
            if (id != null && existingIds.contains(id)) {
                skipped++;
                continue;
            }
            try {
                saveAnswer(answer);
                saved++;
            } catch (Exception e) {
                logger.warn("保存回答失败: {} - {}", answer.getId(), e.getMessage());
            }
        }
        if (skipped > 0) {
            logger.info("跳过 {} 个已保存的回答", skipped);
        }
        return saved;
    }

    /**
     * 检查回答是否已保存
     */
    public boolean isAnswerSaved(String answerId) {
        Long id = parseLong(answerId);
        if (id == null) return false;
        ZhihuAnswerDOExample example = new ZhihuAnswerDOExample();
        example.createCriteria().andAnswerIdEqualTo(id);
        return answerMapper.countByExample(example) > 0;
    }

    /**
     * 检查文章是否已保存
     */
    public boolean isArticleSaved(String articleId) {
        Long id = parseLong(articleId);
        if (id == null) return false;
        ZhihuArticleDOExample example = new ZhihuArticleDOExample();
        example.createCriteria().andArticleIdEqualTo(id);
        return articleMapper.countByExample(example) > 0;
    }

    /**
     * 获取已保存的回答 ID 集合
     */
    public Set<Long> getSavedAnswerIds() {
        ZhihuAnswerDOExample example = new ZhihuAnswerDOExample();
        List<ZhihuAnswerDO> all = answerMapper.selectByExample(example);
        Set<Long> ids = new HashSet<>();
        for (ZhihuAnswerDO a : all) {
            ids.add(a.getAnswerId());
        }
        return ids;
    }

    public void saveCommentPublic(ZhihuComment comment, Long targetId, byte targetType, LocalDateTime crawlTime) {
        saveComment(comment, targetId, targetType, crawlTime);
    }

    private void saveComment(ZhihuComment comment, Long targetId, byte targetType, LocalDateTime crawlTime) {
        Long commentId = parseLong(comment.getId());
        if (commentId == null) return;

        ZhihuCommentDOExample example = new ZhihuCommentDOExample();
        example.createCriteria().andCommentIdEqualTo(commentId);
        List<ZhihuCommentDO> existing = commentMapper.selectByExample(example);

        ZhihuCommentDO record = new ZhihuCommentDO();
        record.setCommentId(commentId);
        record.setTargetId(targetId);
        record.setTargetType(targetType);
        record.setAuthorName(comment.getAuthorName());
        record.setAuthorId(comment.getAuthorId());
        record.setContent(comment.getContent());
        record.setLikeCount(comment.getLikeCount());
        record.setCreatedTime(comment.getCreatedTime());
        record.setParentCommentId(parseLong(comment.getParentCommentId()));
        record.setReplyCommentId(parseLong(comment.getReplyCommentId()));
        record.setReplyToAuthor(comment.getReplyToAuthor());
        record.setCrawlTime(crawlTime);

        if (existing.isEmpty()) {
            commentMapper.insertSelective(record);
        } else {
            record.setId(existing.get(0).getId());
            commentMapper.updateByPrimaryKeySelective(record);
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
