package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.ApprovalFlowDO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ApprovalFlowMapper extends BaseCrudMapper<ApprovalFlowDO> {
    List<ApprovalFlowDO> selectByGroupId(@Param("groupId") Long groupId);
}
