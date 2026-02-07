package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ZhihuCommentDO;
import com.infoanalyse.dao.model.ZhihuCommentDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ZhihuCommentDOMapper {
    long countByExample(ZhihuCommentDOExample example);

    int deleteByExample(ZhihuCommentDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ZhihuCommentDO row);

    int insertSelective(ZhihuCommentDO row);

    List<ZhihuCommentDO> selectByExampleWithBLOBs(ZhihuCommentDOExample example);

    List<ZhihuCommentDO> selectByExample(ZhihuCommentDOExample example);

    ZhihuCommentDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ZhihuCommentDO row, @Param("example") ZhihuCommentDOExample example);

    int updateByExampleWithBLOBs(@Param("row") ZhihuCommentDO row, @Param("example") ZhihuCommentDOExample example);

    int updateByExample(@Param("row") ZhihuCommentDO row, @Param("example") ZhihuCommentDOExample example);

    int updateByPrimaryKeySelective(ZhihuCommentDO row);

    int updateByPrimaryKeyWithBLOBs(ZhihuCommentDO row);

    int updateByPrimaryKey(ZhihuCommentDO row);
}