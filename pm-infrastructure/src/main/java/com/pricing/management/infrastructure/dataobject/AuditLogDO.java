package com.pricing.management.infrastructure.dataobject;

import java.time.LocalDateTime;

/** Append-only audit record; it deliberately has no logical-delete or version fields. */
public class AuditLogDO {
    private Long id; private String bizType; private String bizId; private String action; private String beforeJson; private String afterJson; private String operator; private String traceId; private LocalDateTime operateTime;
    public Long getId() { return id; } public void setId(Long value) { id = value; }
    public String getBizType() { return bizType; } public void setBizType(String value) { bizType = value; }
    public String getBizId() { return bizId; } public void setBizId(String value) { bizId = value; }
    public String getAction() { return action; } public void setAction(String value) { action = value; }
    public String getBeforeJson() { return beforeJson; } public void setBeforeJson(String value) { beforeJson = value; }
    public String getAfterJson() { return afterJson; } public void setAfterJson(String value) { afterJson = value; }
    public String getOperator() { return operator; } public void setOperator(String value) { operator = value; }
    public String getTraceId() { return traceId; } public void setTraceId(String value) { traceId = value; }
    public LocalDateTime getOperateTime() { return operateTime; } public void setOperateTime(LocalDateTime value) { operateTime = value; }
}
