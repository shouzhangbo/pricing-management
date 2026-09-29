package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingSceneDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingSceneMapper extends BaseCrudMapper<PricingSceneDO> {
    List<PricingSceneDO> selectByBizCode(@Param("bizCode") String bizCode, @Param("status") String status);
}
