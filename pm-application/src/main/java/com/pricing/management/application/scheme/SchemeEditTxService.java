package com.pricing.management.application.scheme;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.management.infrastructure.dataobject.PricingSchemeDO;
import com.pricing.management.infrastructure.dataobject.PricingSchemeDimensionDO;
import com.pricing.management.infrastructure.mapper.PricingSchemeDimensionMapper;
import com.pricing.management.infrastructure.mapper.PricingSchemeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draft mutations for a single scheme. Callers must hold the edit lock first; each method opens its transaction via the Spring proxy, so the lock wraps the transaction. */
@Service
public class SchemeEditTxService {
    private final PricingSchemeMapper schemes; private final PricingSchemeDimensionMapper dimensions;
    private final TimeBasedIdGenerator ids; private final ObjectMapper json;
    public SchemeEditTxService(PricingSchemeMapper schemes, PricingSchemeDimensionMapper dimensions, TimeBasedIdGenerator ids, ObjectMapper json) {
        this.schemes=schemes; this.dimensions=dimensions; this.ids=ids; this.json=json;
    }
    @Transactional public PricingSchemeDO updateScheme(PricingSchemeDO s, SchemeCommand.Update c) {
        s.setSchemeName(c.schemeName()); s.setRemark(c.remark()); s.setRowVersion(c.rowVersion());
        if(schemes.updateById(s)!=1) throw new BusinessException("CONFLICT","方案已被修改、已发布或不存在");
        return requireScheme(s.getId());
    }
    @Transactional public void deleteScheme(PricingSchemeDO s, Integer rowVersion) {
        draft(s);
        if(!dimensions.selectBySchemeId(s.getId()).isEmpty()) throw new BusinessException("CONFLICT","方案存在维度绑定，请先删除全部绑定关系");
        if(schemes.logicalDelete(s.getId(),rowVersion)!=1) throw new BusinessException("CONFLICT","方案已被修改、已发布或不存在");
    }
    @Transactional public PricingSchemeDimensionDO createSchemeDimension(PricingSchemeDO s, SchemeCommand.CreateDimension c) {
        draft(s); PricingSchemeDimensionDO d=new PricingSchemeDimensionDO();
        d.setId(ids.nextId()); d.setSchemeId(s.getId()); d.setDimCode(c.dimCode()); d.setDimValues(jsonArray(c.dimValues())); d.setMatchMode(c.matchMode());
        if(dimensions.insertDraft(d)!=1) throw new BusinessException("CONFLICT","所属方案已发布或不存在"); return requireDimension(d.getId());
    }
    @Transactional public PricingSchemeDimensionDO updateSchemeDimension(PricingSchemeDimensionDO d, SchemeCommand.UpdateDimension c) {
        d.setDimValues(jsonArray(c.dimValues())); d.setMatchMode(c.matchMode()); d.setRowVersion(c.rowVersion());
        if(dimensions.updateById(d)!=1) throw new BusinessException("CONFLICT","维度绑定已被修改、所属方案已发布或不存在"); return requireDimension(d.getId());
    }
    @Transactional public void deleteSchemeDimension(PricingSchemeDimensionDO d, Integer rowVersion) {
        if(dimensions.logicalDelete(d.getId(),rowVersion)!=1) throw new BusinessException("CONFLICT","维度绑定已被修改、所属方案已发布或不存在");
    }
    private PricingSchemeDO requireScheme(Long id) { PricingSchemeDO s=schemes.selectById(id); if(s==null) throw new BusinessException("NOT_FOUND","方案不存在"); return s; }
    private PricingSchemeDimensionDO requireDimension(Long id) { PricingSchemeDimensionDO d=dimensions.selectById(id); if(d==null) throw new BusinessException("NOT_FOUND","维度绑定不存在"); return d; }
    private void draft(PricingSchemeDO s) { if(!"DRAFT".equals(s.getStatus())) throw new BusinessException("CONFLICT","仅草稿方案允许编辑维度绑定"); }
    private String jsonArray(String source) { try { JsonNode n=json.readTree(source); if(n==null||!n.isArray()||n.isEmpty()) throw new BusinessException("PARAM_ERROR","dimValues 必须是非空 JSON 数组"); return json.writeValueAsString(n); } catch(JsonProcessingException e) { throw new BusinessException("PARAM_ERROR","dimValues 必须是合法 JSON 数组"); } }
}
