package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingSchemeFactorValueDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingSchemeFactorValueMapper extends BaseCrudMapper<PricingSchemeFactorValueDO> {
    List<PricingSchemeFactorValueDO> selectBySchemeId(@Param("schemeId") Long schemeId);
}
