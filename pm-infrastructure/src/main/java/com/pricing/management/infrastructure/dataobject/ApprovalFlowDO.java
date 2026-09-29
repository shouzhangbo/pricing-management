package com.pricing.management.infrastructure.dataobject;

public class ApprovalFlowDO extends BaseDataObject {
    private String bizType; private Long groupId; private String schemeIds; private String action; private String operator; private String opinion;
    public String getBizType() { return bizType; } public void setBizType(String value) { bizType = value; }
    public Long getGroupId() { return groupId; } public void setGroupId(Long value) { groupId = value; }
    public String getSchemeIds() { return schemeIds; } public void setSchemeIds(String value) { schemeIds = value; }
    public String getAction() { return action; } public void setAction(String value) { action = value; }
    public String getOperator() { return operator; } public void setOperator(String value) { operator = value; }
    public String getOpinion() { return opinion; } public void setOpinion(String value) { opinion = value; }
}
