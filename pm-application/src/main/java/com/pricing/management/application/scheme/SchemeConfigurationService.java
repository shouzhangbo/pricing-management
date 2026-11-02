package com.pricing.management.application.scheme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricing.management.infrastructure.dataobject.*;
import com.pricing.management.infrastructure.mapper.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for the editable factor/template part of a draft scheme. */
@Service
public class SchemeConfigurationService {
    private final PricingSchemeMapper schemes;
    private final PricingSchemeFactorValueMapper values;
    private final PricingSchemeTemplateMapper schemeTemplates;
    private final PricingFactorMapper factors;
    private final PricingTemplateMapper templates;
    private final TimeBasedIdGenerator ids;
    private final ObjectMapper json;

    public SchemeConfigurationService(PricingSchemeMapper schemes, PricingSchemeFactorValueMapper values,
            PricingSchemeTemplateMapper schemeTemplates, PricingFactorMapper factors,
            PricingTemplateMapper templates, TimeBasedIdGenerator ids, ObjectMapper json) {
        this.schemes = schemes; this.values = values; this.schemeTemplates = schemeTemplates;
        this.factors = factors; this.templates = templates; this.ids = ids; this.json = json;
    }

    public List<FactorView> enabledFactors() {
        return factors.selectEnabled().stream().map(f -> new FactorView(f.getId(), f.getFactorCode(), f.getFactorName(),
                f.getCategory(), f.getValueType(), f.getUnit(), f.getDictJson(), f.getValidateRule())).toList();
    }

    public List<TemplateView> enabledTemplates(String type) {
        if (!"CALC".equals(type) && !"DISPLAY".equals(type))
            throw new BusinessException("PARAM_ERROR", "template type 仅支持 CALC 或 DISPLAY");
        return templates.selectEnabledByType(type).stream().map(t -> new TemplateView(t.getId(), t.getTemplateCode(),
                t.getTemplateName(), t.getTemplateType(), t.getVersionNo(), t.getContentJson(), t.getPairCode(), t.getOutputsJson())).toList();
    }

    public List<FactorValueView> factorValues(Long schemeId) {
        requireDraft(schemeId);
        return values.selectBySchemeId(schemeId).stream().map(v -> new FactorValueView(v.getId(), v.getSchemeId(),
                v.getFactorCode(), v.getValueJson(), v.getRowVersion())).toList();
    }

    public List<SchemeTemplateView> schemeTemplates(Long schemeId) {
        requireDraft(schemeId);
        return schemeTemplates.selectBySchemeId(schemeId).stream().map(t -> new SchemeTemplateView(t.getId(), t.getSchemeId(),
                t.getTemplateType(), t.getTemplateCode(), t.getTemplateVersion(), t.getSlotBindings(), t.getRowVersion())).toList();
    }

    @Transactional
    public List<FactorValueView> saveFactorValues(Long schemeId, List<SchemeCommand.FactorValue> commands) {
        requireDraft(schemeId);
        for (SchemeCommand.FactorValue command : commands) {
            requireJson(command.valueJson(), "valueJson");
            if (factors.selectByFactorCode(command.factorCode()) == null)
                throw new BusinessException("NOT_FOUND", "因子不存在: " + command.factorCode());
            PricingSchemeFactorValueDO existing = values.selectBySchemeId(schemeId).stream()
                    .filter(v -> command.factorCode().equals(v.getFactorCode())).findFirst().orElse(null);
            if (existing == null) {
                PricingSchemeFactorValueDO value = new PricingSchemeFactorValueDO();
                value.setId(ids.nextId()); value.setSchemeId(schemeId); value.setFactorCode(command.factorCode()); value.setValueJson(command.valueJson());
                if (values.insert(value) != 1) throw new BusinessException("CONFLICT", "因子赋值保存失败");
            } else {
                existing.setValueJson(command.valueJson()); existing.setRowVersion(command.rowVersion());
                if (values.updateById(existing) != 1) throw new BusinessException("CONFLICT", "因子赋值已被其他人修改");
            }
        }
        return factorValues(schemeId);
    }

    @Transactional
    public List<SchemeTemplateView> saveTemplates(Long schemeId, List<SchemeCommand.TemplateBinding> commands) {
        requireDraft(schemeId);
        if (commands == null || commands.size() != 2 || commands.stream().map(SchemeCommand.TemplateBinding::templateType).distinct().count() != 2)
            throw new BusinessException("PARAM_ERROR", "方案必须同时绑定一个 CALC 和一个 DISPLAY 模板");
        for (SchemeCommand.TemplateBinding command : commands) {
            PricingTemplateDO template = templates.selectByCodeAndVersion(command.templateCode(), command.templateVersion());
            if (template == null || !command.templateType().equals(template.getTemplateType()) || !"ENABLED".equals(template.getStatus()))
                throw new BusinessException("PARAM_ERROR", "模板不存在、类型不匹配或已停用");
            requireJson(command.slotBindings(), "slotBindings", true);
            PricingSchemeTemplateDO existing = schemeTemplates.selectBySchemeId(schemeId).stream()
                    .filter(t -> command.templateType().equals(t.getTemplateType())).findFirst().orElse(null);
            if (existing == null) {
                PricingSchemeTemplateDO value = new PricingSchemeTemplateDO(); value.setId(ids.nextId()); value.setSchemeId(schemeId);
                value.setTemplateType(command.templateType()); value.setTemplateCode(command.templateCode()); value.setTemplateVersion(command.templateVersion()); value.setSlotBindings(command.slotBindings());
                if (schemeTemplates.insert(value) != 1) throw new BusinessException("CONFLICT", "模板绑定保存失败");
            } else {
                existing.setTemplateCode(command.templateCode()); existing.setTemplateVersion(command.templateVersion()); existing.setSlotBindings(command.slotBindings()); existing.setRowVersion(command.rowVersion());
                if (schemeTemplates.updateById(existing) != 1) throw new BusinessException("CONFLICT", "模板绑定已被其他人修改");
            }
        }
        return schemeTemplates(schemeId);
    }

    private PricingSchemeDO requireDraft(Long schemeId) {
        PricingSchemeDO scheme = schemes.selectById(schemeId);
        if (scheme == null) throw new BusinessException("NOT_FOUND", "方案不存在");
        if (!"DRAFT".equals(scheme.getStatus())) throw new BusinessException("CONFLICT", "仅草稿方案允许编辑");
        return scheme;
    }
    private void requireJson(String source, String field) { requireJson(source, field, false); }
    private void requireJson(String source, String field, boolean nullable) {
        if (source == null && nullable) return;
        if (source == null) throw new BusinessException("PARAM_ERROR", field + " 必须是合法 JSON");
        try { json.readTree(source); } catch (Exception e) { throw new BusinessException("PARAM_ERROR", field + " 必须是合法 JSON"); }
    }
}
