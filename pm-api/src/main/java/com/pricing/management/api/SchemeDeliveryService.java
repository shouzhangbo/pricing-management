package com.pricing.management.api;

import com.pricing.management.api.dto.DeliveryResponse;
import com.pricing.management.api.dto.DimContextDTO;
import com.pricing.management.api.dto.EffectiveSetDTO;
import com.pricing.management.api.dto.SchemeContentDTO;
import java.time.LocalDateTime;

/** Read-only pricing-distribution contract for downstream consumers. */
public interface SchemeDeliveryService {
    DeliveryResponse<EffectiveSetDTO> effectiveSet(String bizCode, String groupNo);

    DeliveryResponse<SchemeContentDTO> schemeAt(Long schemeId, LocalDateTime atTime);

    DeliveryResponse<SchemeContentDTO> hit(String bizCode, String groupNo, String feeCode,
                                            DimContextDTO dimContext, LocalDateTime atTime);

    DeliveryResponse<EffectiveSetDTO> effectiveSetAt(String bizCode, LocalDateTime atTime);
}
