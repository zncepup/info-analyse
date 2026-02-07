package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.CrawlImageDO;
import com.infoanalyse.dao.model.CrawlImageDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CrawlImageDOMapper {
    long countByExample(CrawlImageDOExample example);

    int deleteByExample(CrawlImageDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(CrawlImageDO row);

    int insertSelective(CrawlImageDO row);

    List<CrawlImageDO> selectByExample(CrawlImageDOExample example);

    CrawlImageDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") CrawlImageDO row, @Param("example") CrawlImageDOExample example);

    int updateByExample(@Param("row") CrawlImageDO row, @Param("example") CrawlImageDOExample example);

    int updateByPrimaryKeySelective(CrawlImageDO row);

    int updateByPrimaryKey(CrawlImageDO row);
}