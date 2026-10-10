package com.pricing.management.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.management.application.scheme.BusinessException;
import com.pricing.management.application.scheme.DeleteResult;
import com.pricing.management.application.scheme.PricingSchemeApplicationService;
import com.pricing.management.application.scheme.SchemeEditTxService;
import com.pricing.management.application.scheme.TimeBasedIdGenerator;
import com.pricing.management.infrastructure.dataobject.PricingSchemeDO;
import com.pricing.management.infrastructure.dataobject.PricingSchemeDimensionDO;
import com.pricing.management.infrastructure.mapper.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class PricingSchemeApplicationServiceTest {
    @Mock private PricingSchemeMapper schemes;
    @Mock private PricingSchemeDimensionMapper dimensions;
    @Mock private PricingFeeGroupMapper groups;
    @Mock private PricingFeeItemMapper fees;
    @Mock private TimeBasedIdGenerator ids;
    @Mock private RedissonClient redisson;
    @Mock private RLock lock;
    private PricingSchemeApplicationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PricingSchemeApplicationService(schemes, dimensions, groups, fees, ids, redisson,
                new SchemeEditTxService(schemes, dimensions, ids, new ObjectMapper()));
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(0, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void shouldDeleteDraftSchemeWithoutBindings() {
        PricingSchemeDO scheme = scheme(101L, "DRAFT");
        when(schemes.selectById(101L)).thenReturn(scheme);
        when(dimensions.selectBySchemeId(101L)).thenReturn(List.of());
        when(schemes.logicalDelete(101L, 4)).thenReturn(1);

        DeleteResult result = service.delete(101L, 4);

        assertEquals(101L, result.id());
        verify(schemes).logicalDelete(101L, 4);
        verify(lock).unlock();
    }

    @Test
    void shouldRejectDeletingSchemeWithBindings() {
        when(schemes.selectById(101L)).thenReturn(scheme(101L, "DRAFT"));
        when(dimensions.selectBySchemeId(101L)).thenReturn(List.of(dimension(201L, 101L)));

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(101L, 0));

        assertEquals("CONFLICT", error.getCode());
        verify(schemes, never()).logicalDelete(anyLong(), anyInt());
    }

    @Test
    void shouldRejectDeletingPublishedScheme() {
        when(schemes.selectById(101L)).thenReturn(scheme(101L, "PUBLISHED"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.delete(101L, 0));

        assertEquals("CONFLICT", error.getCode());
        verify(dimensions, never()).selectBySchemeId(anyLong());
        verify(schemes, never()).logicalDelete(anyLong(), anyInt());
    }

    @Test
    void shouldReturnConflictWhenDimensionDeleteLosesOptimisticLock() {
        when(dimensions.selectById(201L)).thenReturn(dimension(201L, 101L));
        when(dimensions.logicalDelete(201L, 2)).thenReturn(0);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteDimension(201L, 2));

        assertEquals("CONFLICT", error.getCode());
    }

    private PricingSchemeDO scheme(Long id, String status) {
        PricingSchemeDO value = new PricingSchemeDO(); value.setId(id); value.setStatus(status); value.setRowVersion(0); return value;
    }
    private PricingSchemeDimensionDO dimension(Long id, Long schemeId) {
        PricingSchemeDimensionDO value = new PricingSchemeDimensionDO(); value.setId(id); value.setSchemeId(schemeId); value.setRowVersion(0); return value;
    }
}
