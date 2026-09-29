package com.pricing.management.api.dto;

import java.io.Serializable;
import java.util.Map;

/** Concrete dimension values used to select a scheme instance. */
public record DimContextDTO(Map<String, String> values) implements Serializable {
}
