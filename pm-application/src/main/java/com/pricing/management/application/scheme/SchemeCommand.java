package com.pricing.management.application.scheme;

/** Commands for the management side. Codes identify tenant, group and fee; IDs identify mutable rows. */
public final class SchemeCommand {
    private SchemeCommand() { }
    public record Create(String bizCode, String groupNo, String feeCode, String schemeName, String remark) { }
    public record Update(Long schemeId, String schemeName, String remark, Integer rowVersion) { }
    public record CreateDimension(Long schemeId, String dimCode, String dimValues, String matchMode) { }
    public record UpdateDimension(Long dimensionId, String dimValues, String matchMode, Integer rowVersion) { }
}
