package com.pricing.management.infrastructure.dataobject;

import java.time.LocalDateTime;

/** Shared persistence fields for mutable business tables. */
public class BaseDataObject {
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;
    private Integer rowVersion;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public LocalDateTime getCreateTime() { return createTime; } public void setCreateTime(LocalDateTime value) { createTime = value; }
    public LocalDateTime getUpdateTime() { return updateTime; } public void setUpdateTime(LocalDateTime value) { updateTime = value; }
    public Integer getIsDeleted() { return isDeleted; } public void setIsDeleted(Integer value) { isDeleted = value; }
    public Integer getRowVersion() { return rowVersion; } public void setRowVersion(Integer value) { rowVersion = value; }
}
