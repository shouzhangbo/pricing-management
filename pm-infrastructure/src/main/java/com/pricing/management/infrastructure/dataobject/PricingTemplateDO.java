package com.pricing.management.infrastructure.dataobject;

public class PricingTemplateDO extends BaseDataObject {
    private String templateCode; private String templateName; private String templateType; private Integer versionNo; private String contentJson; private String pairCode; private String outputsJson; private String status;
    public String getTemplateCode() { return templateCode; } public void setTemplateCode(String value) { templateCode = value; }
    public String getTemplateName() { return templateName; } public void setTemplateName(String value) { templateName = value; }
    public String getTemplateType() { return templateType; } public void setTemplateType(String value) { templateType = value; }
    public Integer getVersionNo() { return versionNo; } public void setVersionNo(Integer value) { versionNo = value; }
    public String getContentJson() { return contentJson; } public void setContentJson(String value) { contentJson = value; }
    public String getPairCode() { return pairCode; } public void setPairCode(String value) { pairCode = value; }
    public String getOutputsJson() { return outputsJson; } public void setOutputsJson(String value) { outputsJson = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
