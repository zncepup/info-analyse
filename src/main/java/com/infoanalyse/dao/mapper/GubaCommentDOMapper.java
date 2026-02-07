package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.GubaCommentDO;
import com.infoanalyse.dao.model.GubaCommentDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface GubaCommentDOMapper {
    long countByExample(GubaCommentDOExample example);

    int deleteByExample(GubaCommentDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(GubaCommentDO row);

    int insertSelective(GubaCommentDO row);

    List<GubaCommentDO> selectByExampleWithBLOBs(GubaCommentDOExample example);

    List<GubaCommentDO> selectByExample(GubaCommentDOExample example);

    GubaCommentDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") GubaCommentDO row, @Param("example") GubaCommentDOExample example);

    int updateByExampleWithBLOBs(@Param("row") GubaCommentDO row, @Param("example") GubaCommentDOExample example);

    int updateByExample(@Param("row") GubaCommentDO row, @Param("example") GubaCommentDOExample example);

    int updateByPrimaryKeySelective(GubaCommentDO row);

    int updateByPrimaryKeyWithBLOBs(GubaCommentDO row);

    int updateByPrimaryKey(GubaCommentDO row);
}