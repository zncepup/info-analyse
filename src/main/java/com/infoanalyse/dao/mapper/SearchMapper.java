package com.infoanalyse.dao.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface SearchMapper {
    List<Map<String, Object>> searchAll(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 根据内容类型和ID查标题 */
    Map<String, Object> findContentTitle(@Param("type") String type, @Param("targetId") Object targetId);

    /** 多条件高级搜索（分页） */
    List<Map<String, Object>> advancedSearch(@Param("keyword") String keyword,
                                              @Param("author") String author,
                                              @Param("tagIds") List<Long> tagIds,
                                              @Param("contentType") String contentType,
                                              @Param("limit") int limit,
                                              @Param("offset") int offset);

    /** 多条件高级搜索 — 仅返回总数 */
    int advancedSearchCount(@Param("keyword") String keyword,
                            @Param("author") String author,
                            @Param("tagIds") List<Long> tagIds,
                            @Param("contentType") String contentType);

}
