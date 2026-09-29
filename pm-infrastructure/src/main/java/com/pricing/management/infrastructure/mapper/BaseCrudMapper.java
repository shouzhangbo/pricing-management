package com.pricing.management.infrastructure.mapper;

import com.pricing.management.infrastructure.dataobject.BaseDataObject;
import org.apache.ibatis.annotations.Param;

/** Base contract for mutable tables. Updates and deletes are optimistic-lock guarded. */
public interface BaseCrudMapper<T extends BaseDataObject> {
    T selectById(@Param("id") Long id);
    int insert(T record);
    int updateById(T record);
    int logicalDelete(@Param("id") Long id, @Param("rowVersion") Integer rowVersion);
}
