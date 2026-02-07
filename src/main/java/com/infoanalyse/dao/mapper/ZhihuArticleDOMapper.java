package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ZhihuArticleDO;
import com.infoanalyse.dao.model.ZhihuArticleDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ZhihuArticleDOMapper {
    long countByExample(ZhihuArticleDOExample example);

    int deleteByExample(ZhihuArticleDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ZhihuArticleDO row);

    int insertSelective(ZhihuArticleDO row);

    List<ZhihuArticleDO> selectByExampleWithBLOBs(ZhihuArticleDOExample example);

    List<ZhihuArticleDO> selectByExample(ZhihuArticleDOExample example);

    ZhihuArticleDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ZhihuArticleDO row, @Param("example") ZhihuArticleDOExample example);

    int updateByExampleWithBLOBs(@Param("row") ZhihuArticleDO row, @Param("example") ZhihuArticleDOExample example);

    int updateByExample(@Param("row") ZhihuArticleDO row, @Param("example") ZhihuArticleDOExample example);

    int updateByPrimaryKeySelective(ZhihuArticleDO row);

    int updateByPrimaryKeyWithBLOBs(ZhihuArticleDO row);

    int updateByPrimaryKey(ZhihuArticleDO row);
}