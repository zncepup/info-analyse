package com.infoanalyse.dao.mapper;

import com.infoanalyse.dao.model.ZhihuPinDO;
import com.infoanalyse.dao.model.ZhihuPinDOExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ZhihuPinDOMapper {
    long countByExample(ZhihuPinDOExample example);

    int deleteByExample(ZhihuPinDOExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ZhihuPinDO row);

    int insertSelective(ZhihuPinDO row);

    List<ZhihuPinDO> selectByExampleWithBLOBs(ZhihuPinDOExample example);

    List<ZhihuPinDO> selectByExample(ZhihuPinDOExample example);

    ZhihuPinDO selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ZhihuPinDO row, @Param("example") ZhihuPinDOExample example);

    int updateByExampleWithBLOBs(@Param("row") ZhihuPinDO row, @Param("example") ZhihuPinDOExample example);

    int updateByExample(@Param("row") ZhihuPinDO row, @Param("example") ZhihuPinDOExample example);

    int updateByPrimaryKeySelective(ZhihuPinDO row);

    int updateByPrimaryKeyWithBLOBs(ZhihuPinDO row);

    int updateByPrimaryKey(ZhihuPinDO row);
}