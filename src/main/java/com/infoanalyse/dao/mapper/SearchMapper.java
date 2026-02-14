package com.infoanalyse.dao.mapper;

import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

public interface SearchMapper {
    List<Map<String, Object>> searchAll(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 根据内容类型和ID查标题 */
    Map<String, Object> findContentTitle(@Param("type") String type, @Param("targetId") Object targetId);
}
