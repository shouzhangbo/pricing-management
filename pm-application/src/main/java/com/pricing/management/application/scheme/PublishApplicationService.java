package com.pricing.management.application.scheme;

import com.pricing.management.infrastructure.dataobject.*;
import com.pricing.management.infrastructure.mapper.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishApplicationService {
    private final PricingSchemeMapper schemes; private final PricingSchemeDimensionMapper dimensions;
    private final PricingSchemeFactorValueMapper factorValues; private final PricingSchemeTemplateMapper templates;
    private final PricingFeeGroupMapper groups;
    public PublishApplicationService(PricingSchemeMapper schemes, PricingSchemeDimensionMapper dimensions,
            PricingSchemeFactorValueMapper factorValues, PricingSchemeTemplateMapper templates, PricingFeeGroupMapper groups) {
        this.schemes=schemes; this.dimensions=dimensions; this.factorValues=factorValues; this.templates=templates; this.groups=groups;
    }
    public ValidationReport validate(String bizCode, String groupNo) {
        requireGroup(bizCode, groupNo); List<ValidationReport.ValidationIssue> errors=new ArrayList<>();
        List<PricingSchemeDO> drafts=schemes.selectPage(bizCode, groupNo, null, null, "DRAFT", 0, 1000);
        if(drafts.isEmpty()) errors.add(issue("group","EMPTY_DRAFT","费用组没有可发布草稿"));
        for(PricingSchemeDO s:drafts) validateScheme(s,errors);
        return new ValidationReport(errors.isEmpty(),errors,List.of());
    }
    @Transactional(rollbackFor=Exception.class)
    public PublishResult publish(String bizCode,String groupNo,LocalDateTime startTime,String operator) {
        PricingFeeGroupDO group=requireGroup(bizCode,groupNo); ValidationReport report=validate(bizCode,groupNo);
        if(!report.valid()) throw new BusinessException("PARAM_ERROR",report.errors().getFirst().message());
        LocalDateTime effective=startTime==null?LocalDateTime.now():startTime; String batch=UUID.randomUUID().toString();
        List<PricingSchemeDO> drafts=schemes.selectPage(bizCode,groupNo,null,null,"DRAFT",0,1000);
        for(PricingSchemeDO draft:drafts) {
            for(PricingSchemeDO old:schemes.selectPublishedByFeeIdForUpdate(draft.getFeeId()))
                if(old.getEndTime()==null||old.getEndTime().isAfter(effective)) schemes.closePublished(old.getId(),effective);
            if(schemes.publishDraft(draft.getId(),effective,batch,operator)==0) throw new BusinessException("CONFLICT","草稿已被修改，请刷新后重试");
        }
        return new PublishResult(batch,group.getGroupNo(),effective,drafts.size());
    }
    private void validateScheme(PricingSchemeDO s,List<ValidationReport.ValidationIssue> errors) {
        String p="schemes["+s.getId()+"]";
        if(dimensions.selectBySchemeId(s.getId()).isEmpty()) errors.add(issue(p+".dimensions","DIMENSION_REQUIRED","方案至少需要一条维度绑定"));
        if(factorValues.selectBySchemeId(s.getId()).isEmpty()) errors.add(issue(p+".factorValues","FACTOR_REQUIRED","方案至少需要一条因子赋值"));
        List<PricingSchemeTemplateDO> ts=templates.selectBySchemeId(s.getId());
        if(ts.stream().noneMatch(t->"CALC".equals(t.getTemplateType()))||ts.stream().noneMatch(t->"DISPLAY".equals(t.getTemplateType()))) errors.add(issue(p+".templates","TEMPLATE_PAIR_REQUIRED","方案必须同时绑定 CALC 和 DISPLAY 模板"));
    }
    private PricingFeeGroupDO requireGroup(String bizCode,String groupNo){ PricingFeeGroupDO g=groups.selectByGroupNo(groupNo); if(g==null||!bizCode.equals(g.getBizCode())) throw new BusinessException("NOT_FOUND","费用组不存在或不属于该业务"); return g; }
    private ValidationReport.ValidationIssue issue(String p,String c,String m){return new ValidationReport.ValidationIssue(p,c,m);}
}
