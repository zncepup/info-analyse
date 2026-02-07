package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.CrawlTaskDO;
import com.infoanalyse.dao.model.CrawlTaskDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CrawlTaskDOMapper {
    long countByExample(CrawlTaskDOExample example);

    int deleteByExample(CrawlTaskDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(CrawlTaskDO row);

    int insertSelective(CrawlTaskDO row);

    List<CrawlTaskDO> selectByExampleWithBLOBs(CrawlTaskDOExample example);

    List<CrawlTaskDO> selectByExample(CrawlTaskDOExample example);

    CrawlTaskDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") CrawlTaskDO row, @Param("example") CrawlTaskDOExample example);

    int updateByExampleWithBLOBs(@Param("row") CrawlTaskDO row, @Param("example") CrawlTaskDOExample example);

    int updateByExample(@Param("row") CrawlTaskDO row, @Param("example") CrawlTaskDOExample example);

    int updateByPrimaryKeySelective(CrawlTaskDO row);

    int updateByPrimaryKeyWithBLOBs(CrawlTaskDO row);

    int updateByPrimaryKey(CrawlTaskDO row);
}