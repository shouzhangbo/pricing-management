package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingTemplateDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingTemplateMapper extends BaseCrudMapper<PricingTemplateDO> {
    PricingTemplateDO selectByCodeAndVersion(@Param("templateCode") String templateCode, @Param("versionNo") Integer versionNo);
    List<PricingTemplateDO> selectEnabledByType(@Param("templateType") String templateType);
}
