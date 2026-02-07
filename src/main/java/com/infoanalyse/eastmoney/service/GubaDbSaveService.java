package com.infoanalyse.eastmoney.service;

import com.infoanalyse.dao.mapper.GubaCommentDOMapper;
import com.infoanalyse.dao.mapper.GubaPostDOMapper;
import com.infoanalyse.dao.model.GubaCommentDO;
import com.infoanalyse.dao.model.GubaPostDO;
import com.infoanalyse.dao.model.GubaPostDOExample;
import com.infoanalyse.eastmoney.model.GubaComment;
import com.infoanalyse.eastmoney.model.GubaPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 股吧帖子数据库保存服务 - 替代文件存储
 */
@Service
public class GubaDbSaveService {

    private static final Logger logger = LoggerFactory.getLogger(GubaDbSaveService.class);

    private final GubaPostDOMapper postMapper;
    private final GubaCommentDOMapper commentMapper;

    public GubaDbSaveService(GubaPostDOMapper postMapper, GubaCommentDOMapper commentMapper) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
    }

    /**
     * 保存单个帖子及其评论到数据库（upsert 语义）
     */
    public void savePost(GubaPost post) {
        LocalDateTime now = LocalDateTime.now();
        Long postIdNum = parseLong(post.getPostId());

        // 查询是否已存在
        GubaPostDOExample example = new GubaPostDOExample();
        example.createCriteria().andPostIdEqualTo(postIdNum);
        List<GubaPostDO> existing = postMapper.selectByExample(example);

        GubaPostDO record = new GubaPostDO();
        record.setPostId(postIdNum);
        record.setStockCode(post.getStockCode());
        record.setStockName(post.getStockName());
        record.setTitle(post.getTitle());
        record.setContent(post.getContent());
        record.setHtmlContent(post.getHtmlContent());
        record.setAuthorName(post.getAuthorName());
        record.setAuthorId(post.getAuthorId());
        record.setReadCount(post.getReadCount());
        record.setCommentCount(post.getCommentCount());
        record.setLikeCount(post.getLikeCount());
        record.setUrl(post.getUrl());
        record.setPublishTime(post.getPublishTime());
        record.setCrawlTime(now);

        if (existing.isEmpty()) {
            postMapper.insertSelective(record);
            logger.info("新增股吧帖子: postId={}", postIdNum);
        } else {
            record.setId(existing.get(0).getId());
            postMapper.updateByPrimaryKeySelective(record);
            logger.info("更新股吧帖子: postId={}", postIdNum);
        }

        // 保存评论
        if (post.getComments() != null) {
            for (GubaComment comment : post.getComments()) {
                saveComment(comment, postIdNum, now);
            }
            logger.info("保存 {} 条评论 (postId={})", post.getComments().size(), postIdNum);
        }
    }

    /**
     * 批量保存帖子
     */
    public int savePosts(List<GubaPost> posts) {
        int count = 0;
        for (GubaPost post : posts) {
            try {
                savePost(post);
                count++;
            } catch (Exception e) {
                logger.warn("保存帖子失败: {} - {}", post.getPostId(), e.getMessage());
            }
        }
        return count;
    }

    private void saveComment(GubaComment comment, Long postId, LocalDateTime crawlTime) {
        Long commentIdNum = parseLong(comment.getCommentId());
        if (commentIdNum == null) return;

        com.infoanalyse.dao.model.GubaCommentDOExample example = new com.infoanalyse.dao.model.GubaCommentDOExample();
        example.createCriteria().andCommentIdEqualTo(commentIdNum);
        List<GubaCommentDO> existing = commentMapper.selectByExample(example);

        GubaCommentDO record = new GubaCommentDO();
        record.setCommentId(commentIdNum);
        record.setPostId(postId);
        record.setAuthorName(comment.getAuthorName());
        record.setAuthorId(comment.getAuthorId());
        record.setContent(comment.getContent());
        record.setLikeCount(comment.getLikeCount());
        record.setPublishTime(comment.getPublishTime());
        record.setReplyToUser(comment.getReplyToUser());
        record.setReplyToCommentId(parseLong(comment.getReplyToCommentId()));
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
