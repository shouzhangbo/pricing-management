package com.pricing.management.infrastructure.dataobject;

public class PricingSchemeFactorValueDO extends BaseDataObject {
    private Long schemeId; private String factorCode; private String valueJson;
    public Long getSchemeId() { return schemeId; } public void setSchemeId(Long value) { schemeId = value; }
    public String getFactorCode() { return factorCode; } public void setFactorCode(String value) { factorCode = value; }
    public String getValueJson() { return valueJson; } public void setValueJson(String value) { valueJson = value; }
}
