package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingFactorDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingFactorMapper extends BaseCrudMapper<PricingFactorDO> {
    PricingFactorDO selectByFactorCode(@Param("factorCode") String factorCode);
    List<PricingFactorDO> selectEnabled();
}
