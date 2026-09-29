package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingSchemeTemplateDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingSchemeTemplateMapper extends BaseCrudMapper<PricingSchemeTemplateDO> {
    List<PricingSchemeTemplateDO> selectBySchemeId(@Param("schemeId") Long schemeId);
}
