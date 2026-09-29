package com.pricing.management.delivery;

import com.pricing.management.api.SchemeDeliveryService;
import com.pricing.management.api.dto.DeliveryResponse;
import com.pricing.management.api.dto.DimContextDTO;
import com.pricing.management.api.dto.EffectiveSetDTO;
import com.pricing.management.api.dto.SchemeContentDTO;
import java.time.LocalDateTime;
import org.apache.dubbo.config.annotation.DubboService;

/** Dubbo provider skeleton. Query implementations are deliberately deferred. */
@DubboService(group = "pricing-delivery")
public class SchemeDeliveryServiceImpl implements SchemeDeliveryService {
    @Override
    public DeliveryResponse<EffectiveSetDTO> effectiveSet(String bizCode, String groupNo) {
        return DeliveryResponse.notImplemented();
    }

    @Override
    public DeliveryResponse<SchemeContentDTO> schemeAt(Long schemeId, LocalDateTime atTime) {
        return DeliveryResponse.notImplemented();
    }

    @Override
    public DeliveryResponse<SchemeContentDTO> hit(String bizCode, String groupNo, String feeCode,
                                                   DimContextDTO dimContext, LocalDateTime atTime) {
        return DeliveryResponse.notImplemented();
    }

    @Override
    public DeliveryResponse<EffectiveSetDTO> effectiveSetAt(String bizCode, LocalDateTime atTime) {
        return DeliveryResponse.notImplemented();
    }
}
