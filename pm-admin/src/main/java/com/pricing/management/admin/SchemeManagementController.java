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
    public SchemeManagementController(PricingSchemeApplicationService service) { this.service=service; }

    @PostMapping("/schemes") public ApiResponse<SchemeView> create(@Valid @RequestBody CreateSchemeRequest r) {
        return ApiResponse.success(service.create(new SchemeCommand.Create(r.bizCode(),r.groupNo(),r.feeCode(),r.schemeName(),r.remark())));
    }
    @PostMapping("/schemes/{schemeId}") public ApiResponse<SchemeView> update(@PathVariable Long schemeId,@Valid @RequestBody UpdateSchemeRequest r) {
        return ApiResponse.success(service.update(new SchemeCommand.Update(schemeId,r.schemeName(),r.remark(),r.rowVersion())));
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
    @GetMapping("/schemes/{schemeId}/dimensions") public ApiResponse<PageResult<DimensionView>> pageDimensions(@PathVariable Long schemeId,@RequestParam(required=false) String dimCode,
            @RequestParam(required=false) String dimValue,@RequestParam(defaultValue="1") @Min(1) int pageNo,@RequestParam(defaultValue="20") @Min(1) @Max(100) int pageSize) {
        return ApiResponse.success(service.pageDimensions(schemeId,dimCode,dimValue,pageNo,pageSize));
    }
    @GetMapping("/businesses/{bizCode}/fee-groups") public ApiResponse<List<FeeGroupView>> groups(@PathVariable @NotBlank String bizCode) { return ApiResponse.success(service.listFeeGroups(bizCode)); }
    @GetMapping("/businesses/{bizCode}/fee-groups/{groupNo}/fee-items") public ApiResponse<List<FeeItemView>> fees(@PathVariable @NotBlank String bizCode,@PathVariable @NotBlank String groupNo) { return ApiResponse.success(service.listFeeItems(bizCode,groupNo)); }

    public record CreateSchemeRequest(@NotBlank @Size(max=64) String bizCode,@NotBlank @Size(max=64) String groupNo,@NotBlank @Size(max=64) String feeCode,@NotBlank @Size(max=128) String schemeName,@Size(max=512) String remark) { }
    public record UpdateSchemeRequest(@NotBlank @Size(max=128) String schemeName,@Size(max=512) String remark,@NotNull @Min(0) Integer rowVersion) { }
    public record CreateDimensionRequest(@NotBlank @Size(max=64) String dimCode,@NotBlank String dimValues,@NotBlank @Pattern(regexp="IN|NOT_IN",message="matchMode 仅支持 IN 或 NOT_IN") String matchMode) { }
    public record UpdateDimensionRequest(@NotBlank String dimValues,@NotBlank @Pattern(regexp="IN|NOT_IN",message="matchMode 仅支持 IN 或 NOT_IN") String matchMode,@NotNull @Min(0) Integer rowVersion) { }
}
