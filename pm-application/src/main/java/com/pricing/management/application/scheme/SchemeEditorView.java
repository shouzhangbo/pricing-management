package com.pricing.management.application.scheme;

import java.util.List;

/** Read model used by the scheme edit page. */
public record SchemeEditorView(
        SchemeView scheme,
        List<DimensionView> dimensions,
        boolean editable,
        String editReason) {
}
