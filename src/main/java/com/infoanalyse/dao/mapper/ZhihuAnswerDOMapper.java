package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ZhihuAnswerDO;
import com.infoanalyse.dao.model.ZhihuAnswerDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ZhihuAnswerDOMapper {
    long countByExample(ZhihuAnswerDOExample example);

    int deleteByExample(ZhihuAnswerDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ZhihuAnswerDO row);

    int insertSelective(ZhihuAnswerDO row);

    List<ZhihuAnswerDO> selectByExampleWithBLOBs(ZhihuAnswerDOExample example);

    List<ZhihuAnswerDO> selectByExample(ZhihuAnswerDOExample example);

    ZhihuAnswerDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ZhihuAnswerDO row, @Param("example") ZhihuAnswerDOExample example);

    int updateByExampleWithBLOBs(@Param("row") ZhihuAnswerDO row, @Param("example") ZhihuAnswerDOExample example);

    int updateByExample(@Param("row") ZhihuAnswerDO row, @Param("example") ZhihuAnswerDOExample example);

    int updateByPrimaryKeySelective(ZhihuAnswerDO row);

    int updateByPrimaryKeyWithBLOBs(ZhihuAnswerDO row);

    int updateByPrimaryKey(ZhihuAnswerDO row);
}