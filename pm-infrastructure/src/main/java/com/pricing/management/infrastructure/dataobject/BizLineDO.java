package com.pricing.management.infrastructure.dataobject;

public class BizLineDO extends BaseDataObject {
    private String bizCode; private String bizName; private String owner; private String status; private String remark;
    public String getBizCode() { return bizCode; } public void setBizCode(String value) { bizCode = value; }
    public String getBizName() { return bizName; } public void setBizName(String value) { bizName = value; }
    public String getOwner() { return owner; } public void setOwner(String value) { owner = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public String getRemark() { return remark; } public void setRemark(String value) { remark = value; }
}
