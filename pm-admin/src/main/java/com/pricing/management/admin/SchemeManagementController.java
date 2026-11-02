package com.pricing.management.admin;

import com.pricing.management.application.scheme.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SchemeManagementController {
    private final PricingSchemeApplicationService service;
    private final SchemeConfigurationService configuration;
    private final PublishApplicationService publishing;
    public SchemeManagementController(PricingSchemeApplicationService service, SchemeConfigurationService configuration, PublishApplicationService publishing) { this.service=service; this.configuration=configuration; this.publishing=publishing; }

    @PostMapping("/schemes") public ApiResponse<SchemeView> create(@Valid @RequestBody CreateSchemeRequest r) {
        return ApiResponse.success(service.create(new SchemeCommand.Create(r.bizCode(),r.groupNo(),r.feeCode(),r.schemeName(),r.remark())));
    }
    @PostMapping("/schemes/{schemeId}") public ApiResponse<SchemeView> update(@PathVariable Long schemeId,@Valid @RequestBody UpdateSchemeRequest r) {
        return ApiResponse.success(service.update(new SchemeCommand.Update(schemeId,r.schemeName(),r.remark(),r.rowVersion())));
    }
    @GetMapping("/schemes/{schemeId}") public ApiResponse<SchemeView> detail(@PathVariable Long schemeId) {
        return ApiResponse.success(service.detail(schemeId));
    }
    @GetMapping("/schemes/{schemeId}/editor") public ApiResponse<SchemeEditorView> editor(@PathVariable Long schemeId) {
        return ApiResponse.success(service.editor(schemeId));
    }
    @PostMapping("/schemes/{schemeId}/delete") public ApiResponse<DeleteResult> delete(@PathVariable Long schemeId,@Valid @RequestBody DeleteRequest r) {
        return ApiResponse.success(service.delete(schemeId,r.rowVersion()));
    }
    @GetMapping("/schemes") public ApiResponse<PageResult<SchemeView>> page(@RequestParam(required=false) String bizCode,@RequestParam(required=false) String groupNo,
            @RequestParam(required=false) String feeCode,@RequestParam(required=false) String schemeName,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="1") @Min(1) int pageNo,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.success(service.page(bizCode,groupNo,feeCode,schemeName,status,pageNo,pageSize));
    }
    @PostMapping("/schemes/{schemeId}/dimensions") public ApiResponse<DimensionView> createDimension(@PathVariable Long schemeId,@Valid @RequestBody CreateDimensionRequest r) {
        return ApiResponse.success(service.createDimension(new SchemeCommand.CreateDimension(schemeId,r.dimCode(),r.dimValues(),r.matchMode())));
    }
    @PostMapping("/scheme-dimensions/{dimensionId}") public ApiResponse<DimensionView> updateDimension(@PathVariable Long dimensionId,@Valid @RequestBody UpdateDimensionRequest r) {
        return ApiResponse.success(service.updateDimension(new SchemeCommand.UpdateDimension(dimensionId,r.dimValues(),r.matchMode(),r.rowVersion())));
    }
    @GetMapping("/scheme-dimensions/{dimensionId}") public ApiResponse<DimensionView> dimensionDetail(@PathVariable Long dimensionId) {
        return ApiResponse.success(service.dimensionDetail(dimensionId));
    }
    @PostMapping("/scheme-dimensions/{dimensionId}/delete") public ApiResponse<DeleteResult> deleteDimension(@PathVariable Long dimensionId,@Valid @RequestBody DeleteRequest r) {
        return ApiResponse.success(service.deleteDimension(dimensionId,r.rowVersion()));
    }
    @GetMapping("/schemes/{schemeId}/dimensions") public ApiResponse<PageResult<DimensionView>> pageDimensions(@PathVariable Long schemeId,@RequestParam(required=false) String dimCode,
            @RequestParam(required=false) String dimValue,@RequestParam(defaultValue="1") @Min(1) int pageNo,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.success(service.pageDimensions(schemeId,dimCode,dimValue,pageNo,pageSize));
    }
    @GetMapping("/businesses/{bizCode}/fee-groups") public ApiResponse<List<FeeGroupView>> groups(@PathVariable @NotBlank String bizCode) { return ApiResponse.success(service.listFeeGroups(bizCode)); }
    @GetMapping("/businesses/{bizCode}/fee-groups/{groupNo}/fee-items") public ApiResponse<List<FeeItemView>> fees(@PathVariable @NotBlank String bizCode,@PathVariable @NotBlank String groupNo) { return ApiResponse.success(service.listFeeItems(bizCode,groupNo)); }
    @GetMapping("/fee-groups/{groupNo}/editor") public ApiResponse<FeeGroupEditorView> groupEditor(@PathVariable @NotBlank String groupNo,
            @RequestParam @NotBlank String bizCode) { return ApiResponse.success(service.groupEditor(bizCode, groupNo)); }
    @GetMapping("/factors") public ApiResponse<List<FactorView>> factors() { return ApiResponse.success(configuration.enabledFactors()); }
    @GetMapping("/templates") public ApiResponse<List<TemplateView>> templates(@RequestParam String type) { return ApiResponse.success(configuration.enabledTemplates(type)); }
    @GetMapping("/schemes/{schemeId}/factor-values") public ApiResponse<List<FactorValueView>> factorValues(@PathVariable Long schemeId) { return ApiResponse.success(configuration.factorValues(schemeId)); }
    @PutMapping("/schemes/{schemeId}/factor-values") public ApiResponse<List<FactorValueView>> saveFactorValues(@PathVariable Long schemeId,
            @Valid @RequestBody FactorValuesRequest request) { return ApiResponse.success(configuration.saveFactorValues(schemeId, request.values())); }
    @GetMapping("/schemes/{schemeId}/templates") public ApiResponse<List<SchemeTemplateView>> schemeTemplates(@PathVariable Long schemeId) { return ApiResponse.success(configuration.schemeTemplates(schemeId)); }
    @PutMapping("/schemes/{schemeId}/templates") public ApiResponse<List<SchemeTemplateView>> saveTemplates(@PathVariable Long schemeId,
            @Valid @RequestBody TemplateBindingsRequest request) { return ApiResponse.success(configuration.saveTemplates(schemeId, request.templates())); }
    @PostMapping("/fee-groups/{groupNo}/validate") public ApiResponse<ValidationReport> validatePublish(@PathVariable @NotBlank String groupNo,
            @RequestParam @NotBlank String bizCode) { return ApiResponse.success(publishing.validate(bizCode, groupNo)); }
    @PostMapping("/fee-groups/{groupNo}/publish") public ApiResponse<PublishResult> publish(@PathVariable @NotBlank String groupNo,
            @RequestParam @NotBlank String bizCode, @RequestBody PublishRequest request) {
        return ApiResponse.success(publishing.publish(bizCode, groupNo, request.startTime(), request.operator()));
    }

    public record CreateSchemeRequest(@NotBlank @Size(max=64) String bizCode,@NotBlank @Size(max=64) String groupNo,@NotBlank @Size(max=64) String feeCode,@NotBlank @Size(max=128) String schemeName,@Size(max=512) String remark) { }
    public record UpdateSchemeRequest(@NotBlank @Size(max=128) String schemeName,@Size(max=512) String remark,@NotNull @Min(0) Integer rowVersion) { }
    public record CreateDimensionRequest(@NotBlank @Size(max=64) String dimCode,@NotBlank String dimValues,@NotBlank @Pattern(regexp="IN|NOT_IN",message="matchMode 仅支持 IN 或 NOT_IN") String matchMode) { }
    public record UpdateDimensionRequest(@NotBlank String dimValues,@NotBlank @Pattern(regexp="IN|NOT_IN",message="matchMode 仅支持 IN 或 NOT_IN") String matchMode,@NotNull @Min(0) Integer rowVersion) { }
    public record DeleteRequest(@NotNull @Min(0) Integer rowVersion) { }
    public record FactorValuesRequest(@NotNull List<SchemeCommand.FactorValue> values) { }
    public record TemplateBindingsRequest(@NotNull List<SchemeCommand.TemplateBinding> templates) { }
    public record PublishRequest(java.time.LocalDateTime startTime, String operator) { }
}
