package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ZhihuAuthorDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ZhihuAuthorDOMapper {

    @Select("SELECT * FROM zhihu_author ORDER BY created_time DESC")
    @Results(id = "authorResult", value = {
        @Result(property = "id", column = "id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "authorName", column = "author_name"),
        @Result(property = "profileUrl", column = "profile_url"),
        @Result(property = "createdTime", column = "created_time")
    })
    List<ZhihuAuthorDO> selectAll();

    @Select("SELECT * FROM zhihu_author WHERE user_id = #{userId}")
    @ResultMap("authorResult")
    ZhihuAuthorDO selectByUserId(@Param("userId") String userId);

    @Insert("INSERT INTO zhihu_author (user_id, author_name, profile_url) VALUES (#{userId}, #{authorName}, #{profileUrl})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ZhihuAuthorDO record);

    @Update("UPDATE zhihu_author SET author_name = #{authorName} WHERE id = #{id}")
    int updateName(ZhihuAuthorDO record);

    @Delete("DELETE FROM zhihu_author WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
