package com.pricing.management.application.scheme;

import com.pricing.management.infrastructure.dataobject.*;
import com.pricing.management.infrastructure.mapper.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draft-only scheme and dimension management. Edit methods take the Redis lock outside the transaction, which opens in SchemeEditTxService via the proxy. */
@Service
public class PricingSchemeApplicationService {
    private final PricingSchemeMapper schemes; private final PricingSchemeDimensionMapper dimensions;
    private final PricingFeeGroupMapper groups; private final PricingFeeItemMapper fees;
    private final TimeBasedIdGenerator ids; private final RedissonClient redisson; private final SchemeEditTxService editTx;
    public PricingSchemeApplicationService(PricingSchemeMapper schemes, PricingSchemeDimensionMapper dimensions,
            PricingFeeGroupMapper groups, PricingFeeItemMapper fees, TimeBasedIdGenerator ids,
            RedissonClient redisson, SchemeEditTxService editTx) {
        this.schemes=schemes; this.dimensions=dimensions; this.groups=groups; this.fees=fees; this.ids=ids; this.redisson=redisson; this.editTx=editTx;
    }
    @Transactional
    public SchemeView create(SchemeCommand.Create c) {
        PricingFeeGroupDO group=groups.selectByGroupNo(c.groupNo());
        if (group==null || !c.bizCode().equals(group.getBizCode()))
            throw new BusinessException("NOT_FOUND","费用包不存在或不属于该业务");
        PricingFeeItemDO fee=fees.selectByGroupId(group.getId()).stream().filter(x->c.feeCode().equals(x.getFeeCode())).findFirst()
                .orElseThrow(()->new BusinessException("NOT_FOUND","费用项不存在或不属于该费用包"));
        long id=ids.nextId();
        PricingSchemeDO s=new PricingSchemeDO();
        s.setId(id);
        s.setFeeId(fee.getId());
        s.setSchemeName(c.schemeName());
        s.setStatus("DRAFT");
        s.setGrayFlag(0);
        s.setRemark(c.remark());
        schemes.insert(s);
        return schemeView(requireScheme(id));
    }
    public SchemeView update(SchemeCommand.Update c) {
        PricingSchemeDO s=requireScheme(c.schemeId());
        return locked(s.getId(),()->schemeView(editTx.updateScheme(s,c)));
    }
    public SchemeView detail(Long schemeId) { return schemeView(requireScheme(schemeId)); }
    public DeleteResult delete(Long schemeId, Integer rowVersion) {
        PricingSchemeDO s=requireScheme(schemeId);
        return locked(s.getId(),()->{ editTx.deleteScheme(s,rowVersion); return new DeleteResult(s.getId()); });
    }
    public PageResult<SchemeView> page(String bizCode,String groupNo,String feeCode,String schemeName,String status,int pageNo,int pageSize) {
        int offset=(pageNo-1)*pageSize; long total=schemes.countPage(bizCode,groupNo,feeCode,schemeName,status);
        return new PageResult<>(total,pageNo,pageSize,schemes.selectPage(bizCode,groupNo,feeCode,schemeName,status,offset,pageSize).stream().map(this::schemeView).toList());
    }
    public DimensionView createDimension(SchemeCommand.CreateDimension c) {
        PricingSchemeDO s=requireScheme(c.schemeId());
        return locked(s.getId(),()->dimensionView(editTx.createSchemeDimension(s,c)));
    }
    public DimensionView updateDimension(SchemeCommand.UpdateDimension c) {
        PricingSchemeDimensionDO d=requireDimension(c.dimensionId());
        return locked(d.getSchemeId(),()->dimensionView(editTx.updateSchemeDimension(d,c)));
    }
    public DimensionView dimensionDetail(Long dimensionId) { return dimensionView(requireDimension(dimensionId)); }
    public DeleteResult deleteDimension(Long dimensionId, Integer rowVersion) {
        PricingSchemeDimensionDO d=requireDimension(dimensionId);
        return locked(d.getSchemeId(),()->{ editTx.deleteSchemeDimension(d,rowVersion); return new DeleteResult(d.getId()); });
    }
    public PageResult<DimensionView> pageDimensions(Long schemeId,String dimCode,String dimValue,int pageNo,int pageSize) {
        requireScheme(schemeId); int offset=(pageNo-1)*pageSize; long total=dimensions.countPage(schemeId,dimCode,dimValue);
        return new PageResult<>(total,pageNo,pageSize,dimensions.selectPage(schemeId,dimCode,dimValue,offset,pageSize).stream().map(this::dimensionView).toList());
    }
    public List<FeeGroupView> listFeeGroups(String bizCode) { return groups.selectByBizCode(bizCode).stream().map(x->new FeeGroupView(x.getId(),x.getGroupNo(),x.getGroupName(),x.getSceneCode(),x.getStatus())).toList(); }
    public List<FeeItemView> listFeeItems(String bizCode,String groupNo) {
        PricingFeeGroupDO g=groups.selectByGroupNo(groupNo); if(g==null || !bizCode.equals(g.getBizCode())) throw new BusinessException("NOT_FOUND","费用包不存在或不属于该业务");
        return fees.selectByGroupId(g.getId()).stream().map(x->new FeeItemView(x.getId(),x.getFeeCode(),x.getFeeName(),x.getSeqNo(),x.getStatus())).toList();
    }
    private PricingSchemeDO requireScheme(Long id) { PricingSchemeDO s=schemes.selectById(id); if(s==null) throw new BusinessException("NOT_FOUND","方案不存在"); return s; }
    private PricingSchemeDimensionDO requireDimension(Long id) { PricingSchemeDimensionDO d=dimensions.selectById(id); if(d==null) throw new BusinessException("NOT_FOUND","维度绑定不存在"); return d; }
    private <T> T locked(Long schemeId, Work<T> work) { RLock l=redisson.getLock("pm:lock:edit:"+schemeId); try { if(!l.tryLock(0,TimeUnit.SECONDS)) throw new BusinessException("CONFLICT","方案正在编辑，请稍后重试"); return work.get(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("SYSTEM_ERROR","获取编辑锁被中断"); } finally { if(l.isHeldByCurrentThread()) l.unlock(); } }
    private SchemeView schemeView(PricingSchemeDO s) { return new SchemeView(s.getId(),s.getFeeId(),s.getSchemeName(),s.getStatus(),s.getStartTime(),s.getEndTime(),s.getRemark(),s.getRowVersion()); }
    private DimensionView dimensionView(PricingSchemeDimensionDO d) { return new DimensionView(d.getId(),d.getSchemeId(),d.getDimCode(),d.getDimValues(),d.getMatchMode(),d.getStartTime(),d.getEndTime(),d.getRowVersion()); }
    @FunctionalInterface private interface Work<T> { T get(); }
}
