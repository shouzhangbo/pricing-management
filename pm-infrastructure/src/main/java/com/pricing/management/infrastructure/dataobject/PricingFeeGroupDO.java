package com.pricing.management.infrastructure.dataobject;

public class PricingFeeGroupDO extends BaseDataObject {
    private String groupNo; private String groupName; private String bizCode; private String sceneCode; private String groupDesc; private String status;
    public String getGroupNo() { return groupNo; } public void setGroupNo(String value) { groupNo = value; }
    public String getGroupName() { return groupName; } public void setGroupName(String value) { groupName = value; }
    public String getBizCode() { return bizCode; } public void setBizCode(String value) { bizCode = value; }
    public String getSceneCode() { return sceneCode; } public void setSceneCode(String value) { sceneCode = value; }
    public String getGroupDesc() { return groupDesc; } public void setGroupDesc(String value) { groupDesc = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
