package com.pricing.management.application.scheme;

import com.pricing.management.infrastructure.dataobject.*;
import com.pricing.management.infrastructure.mapper.*;
import com.pricing.management.infrastructure.redis.RawRedisSchemeEditLock;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draft-only scheme and dimension management. Edit methods take the Redis lock outside the transaction, which opens in SchemeEditTxService via the proxy. */
@Service
public class PricingSchemeApplicationService {
    private final PricingSchemeMapper schemes; private final PricingSchemeDimensionMapper dimensions;
    private final PricingFeeGroupMapper groups; private final PricingFeeItemMapper fees;
    private final TimeBasedIdGenerator ids; private final RedissonClient redisson; private final SchemeEditTxService editTx;
    private final RawRedisSchemeEditLock editorLock;
    public PricingSchemeApplicationService(PricingSchemeMapper schemes, PricingSchemeDimensionMapper dimensions,
            PricingFeeGroupMapper groups, PricingFeeItemMapper fees, TimeBasedIdGenerator ids,
            RedissonClient redisson, SchemeEditTxService editTx, RawRedisSchemeEditLock editorLock) {
        this.schemes=schemes; this.dimensions=dimensions; this.groups=groups; this.fees=fees; this.ids=ids; this.redisson=redisson; this.editTx=editTx; this.editorLock=editorLock;
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
    /**
     * 编辑器视图加载：用原始 Redis（SETNX + GET/EXPIRE/DEL，不使用 Lua）抢编辑器分布式锁，
     * 防止同一方案被多人并发打开编辑器后互相覆盖；同一线程嵌套进入按重入放行。
     * 流程：1 校验方案存在 → 2 抢锁（快速失败；Redis 不可用按 fail-close 拒绝）→ 3 组装视图 → 4 释放锁。
     */
    public SchemeEditorView editor(Long schemeId) {
        // 流程1：先校验方案存在，避免为不存在的方案做无意义的加锁/解锁
        PricingSchemeDO scheme = requireScheme(schemeId);
        // 流程2：尝试加锁（不等待、不重试，抢不到立即返回冲突提示；同一线程嵌套进入会重入放行并刷新 TTL）。
        //        Redis 连接异常会抛 DataAccessException，此处按项目 fail-close 口径直接拒绝访问，
        //        绝不"无锁放行"——编辑低频高代价，宁可停服也不牺牲数据正确性。
        String lockToken;
        try {
            lockToken = editorLock.tryAcquire(schemeId);
        } catch (DataAccessException e) {
            throw new BusinessException("SYSTEM_ERROR", "Redis 不可用，编辑器入口拒绝访问（fail-close）");
        }
        // tryAcquire 返回 null = SET NX 未命中，锁在他人手里
        if (lockToken == null) {
            throw new BusinessException("CONFLICT", "该方案编辑器正被他人使用，请稍后重试");
        }
        try {
            // 流程3：持锁期间组装载入编辑器所需的完整视图：方案主体 + 全部维度绑定；仅 DRAFT 状态可编辑
            boolean editable = "DRAFT".equals(scheme.getStatus());
            return new SchemeEditorView(schemeView(scheme),
                    dimensions.selectBySchemeId(schemeId).stream().map(this::dimensionView).toList(),
                    editable, editable ? null : "仅草稿方案允许编辑");
        } finally {
            // 流程4：无论业务成功与否都释放锁。release 内部"GET 比对 token 相等才 DEL"（原始命令，不用 Lua），
            //        只删本次持有的锁；同线程重入时内层 release 只回退计数，最外层才真删；
            //        即使此处释放失败（Redis 闪断），锁也会在 TTL 30s 后自动过期兜底，不产生死锁，因此异常可安全吞掉。
            try {
                editorLock.release(schemeId, lockToken);
            } catch (DataAccessException ignored) { }
        }
    }
    public FeeGroupEditorView groupEditor(String bizCode, String groupNo) {
        PricingFeeGroupDO group = groups.selectByGroupNo(groupNo);
        if (group == null || !bizCode.equals(group.getBizCode()))
            throw new BusinessException("NOT_FOUND", "费用组不存在或不属于该业务");
        List<FeeGroupEditorView.FeeEditorView> items = fees.selectByGroupId(group.getId()).stream().map(fee ->
                new FeeGroupEditorView.FeeEditorView(new FeeItemView(fee.getId(), fee.getFeeCode(), fee.getFeeName(), fee.getSeqNo(), fee.getStatus()),
                        schemes.selectPage(bizCode, groupNo, fee.getFeeCode(), null, null, 0, 1000).stream()
                                .map(s -> new SchemeEditorView(schemeView(s), dimensions.selectBySchemeId(s.getId()).stream().map(this::dimensionView).toList(),
                                        "DRAFT".equals(s.getStatus()), "DRAFT".equals(s.getStatus()) ? null : "仅草稿方案允许编辑"))
                                .toList())).toList();
        return new FeeGroupEditorView(new FeeGroupView(group.getId(), group.getGroupNo(), group.getGroupName(), group.getSceneCode(), group.getStatus()), items);
    }
    private PricingSchemeDO requireScheme(Long id) { PricingSchemeDO s=schemes.selectById(id); if(s==null) throw new BusinessException("NOT_FOUND","方案不存在"); return s; }
    private PricingSchemeDimensionDO requireDimension(Long id) { PricingSchemeDimensionDO d=dimensions.selectById(id); if(d==null) throw new BusinessException("NOT_FOUND","维度绑定不存在"); return d; }
    private <T> T locked(Long schemeId, Work<T> work) { RLock l=redisson.getLock("pm:lock:edit:"+schemeId); try { if(!l.tryLock(0,TimeUnit.SECONDS)) throw new BusinessException("CONFLICT","方案正在编辑，请稍后重试"); return work.get(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new BusinessException("SYSTEM_ERROR","获取编辑锁被中断"); } finally { if(l.isHeldByCurrentThread()) l.unlock(); } }
    private SchemeView schemeView(PricingSchemeDO s) { return new SchemeView(s.getId(),s.getFeeId(),s.getSchemeName(),s.getStatus(),s.getStartTime(),s.getEndTime(),s.getRemark(),s.getRowVersion()); }
    private DimensionView dimensionView(PricingSchemeDimensionDO d) { return new DimensionView(d.getId(),d.getSchemeId(),d.getDimCode(),d.getDimValues(),d.getMatchMode(),d.getStartTime(),d.getEndTime(),d.getRowVersion()); }
    @FunctionalInterface private interface Work<T> { T get(); }
}
