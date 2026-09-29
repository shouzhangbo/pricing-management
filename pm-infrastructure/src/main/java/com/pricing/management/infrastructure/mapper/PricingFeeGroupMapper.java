package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingFeeGroupDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingFeeGroupMapper extends BaseCrudMapper<PricingFeeGroupDO> {
    PricingFeeGroupDO selectByGroupNo(@Param("groupNo") String groupNo);
    List<PricingFeeGroupDO> selectByScene(@Param("bizCode") String bizCode, @Param("sceneCode") String sceneCode);
    List<PricingFeeGroupDO> selectByBizCode(@Param("bizCode") String bizCode);
}
