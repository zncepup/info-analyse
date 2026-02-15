package com.infoanalyse.web.controller;

import com.infoanalyse.dao.mapper.ContentTagMapper;
import com.infoanalyse.dao.model.ContentTagDO;
import com.infoanalyse.dao.model.ContentTagMappingDO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final ContentTagMapper tagMapper;

    public TagController(ContentTagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @GetMapping
    public List<ContentTagDO> list() {
        return tagMapper.selectAll();
    }

    @PostMapping
    public ContentTagDO create(@RequestBody TagRequest req) {
        if (req.tagName == null || req.tagName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "标签名不能为空");
        }
        ContentTagDO tag = new ContentTagDO();
        tag.setTagName(req.tagName.trim());
        tag.setColor(req.color != null ? req.color : "#007AFF");
        tag.setSortOrder(req.sortOrder != null ? req.sortOrder : 0);
        tagMapper.insert(tag);
        return tag;
    }

    @PutMapping("/{id}")
    public ContentTagDO update(@PathVariable("id") Long id, @RequestBody TagRequest req) {
        ContentTagDO tag = tagMapper.selectById(id);
        if (tag == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "标签不存在");
        if (req.tagName != null && !req.tagName.isBlank()) tag.setTagName(req.tagName.trim());
        if (req.color != null) tag.setColor(req.color);
        if (req.sortOrder != null) tag.setSortOrder(req.sortOrder);
        tagMapper.update(tag);
        return tag;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        tagMapper.deleteMappingsByTagId(id);
        int deleted = tagMapper.deleteById(id);
        return Map.of("deleted", deleted > 0);
    }

    /** 给内容打标签 */
    @PostMapping("/{id}/contents")
    public Map<String, Object> addTagToContent(@PathVariable("id") Long tagId, @RequestBody ContentRef ref) {
        validateRef(ref);
        ContentTagMappingDO m = new ContentTagMappingDO();
        m.setTagId(tagId);
        m.setSource(ref.source);
        m.setTargetId(Long.parseLong(ref.targetId));
        m.setTargetType(ref.targetType);
        tagMapper.insertMapping(m);
        return Map.of("success", true);
    }

    /** 移除内容标签 */
    @DeleteMapping("/{id}/contents")
    public Map<String, Object> removeTagFromContent(@PathVariable("id") Long tagId, @RequestBody ContentRef ref) {
        validateRef(ref);
        int deleted = tagMapper.deleteMapping(tagId, ref.source, Long.parseLong(ref.targetId), ref.targetType);
        return Map.of("deleted", deleted > 0);
    }

    /** 批量给多个内容打标签 */
    @PostMapping("/{id}/contents/batch")
    public Map<String, Object> batchAddTag(@PathVariable("id") Long tagId, @RequestBody BatchContentRef req) {
        if (req.contents == null || req.contents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容列表不能为空");
        }
        int count = 0;
        for (ContentRef ref : req.contents) {
            ContentTagMappingDO m = new ContentTagMappingDO();
            m.setTagId(tagId);
            m.setSource(ref.source);
            m.setTargetId(Long.parseLong(ref.targetId));
            m.setTargetType(ref.targetType);
            count += tagMapper.insertMapping(m);
        }
        return Map.of("added", count);
    }

    /** 查询内容的标签 */
    @GetMapping("/by-content")
    public List<ContentTagDO> getTagsByContent(@RequestParam String source,
                                                @RequestParam String targetId,
                                                @RequestParam String targetType) {
        List<ContentTagMappingDO> mappings = tagMapper.selectMappingsByContent(source, Long.parseLong(targetId), targetType);
        if (mappings.isEmpty()) return List.of();
        List<ContentTagDO> tags = new ArrayList<>();
        for (ContentTagMappingDO m : mappings) {
            ContentTagDO tag = tagMapper.selectById(m.getTagId());
            if (tag != null) tags.add(tag);
        }
        return tags;
    }

    private void validateRef(ContentRef ref) {
        if (ref.source == null || ref.targetId == null || ref.targetType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "source, targetId, targetType 必填");
        }
    }

    public static class TagRequest {
        public String tagName;
        public String color;
        public Integer sortOrder;
    }

    public static class ContentRef {
        public String source;
        public String targetId;
        public String targetType;
    }

    public static class BatchContentRef {
        public List<ContentRef> contents;
    }
}
