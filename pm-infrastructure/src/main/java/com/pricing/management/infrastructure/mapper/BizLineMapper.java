package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.BizLineDO;
import org.apache.ibatis.annotations.Param;

public interface BizLineMapper extends BaseCrudMapper<BizLineDO> {
    BizLineDO selectByBizCode(@Param("bizCode") String bizCode);
}
