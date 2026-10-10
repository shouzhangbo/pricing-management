package com.pricing.management.infrastructure.dataobject;

import java.time.LocalDateTime;

public class PricingSchemeDO extends BaseDataObject {
    private Long feeId; private String schemeName; private String status; private LocalDateTime startTime; private LocalDateTime endTime; private String contentHash; private Integer grayFlag; private String publishedBy; private LocalDateTime publishedAt; private String remark;
    public Long getFeeId() { return feeId; } public void setFeeId(Long value) { feeId = value; }
    public String getSchemeName() { return schemeName; } public void setSchemeName(String value) { schemeName = value; }
    public String getStatus() { return status; } public void setStatus(String value) { status = value; }
    public LocalDateTime getStartTime() { return startTime; } public void setStartTime(LocalDateTime value) { startTime = value; }
    public LocalDateTime getEndTime() { return endTime; } public void setEndTime(LocalDateTime value) { endTime = value; }
    public String getContentHash() { return contentHash; } public void setContentHash(String value) { contentHash = value; }
    public Integer getGrayFlag() { return grayFlag; } public void setGrayFlag(Integer value) { grayFlag = value; }
    public String getPublishedBy() { return publishedBy; } public void setPublishedBy(String value) { publishedBy = value; }
    public LocalDateTime getPublishedAt() { return publishedAt; } public void setPublishedAt(LocalDateTime value) { publishedAt = value; }
    public String getRemark() { return remark; } public void setRemark(String value) { remark = value; }
}
