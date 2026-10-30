package dev.fajar.hris.openapi

import io.swagger.v3.oas.models.OpenAPI
import java.math.BigDecimal

fun completeApprovalHistoryContract(api: OpenAPI) {
    val operation = api.paths["/api/v1/companies/{companyId}/approvals/{id}/history"]?.get ?: return
    operation.summary = "Read recorded approval decisions and a page of approver changes"
    operation.description =
        "Uses current request authorization and a coherent guarded request version. Decisions retain actor, delegated account, reason and instant in workflow-step order. Reassignments use ascending immutable revisions. A continuation requires expectedVersion; a changed request returns approval_changed (409). Reading creates no decisions or other business writes."
    for (parameter in operation.parameters.orEmpty()) {
        if (parameter.name in setOf("after", "expectedVersion")) {
            parameter.schema?.minimum =
                if (parameter.name == "after") BigDecimal.ONE else BigDecimal.ZERO
            parameter.schema?.maximum = BigDecimal("9007199254740991")
            parameter.description =
                if (parameter.name == "after")
                    "Last observed reassignment revision, exclusive. Requires expectedVersion from the preceding response."
                else
                    "Observed request.version, required for continuation. Omit with after to refresh the current first page."
        }
    }
    api.components.schemas["ApprovalHistoryResponse"]?.properties?.get("decisions")?.maxItems = 8
}
