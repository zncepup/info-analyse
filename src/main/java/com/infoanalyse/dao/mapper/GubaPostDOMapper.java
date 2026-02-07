package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.GubaPostDO;
import com.infoanalyse.dao.model.GubaPostDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface GubaPostDOMapper {
    long countByExample(GubaPostDOExample example);

    int deleteByExample(GubaPostDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(GubaPostDO row);

    int insertSelective(GubaPostDO row);

    List<GubaPostDO> selectByExampleWithBLOBs(GubaPostDOExample example);

    List<GubaPostDO> selectByExample(GubaPostDOExample example);

    GubaPostDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") GubaPostDO row, @Param("example") GubaPostDOExample example);

    int updateByExampleWithBLOBs(@Param("row") GubaPostDO row, @Param("example") GubaPostDOExample example);

    int updateByExample(@Param("row") GubaPostDO row, @Param("example") GubaPostDOExample example);

    int updateByPrimaryKeySelective(GubaPostDO row);

    int updateByPrimaryKeyWithBLOBs(GubaPostDO row);

    int updateByPrimaryKey(GubaPostDO row);
}