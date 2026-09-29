package com.pricing.management.api.dto;

import com.pricing.management.common.ErrorCode;
import java.io.Serializable;

/** Envelope used by the read-only Dubbo contract. */
public record DeliveryResponse<T>(boolean success, T data, ErrorCode errorCode, String message)
        implements Serializable {

    public static <T> DeliveryResponse<T> notImplemented() {
        return new DeliveryResponse<>(false, null, ErrorCode.NOT_IMPLEMENTED,
                "Delivery capability has not been implemented yet.");
    }
}
