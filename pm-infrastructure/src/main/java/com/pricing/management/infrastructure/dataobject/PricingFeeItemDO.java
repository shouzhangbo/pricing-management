package com.pricing.management.infrastructure.dataobject;

public class PricingFeeItemDO extends BaseDataObject {
    private Long groupId; private String feeCode; private String feeName; private Integer seqNo; private Integer isRequired; private String feeDesc; private String status;
    public Long getGroupId() { return groupId; } public void setGroupId(Long value) { groupId = value; }
    public String getFeeCode() { return feeCode; } public void setFeeCode(String value) { feeCode = value; }
    public String getFeeName() { return feeName; } public void setFeeName(String value) { feeName = value; }
    public Integer getSeqNo() { return seqNo; } public void setSeqNo(Integer value) { seqNo = value; }
    public Integer getIsRequired() { return isRequired; } public void setIsRequired(Integer value) { isRequired = value; }
    public String getFeeDesc() { return feeDesc; } public void setFeeDesc(String value) { feeDesc = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
}
