package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingFeeItemDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingFeeItemMapper extends BaseCrudMapper<PricingFeeItemDO> {
    List<PricingFeeItemDO> selectByGroupId(@Param("groupId") Long groupId);
}
