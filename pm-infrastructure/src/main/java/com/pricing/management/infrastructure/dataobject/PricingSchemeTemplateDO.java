package com.pricing.management.infrastructure.dataobject;

public class PricingSchemeTemplateDO extends BaseDataObject {
    private Long schemeId; private String templateType; private String templateCode; private Integer templateVersion; private String slotBindings;
    public Long getSchemeId() { return schemeId; } public void setSchemeId(Long value) { schemeId = value; }
    public String getTemplateType() { return templateType; } public void setTemplateType(String value) { templateType = value; }
    public String getTemplateCode() { return templateCode; } public void setTemplateCode(String value) { templateCode = value; }
    public Integer getTemplateVersion() { return templateVersion; } public void setTemplateVersion(Integer value) { templateVersion = value; }
    public String getSlotBindings() { return slotBindings; } public void setSlotBindings(String value) { slotBindings = value; }
}
