package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingSchemeDimensionDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingSchemeDimensionMapper extends BaseCrudMapper<PricingSchemeDimensionDO> {
    List<PricingSchemeDimensionDO> selectBySchemeId(@Param("schemeId") Long schemeId);
    int insertDraft(PricingSchemeDimensionDO record);
    List<PricingSchemeDimensionDO> selectPage(@Param("schemeId") Long schemeId, @Param("dimCode") String dimCode,
        @Param("dimValue") String dimValue, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countPage(@Param("schemeId") Long schemeId, @Param("dimCode") String dimCode, @Param("dimValue") String dimValue);
}
