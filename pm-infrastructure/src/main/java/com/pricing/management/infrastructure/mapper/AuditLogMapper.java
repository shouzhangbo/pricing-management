package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.AuditLogDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Append-only mapper for the partitioned audit table. */
public interface AuditLogMapper {
    int insert(AuditLogDO record);
    List<AuditLogDO> selectByBusiness(@Param("bizType") String bizType, @Param("bizId") String bizId,
                                      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
