package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ContentTagDO;
import com.infoanalyse.dao.model.ContentTagMappingDO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface ContentTagMapper {

    @Select("SELECT * FROM content_tag ORDER BY sort_order, id")
    List<ContentTagDO> selectAll();

    @Select("SELECT * FROM content_tag WHERE id = #{id}")
    ContentTagDO selectById(Long id);

    @Insert("INSERT INTO content_tag (tag_name, color, sort_order) VALUES (#{tagName}, #{color}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ContentTagDO tag);

    @Update("UPDATE content_tag SET tag_name=#{tagName}, color=#{color}, sort_order=#{sortOrder} WHERE id=#{id}")
    int update(ContentTagDO tag);

    @Delete("DELETE FROM content_tag WHERE id = #{id}")
    int deleteById(Long id);

    // ===== Mapping =====

    @Insert("INSERT IGNORE INTO content_tag_mapping (tag_id, source, target_id, target_type) VALUES (#{tagId}, #{source}, #{targetId}, #{targetType})")
    int insertMapping(ContentTagMappingDO mapping);

    @Delete("DELETE FROM content_tag_mapping WHERE tag_id=#{tagId} AND source=#{source} AND target_id=#{targetId} AND target_type=#{targetType}")
    int deleteMapping(@Param("tagId") Long tagId, @Param("source") String source,
                      @Param("targetId") Long targetId, @Param("targetType") String targetType);

    @Delete("DELETE FROM content_tag_mapping WHERE tag_id = #{tagId}")
    int deleteMappingsByTagId(Long tagId);

    @Select("SELECT * FROM content_tag_mapping WHERE source=#{source} AND target_id=#{targetId} AND target_type=#{targetType}")
    List<ContentTagMappingDO> selectMappingsByContent(@Param("source") String source,
                                                       @Param("targetId") Long targetId,
                                                       @Param("targetType") String targetType);

    @Select("SELECT m.source, m.target_id, m.target_type FROM content_tag_mapping m WHERE m.tag_id = #{tagId}")
    List<Map<String, Object>> selectContentsByTagId(Long tagId);
}
