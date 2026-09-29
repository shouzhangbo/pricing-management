package com.pricing.management.infrastructure.dataobject;

public class PricingFactorDO extends BaseDataObject {
    private String factorCode; private String factorName; private String category; private String valueType; private String unit; private String dictJson; private String validateRule; private String status;
    public String getFactorCode() { return factorCode; } public void setFactorCode(String value) { factorCode = value; }
    public String getFactorName() { return factorName; } public void setFactorName(String value) { factorName = value; }
    public String getCategory() { return category; } public void setCategory(String value) { category = value; }
    public String getValueType() { return valueType; } public void setValueType(String value) { valueType = value; }
    public String getUnit() { return unit; } public void setUnit(String value) { unit = value; }
    public String getDictJson() { return dictJson; } public void setDictJson(String value) { dictJson = value; }
    public String getValidateRule() { return validateRule; } public void setValidateRule(String value) { validateRule = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
