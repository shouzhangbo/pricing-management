package com.pricing.management.infrastructure.dataobject;

import java.time.LocalDateTime;

/** JSON fields stay as canonical JSON text at persistence boundaries. */
public class PricingSchemeDimensionDO extends BaseDataObject {
    private Long schemeId; private LocalDateTime startTime; private LocalDateTime endTime; private String dimCode; private String dimValues; private String matchMode;
    public Long getSchemeId() { return schemeId; } public void setSchemeId(Long value) { schemeId = value; }
    public LocalDateTime getStartTime() { return startTime; } public void setStartTime(LocalDateTime value) { startTime = value; }
    public LocalDateTime getEndTime() { return endTime; } public void setEndTime(LocalDateTime value) { endTime = value; }
    public String getDimCode() { return dimCode; } public void setDimCode(String value) { dimCode = value; }
    public String getDimValues() { return dimValues; } public void setDimValues(String value) { dimValues = value; }
    public String getMatchMode() { return matchMode; } public void setMatchMode(String value) { matchMode = value; }
}
