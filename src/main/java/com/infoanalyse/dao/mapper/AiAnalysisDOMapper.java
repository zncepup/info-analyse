package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.AiAnalysisDO;
import com.infoanalyse.dao.model.AiAnalysisDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AiAnalysisDOMapper {
    long countByExample(AiAnalysisDOExample example);

    int deleteByExample(AiAnalysisDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(AiAnalysisDO row);

    int insertSelective(AiAnalysisDO row);

    List<AiAnalysisDO> selectByExampleWithBLOBs(AiAnalysisDOExample example);

    List<AiAnalysisDO> selectByExample(AiAnalysisDOExample example);

    AiAnalysisDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") AiAnalysisDO row, @Param("example") AiAnalysisDOExample example);

    int updateByExampleWithBLOBs(@Param("row") AiAnalysisDO row, @Param("example") AiAnalysisDOExample example);

    int updateByExample(@Param("row") AiAnalysisDO row, @Param("example") AiAnalysisDOExample example);

    int updateByPrimaryKeySelective(AiAnalysisDO row);

    int updateByPrimaryKeyWithBLOBs(AiAnalysisDO row);

    int updateByPrimaryKey(AiAnalysisDO row);
}