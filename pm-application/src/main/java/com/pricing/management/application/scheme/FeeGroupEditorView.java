package com.pricing.management.application.scheme;

import java.util.List;

/** Read model used by the fee-group configuration page. */
public record FeeGroupEditorView(
        FeeGroupView group,
        List<FeeEditorView> fees) {
    public record FeeEditorView(FeeItemView fee, List<SchemeEditorView> schemes) { }
}
