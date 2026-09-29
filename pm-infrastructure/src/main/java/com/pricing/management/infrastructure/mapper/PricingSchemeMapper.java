package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.PricingSchemeDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PricingSchemeMapper extends BaseCrudMapper<PricingSchemeDO> {
    List<PricingSchemeDO> selectByRootSchemeId(@Param("rootSchemeId") Long rootSchemeId);
    List<PricingSchemeDO> selectPage(@Param("bizCode") String bizCode, @Param("groupNo") String groupNo,
        @Param("feeCode") String feeCode, @Param("schemeName") String schemeName,
        @Param("status") String status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countPage(@Param("bizCode") String bizCode, @Param("groupNo") String groupNo,
        @Param("feeCode") String feeCode, @Param("schemeName") String schemeName, @Param("status") String status);
    List<PricingSchemeDO> selectEffectiveByFeeId(@Param("feeId") Long feeId, @Param("atTime") LocalDateTime atTime);
    List<PricingSchemeDO> selectPublishedByFeeIdForUpdate(@Param("feeId") Long feeId);
}
