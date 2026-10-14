package dev.fajar.hris

import dev.fajar.hris.identity.delivery.requests.LoginRequest
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.reflect.full.primaryConstructor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.TestPropertySource
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import tools.jackson.databind.JsonNode

@TestPropertySource(properties = ["springdoc.api-docs.enabled=true"])
class ApiContractHttpTest : PeopleApiFixture() {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var mappings: RequestMappingHandlerMapping

    private fun contract(): JsonNode {
        val browser = client()
        login(browser)
        val response = get(browser, "/api/v1/openapi")
        assertEquals(200, response.statusCode(), response.body().take(500))
        assertTrue(response.body().length < 2_097_152, "The API description must stay bounded")
        return json.readTree(response.body())
    }

    @Test
    fun finalizationPreparationAndDirectoryKeepApprovalVersionsAndProgressSeparate() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/payroll"
        for (path in
            listOf("/publication-runs", "/runs/{runId}/finalization-preparation")) assertTrue(
            api["paths"][base + path]["get"]["x-hris-company-admission"].asBoolean()
        )
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf(
                "run",
                "totals",
                "latestReview",
                "approvalVersion",
                "latestFinalization",
                "latestJobStatus",
                "blockingIssues",
                "availableActions",
            ),
            schemas["PayrollFinalizationPreparationResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf("finalization", "job", "run", "totals"),
            schemas["PayrollFinalizationDetailsResponse"]["properties"].propertyNames().toSet(),
        )
    }

    @Test
    fun payrollReviewPreparationAndActionsKeepVersionsAndAccountLabelsExplicit() {
        val api = contract()
        val path = "/api/v1/companies/{companyId}/payroll/runs/{runId}/review-preparation"
        assertTrue(api["paths"][path]["get"]["x-hris-company-admission"].asBoolean())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("run", "totals", "latestReview", "blockingIssues", "availableActions"),
            schemas["PayrollReviewPreparationResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf(
                "review",
                "approval",
                "changes",
                "run",
                "accounts",
                "finalizationActive",
                "availableActions",
            ),
            schemas["PayrollReviewDetailsResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf("id", "displayName"),
            schemas["PayrollReviewAccountResponse"]["properties"].propertyNames().toSet(),
        )
    }

    @Test
    fun calculationPreparationAndRunRecoveryKeepSourceReferencesExplicit() {
        val api = contract()
        val path = "/api/v1/companies/{companyId}/payroll/periods/{periodId}/calculation-review"
        assertTrue(api["paths"][path]["get"]["x-hris-company-admission"].asBoolean())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("period", "source", "policy", "runCount", "blockingIssues", "availableActions"),
            schemas["PayrollCalculationReviewResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf(
                "version",
                "appliedRevision",
                "effectiveFrom",
                "effectiveUntil",
                "incomeTaxRuleId",
                "insuranceRuleId",
            ),
            schemas["PayrollCalculationPolicyResponse"]["properties"].propertyNames().toSet(),
        )
        val details = schemas["PayrollRunDetailsResponse"]["properties"]
        assertTrue(details.has("period"))
        assertTrue(details.has("availableActions"))
        val item = schemas["PayrollRunItemResponse"]["properties"]
        assertFalse(item.has("facts"))
        assertFalse(item.has("calculation"))
    }

    @Test
    fun taxOpeningReviewExposesBoundedReferencesAndExplicitRevisionSelection() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/payroll"
        val lookup = api["paths"]["$base/tax-openings/employees"]["get"]
        val review = api["paths"]["$base/employees/{employeeId}/tax-openings/{year}/review"]["get"]
        val detail = api["paths"]["$base/employees/{employeeId}/tax-openings/{year}"]["get"]
        assertTrue(lookup["x-hris-company-admission"].asBoolean())
        assertTrue(review["x-hris-company-admission"].asBoolean())
        val parameters = lookup["parameters"].associateBy { it["name"].asString() }
        assertTrue(parameters.getValue("year")["required"].asBoolean())
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        assertEquals(120, parameters.getValue("query")["schema"]["maxLength"].asInt())
        val revision = detail["parameters"].first { it["name"].asString() == "revision" }
        assertEquals(999, revision["schema"]["maximum"].asInt())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf(
                "id",
                "employeeNumber",
                "name",
                "employmentVersion",
                "openingVersion",
                "openingStatus",
            ),
            schemas["PayrollTaxOpeningEmployeeResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf("year", "employee", "current", "history", "frozen", "availableActions"),
            schemas["PayrollTaxOpeningReviewResponse"]["properties"].propertyNames().toSet(),
        )
        assertFalse(schemas["PayrollTaxOpeningSummaryResponse"]["properties"].has("terms"))
    }

    @Test
    fun payrollInputReviewDocumentsCurrentSourcesIndependentActionsAndBoundedDiscovery() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/payroll"
        val lookup = api["paths"]["$base/inputs/employees"]["get"]
        val review = api["paths"]["$base/employees/{employeeId}/inputs/{month}/review"]["get"]
        assertTrue(lookup["x-hris-company-admission"].asBoolean())
        assertTrue(review["x-hris-company-admission"].asBoolean())
        val parameters = lookup["parameters"].associateBy { it["name"].asString() }
        assertTrue(parameters.getValue("month")["required"].asBoolean())
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        assertEquals(120, parameters.getValue("query")["schema"]["maxLength"].asInt())
        assertEquals(
            setOf("UNPREPARED", "DRAFT", "VERIFIED"),
            parameters
                .getValue("status")["schema"]["enum"]
                .iterator()
                .asSequence()
                .map { it.asString() }
                .toSet(),
        )
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf(
                "id",
                "employeeNumber",
                "name",
                "employmentVersion",
                "inputVersion",
                "inputStatus",
            ),
            schemas["PayrollInputEmployeeResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf(
                "earningsMonth",
                "employee",
                "source",
                "current",
                "history",
                "frozen",
                "availableActions",
            ),
            schemas["PayrollInputReviewResponse"]["properties"].propertyNames().toSet(),
        )
    }

    @Test
    fun payrollPeriodReferencesRemainMinimalAndCompanyAdmitted() {
        val api = contract()
        val lookup = api["paths"]["/api/v1/companies/{companyId}/payroll/periods/employees"]["get"]
        assertTrue(lookup["x-hris-company-admission"].asBoolean())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("employeeId", "employeeNumber", "name", "inputVersion", "inputStatus"),
            schemas["PayrollPeriodMemberResponse"]["properties"].propertyNames().toSet(),
        )
    }

    @Test
    fun compensationReviewAndEmployeeLookupKeepPrivateProfilesOutOfTheirContract() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/payroll"
        val review = api["paths"]["$base/employees/{employeeId}/compensation/review"]["get"]
        val lookup = api["paths"]["$base/compensation-employees"]["get"]
        assertTrue(review["x-hris-company-admission"].asBoolean())
        assertTrue(lookup["x-hris-company-admission"].asBoolean())
        val parameters = review["parameters"].associateBy { it["name"].asString() }
        assertTrue(parameters.getValue("asOf")["required"].asBoolean())
        assertTrue(
            parameters.keys.containsAll(
                setOf("companyId", "employeeId", "asOf", "historyAfter", "historyLimit")
            )
        )
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("asOf", "employee", "current", "effective", "history", "canChange"),
            schemas["CompensationReviewResponse"]["properties"].propertyNames().toSet(),
        )
        assertEquals(
            setOf("id", "employeeNumber", "name", "employmentVersion"),
            schemas["PayrollEmployeeResponse"]["properties"].propertyNames().toSet(),
        )
    }

    @Test
    fun payrollPolicyReviewKeepsConfigurationAndDatedSelectionSeparate() {
        val api = contract()
        val operation = api["paths"]["/api/v1/companies/{companyId}/payroll/policy/review"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters = operation["parameters"].associateBy { it["name"].asString() }
        assertTrue(parameters.getValue("asOf")["required"].asBoolean())
        assertTrue(
            parameters.keys.containsAll(setOf("companyId", "asOf", "historyAfter", "historyLimit"))
        )
        val properties = api["components"]["schemas"]["PayrollPolicyReviewResponse"]["properties"]
        assertEquals(
            setOf("asOf", "current", "effective", "history"),
            properties.properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun attendanceExceptionsHaveABoundedCompanyAdmittedMetadataContract() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/workforce/attendance/exceptions"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters = operation["parameters"].associateBy { it["name"].asString() }
        assertEquals(200, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(160, parameters.getValue("after")["schema"]["maxLength"].asInt())
        val properties = api["components"]["schemas"]["AttendanceExceptionResponse"]["properties"]
        assertEquals(
            setOf(
                "employeeId",
                "employeeNumber",
                "employeeName",
                "workDate",
                "pendingCount",
                "incomplete",
                "correctionVersion",
                "lastReceivedAt",
            ),
            properties.properties().map { it.key }.toSet(),
        )
        assertEquals(32, properties["pendingCount"]["maximum"].asInt())
    }

    @Test
    fun retirementReviewExposesDecisionMetadataWithoutContentAccess() {
        val api = contract()
        val operation =
            api["paths"][
                "/api/v1/companies/{companyId}/documents/revisions/{revisionId}/retirement"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        assertFalse(
            operation.path("parameters").any { it.path("name").asString() == "Idempotency-Key" }
        )
        val properties =
            api["components"]["schemas"]["DocumentRetirementReviewResponse"]["properties"]
        assertEquals(
            setOf(
                "revisionId",
                "revisionNumber",
                "revisionVersion",
                "status",
                "published",
                "retention",
                "reviewedAt",
                "blockingReason",
            ),
            properties.properties().map { it.key }.toSet(),
        )
        assertEquals(64, properties["blockingReason"]["maxLength"].asInt())
    }

    @Test
    fun selfDocumentDiscoveryDocumentsBoundedOwnedReferencesAndPublishedEvidence() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/documents/me"
        val record = api["paths"]["$base/evidence/{documentId}"]["get"]
        assertTrue(record["x-hris-company-admission"].asBoolean())
        assertEquals(
            setOf("id", "version", "employmentId", "classification", "evidence"),
            api["components"]["schemas"]["DocumentCatalogRecordResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        for (path in listOf("$base/employments", "$base/evidence")) {
            val operation = api["paths"][path]["get"]
            assertTrue(operation["x-hris-company-admission"].asBoolean())
            val parameters = operation["parameters"].associateBy { it["name"].asString() }
            assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
            assertEquals(1, parameters.getValue("limit")["schema"]["minimum"].asInt())
            assertEquals(20, parameters.getValue("limit")["schema"]["default"].asInt())
            assertEquals("integer", parameters.getValue("limit")["schema"]["type"].asString())
            assertFalse(parameters.containsKey("Idempotency-Key"))
            if (path.endsWith("/employments")) {
                assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
            } else {
                assertTrue(parameters.getValue("employmentId")["required"].asBoolean())
                assertEquals(
                    "PERSONAL",
                    parameters.getValue("classification")["schema"]["default"].asString(),
                )
                assertEquals(
                    setOf("PERSONAL", "RECEIPT"),
                    parameters
                        .getValue("classification")["schema"]["enum"]
                        .iterator()
                        .asSequence()
                        .map { it.asString() }
                        .toSet(),
                )
            }
        }
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            schemas["DocumentEmploymentResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("accountId", "companyId", "employmentId", "classification", "documents"),
            schemas["SelfDocumentEvidenceResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "sha256",
                "validatedAt",
            ),
            schemas["DocumentEvidenceResponse"]["properties"].properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun leavePreparationDocumentsItsBoundedReadsWithoutAnIdempotencyRequirement() {
        val api = contract()
        for (scope in listOf("requests", "me/requests")) {
            val operation =
                api["paths"]["/api/v1/companies/{companyId}/leave/$scope/preview"]["post"]
            assertTrue(operation["x-hris-company-admission"].asBoolean())
            assertFalse(
                operation.path("parameters").any { it.path("name").asString() == "Idempotency-Key" }
            )
        }
        val days = api["components"]["schemas"]["LeavePreviewRequest"]["properties"]["days"]
        assertEquals(1, days["minItems"].asInt())
        assertEquals(366, days["maxItems"].asInt())
        val response = api["components"]["schemas"]["LeaveRequestPreviewResponse"]["properties"]
        assertTrue(response.has("excludedDates"))
        assertTrue(response.has("balances"))
        assertTrue(response.has("evaluatedAt"))
        assertFalse(response.has("approval"))
    }

    @Test
    fun administrativeLeavePreparationHasBoundedCompanyMetadataRoutes() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/leave/preparation/employees"
        for (suffix in listOf("", "/{employeeId}/evidence")) {
            val operation = api["paths"][base + suffix]["get"]
            assertTrue(operation["x-hris-company-admission"].asBoolean())
            val parameters =
                operation["parameters"].iterator().asSequence().associateBy {
                    it["name"].asString()
                }
            assertEquals(1, parameters.getValue("limit")["schema"]["minimum"].asInt())
            assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
            if (suffix.isEmpty()) {
                assertEquals(120, parameters.getValue("query")["schema"]["maxLength"].asInt())
                assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
            }
        }
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("companyDate", "timezone", "employees"),
            schemas["LeavePreparationDirectoryResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            schemas["LeavePreparationEmployeeResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "validatedAt",
            ),
            schemas["LeaveEvidenceOptionResponse"]["properties"].properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun coworkerDirectoryDocumentsItsOwnBoundedPagesAndFilter() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/employees/directory"
        for (path in listOf(base, "$base/departments")) {
            val parameters =
                api["paths"][path]["get"]["parameters"].iterator().asSequence().associateBy {
                    it["name"].asString()
                }
            assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
            assertEquals(1, parameters.getValue("limit")["schema"]["minimum"].asInt())
            if (path == base) assertTrue(parameters.containsKey("departmentId"))
        }
    }

    @Test
    fun audienceReferenceSelectionsHaveExplicitBoundsAndCompanyAdmission() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/communications/audience-references"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters =
            operation["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("ids")["schema"]["maxItems"].asInt())
        assertTrue(parameters.getValue("ids")["schema"]["uniqueItems"].asBoolean())
        assertEquals(120, parameters.getValue("query")["schema"]["maxLength"].asInt())
        assertEquals(200, parameters.getValue("limit")["schema"]["maximum"].asInt())
    }

    @Test
    fun publicationReviewUsesCompanyAdmissionAndItsOwnResponseContract() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/announcements/{id}/publication-review"][
                "get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val schema = api["components"]["schemas"]["AnnouncementReviewResponse"]["properties"]
        assertTrue(schema.has("announcement"))
        assertTrue(schema.has("publicationJob"))
        assertTrue(schema.has("availableActions"))
        assertFalse(schema.has("recipients"))
    }

    @Test
    fun revisionDetailsAdvertiseBoundedCurrentActions() {
        val api = contract()
        val schema = api["components"]["schemas"]["DocumentRevisionResponse"]["properties"]
        assertTrue(schema.has("status"))
        assertTrue(schema.has("version"))
        val actions = schema["availableActions"]
        assertEquals(2, actions["maxItems"].asInt())
        assertTrue(actions["uniqueItems"].asBoolean())
        assertEquals(
            setOf("UPLOAD_CONTENT", "VALIDATE", "CANCEL_UPLOAD"),
            actions["items"]["enum"].iterator().asSequence().map { it.asString() }.toSet(),
        )
        assertTrue(schema.has("uploadContinuation"))
        val continuation =
            api["components"]["schemas"]["DocumentUploadContinuationResponse"]["properties"]
        assertEquals(
            setOf("operationId", "offset", "size", "sha256", "attempts", "retryAt"),
            continuation.propertyNames().toSet(),
        )
    }

    @Test
    fun shiftDetailsAdvertiseCompanyAdmissionAndTheirCurrentVersion() {
        val api = contract()
        val operation = api["paths"]["/api/v1/companies/{companyId}/workforce/shifts/{id}"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val schema = api["components"]["schemas"]["ShiftResponse"]["properties"]
        assertTrue(schema.has("id"))
        assertTrue(schema.has("details"))
        assertTrue(schema.has("active"))
        assertTrue(schema.has("version"))
    }

    @Test
    fun selfWorkforceDiscoveryHasItsOwnMinimalCompanyAdmittedContract() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/workforce/me/employments"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val directory = api["components"]["schemas"]["SelfWorkforceDirectoryResponse"]["properties"]
        val parameters =
            operation["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        assertTrue(directory.has("accountId"))
        assertTrue(directory.has("companyId"))
        assertTrue(directory.has("asOf"))
        assertTrue(directory.has("timezone"))
        val reference = api["components"]["schemas"]["WorkforceEmploymentResponse"]["properties"]
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            reference.properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun selfLeaveDiscoveryHasItsOwnMinimalCompanyAdmittedContract() {
        val api = contract()
        val operation = api["paths"]["/api/v1/companies/{companyId}/leave/me/employments"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val directory = api["components"]["schemas"]["SelfLeaveDirectoryResponse"]["properties"]
        val parameters =
            operation["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        assertTrue(directory.has("accountId"))
        assertTrue(directory.has("companyId"))
        assertTrue(directory.has("asOf"))
        assertTrue(directory.has("timezone"))
        val reference = api["components"]["schemas"]["LeaveEmploymentResponse"]["properties"]
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            reference.properties().map { it.key }.toSet(),
        )
        val details = api["paths"]["/api/v1/companies/{companyId}/leave/me/requests/{id}"]["get"]
        assertTrue(details["x-hris-company-admission"].asBoolean())
        val historyLimit = details["parameters"].single { it["name"].asString() == "historyLimit" }
        assertEquals(50, historyLimit["schema"]["maximum"].asInt())
        assertEquals(10, historyLimit["schema"]["default"].asInt())
    }

    @Test
    fun employeeCalendarReviewDescribesMinimalReferencesAndObservedAssignments() {
        val api = contract()
        val directory = api["paths"]["/api/v1/companies/{companyId}/workforce/employees"]["get"]
        assertTrue(directory["x-hris-company-admission"].asBoolean())
        val parameters =
            directory["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            schemas["WorkforceEmployeeResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("employee", "from", "until", "calendar", "assignmentAtStart"),
            schemas["EmployeeCalendarReviewResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("effectiveFrom", "revision", "days"),
            schemas["ScheduleAssignmentResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        val review =
            api["paths"]["/api/v1/companies/{companyId}/workforce/employees/{id}/calendar/review"][
                "get"]
        assertTrue(review["x-hris-company-admission"].asBoolean())
    }

    @Test
    fun closingOverviewDescribesMinimalProgressAndBoundedPublishedEmployees() {
        val api = contract()
        val path = "/api/v1/companies/{companyId}/workforce/periods/{month}"
        assertTrue(api["paths"][path]["get"]["x-hris-company-admission"].asBoolean())
        val directory = api["paths"]["$path/employees"]["get"]
        assertTrue(directory["x-hris-company-admission"].asBoolean())
        val parameters = directory["parameters"].associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf(
                "month",
                "companyDate",
                "companyTimezone",
                "period",
                "attempt",
                "availableActions",
            ),
            schemas["WorkPeriodOverviewResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf(
                "id",
                "status",
                "totalEmployees",
                "completedEmployees",
                "cancellationRequested",
                "failureCode",
                "createdAt",
                "finishedAt",
            ),
            schemas["WorkPeriodAttemptResponse"]["properties"].properties().map { it.key }.toSet(),
        )
    }

    private fun checkReferences(root: JsonNode, node: JsonNode, depth: Int = 0) {
        assertTrue(depth < 64)
        if (node.isObject) {
            node["\$ref"]?.let {
                val value = it.asString()
                assertTrue(
                    value.startsWith("#/"),
                    "Only local schema references are allowed: $value",
                )
                assertFalse(root.at(value.removePrefix("#")).isMissingNode, value)
            }
        }
        if (node.isObject || node.isArray)
            node.iterator().asSequence().forEach { checkReferences(root, it, depth + 1) }
    }

    @Test
    fun generatedContractCoversRegisteredRoutesAndHasValidLocalReferences() {
        val document = contract()
        assertTrue(document["openapi"].asString().startsWith("3.1."))
        assertEquals("/", document["servers"][0]["url"].asString())
        checkReferences(document, document)
        val options =
            ParseOptions().apply {
                setResolve(true)
                setResolveFully(false)
            }
        val validation = OpenAPIV3Parser().readContents(document.toString(), null, options)
        assertNotNull(validation.openAPI)
        assertTrue(validation.messages.isNullOrEmpty(), validation.messages.toString())
        val paths = document["paths"]
        val methods = setOf("get", "post", "put", "patch", "delete", "head", "options")
        val operationIds = mutableSetOf<String>()
        val documentedRoutes = mutableSetOf<Pair<String, String>>()
        for ((path, item) in paths.properties()) {
            assertTrue(path.startsWith("/api/v1/"), path)
            for ((method, operation) in item.properties()) {
                if (method !in methods) continue
                documentedRoutes.add(method to path)
                assertTrue(operationIds.add(operation["operationId"].asString()), "$method $path")
                val expected = Regex("\\{([^}]+)}").findAll(path).map { it.groupValues[1] }.toSet()
                val parameters = operation["parameters"].iterator().asSequence().toList()
                assertEquals(
                    expected,
                    parameters
                        .filter { it["in"].asString() == "path" }
                        .map { it["name"].asString() }
                        .toSet(),
                    path,
                )
                assertTrue(
                    parameters
                        .filter { it["in"].asString() == "path" }
                        .all { it["required"].asBoolean() },
                    path,
                )
                assertFalse(
                    parameters.any {
                        it["name"].asString() in
                            setOf(
                                "actor",
                                "credentialVersion",
                                "authenticatedAt",
                                "mfaVerifiedAt",
                                "token",
                            )
                    },
                    path,
                )
                assertEquals(
                    parameters.size,
                    parameters.map { it["in"].asString() to it["name"].asString() }.distinct().size,
                    path,
                )
                assertEquals(
                    "#/components/schemas/ApiProblem",
                    operation["responses"]["422"]["content"]["application/problem+json"]["schema"][
                            "\$ref"]
                        .asString(),
                )
            }
        }
        val registeredRoutes =
            mutableSetOf("post" to "/api/v1/auth/login", "post" to "/api/v1/auth/logout")
        for ((mapping, _) in mappings.handlerMethods) {
            for (path in mapping.patternValues) {
                if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/openapi")) continue
                for (method in mapping.methodsCondition.methods) {
                    registeredRoutes.add(method.name.lowercase() to path)
                    assertNotNull(
                        paths[path]?.get(method.name.lowercase()),
                        "Missing $method $path",
                    )
                }
            }
        }
        assertEquals(registeredRoutes, documentedRoutes)
        assertNotNull(paths["/api/v1/auth/login"]["post"])
        assertNotNull(paths["/api/v1/auth/logout"]["post"])
        assertNull(document["components"]["schemas"]["Actor"])
        assertNull(document["components"]["schemas"]["CsrfToken"])
        val output = Path.of("build/generated/openapi/hris-v1.json")
        Files.createDirectories(output.parent)
        Files.writeString(
            output,
            json.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
        )
    }

    @Test
    fun mobileTransportDescribesTokensIdempotencySyncAndActualWireTypes() {
        val document = contract()
        val schemas = document["components"]["schemas"]
        val paths = document["paths"]
        val bootstrap = paths["/api/v1/companies/{companyId}/sync/bootstrap"]["get"]
        val choices =
            bootstrap["security"]
                .iterator()
                .asSequence()
                .map { it.properties().map { entry -> entry.key }.toSet() }
                .toSet()
        assertEquals(setOf(setOf("nativeBearer"), setOf("cookieSession")), choices)
        assertEquals(
            "COOKIE",
            paths["/api/v1/auth/native/exchange"]["post"]["x-hris-session-transport"].asString(),
        )
        assertEquals(0, paths["/api/v1/auth/native/refresh"]["post"]["security"].size())
        assertEquals(0, paths["/api/v1/auth/native/login"]["post"]["security"].size())
        assertFalse(
            paths["/api/v1/auth/native/login"]["post"]["x-hris-authentication-required"].asBoolean()
        )
        assertTrue(paths["/api/v1/auth/native/login"]["post"]["x-hris-idempotency-key"].asBoolean())
        assertEquals(
            "NATIVE",
            paths["/api/v1/auth/native/mfa/enrollment"]["post"]["x-hris-session-transport"]
                .asString(),
        )
        assertTrue(
            paths["/api/v1/auth/native/refresh"]["post"]["x-hris-idempotency-key"].asBoolean()
        )
        val selection =
            bootstrap["parameters"].iterator().asSequence().first {
                it["name"].asString() == "collections"
            }
        assertEquals("form", selection["style"].asString())
        assertFalse(selection["explode"].asBoolean())
        val company =
            bootstrap["parameters"].iterator().asSequence().first {
                it["name"].asString() == "companyId"
            }
        assertEquals("uuid", company["schema"]["format"].asString())
        val mutation = paths["/api/v1/companies/{companyId}/employees"]["post"]
        assertTrue(mutation["x-hris-company-admission"].asBoolean())
        assertEquals(
            setOf("X-HRIS-Client-Platform", "X-HRIS-Client-Build"),
            mutation["parameters"]
                .iterator()
                .asSequence()
                .map { it["name"].asString() }
                .filter { it.startsWith("X-HRIS-Client-") }
                .toSet(),
        )
        assertFalse(
            paths["/api/v1/companies/{companyId}/client-policy"]["get"]["x-hris-company-admission"]
                .asBoolean()
        )
        val groupReport = paths["/api/v1/reports/headcount"]["get"]
        assertTrue(groupReport["x-hris-company-admission"].asBoolean())
        assertTrue(groupReport["x-hris-group-admission"].asBoolean())
        val groupParameters =
            groupReport["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals(
            setOf(
                "companies",
                "asOf",
                "X-HRIS-Client-Platform",
                "X-HRIS-Client-Build",
                "X-Request-ID",
            ),
            groupParameters.keys,
        )
        val selectedCompanies = groupParameters.getValue("companies")
        assertEquals(1, selectedCompanies["schema"]["minItems"].asInt())
        assertEquals(32, selectedCompanies["schema"]["maxItems"].asInt())
        assertTrue(selectedCompanies["schema"]["uniqueItems"].asBoolean())
        assertEquals("uuid", selectedCompanies["schema"]["items"]["format"].asString())
        assertFalse(selectedCompanies["explode"].asBoolean())
        val audit = paths["/api/v1/companies/{companyId}/audit-events"]["get"]
        val settingsSchema =
            document["components"]["schemas"]["CompanyClientPolicySettingsResponse"]
        assertEquals(
            setOf("latest", "effective"),
            settingsSchema["properties"].properties().map { it.key }.toSet(),
        )
        assertFalse(audit["x-hris-company-admission"].asBoolean())
        val auditParameters =
            audit["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals("uuid", auditParameters.getValue("cursor")["schema"]["format"].asString())
        assertEquals(200, auditParameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(
            setOf(
                "id",
                "companyId",
                "actorId",
                "resourceType",
                "resourceId",
                "action",
                "correlationId",
                "recordedAt",
            ),
            schemas["AuditEventResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertTrue(mutation["x-hris-idempotency-key"].asBoolean())
        val permissions =
            mutation["security"]
                .iterator()
                .asSequence()
                .map { it.properties().map { entry -> entry.key }.toSet() }
                .toSet()
        assertEquals(setOf(setOf("nativeBearer"), setOf("cookieSession", "csrfToken")), permissions)
        val amount = schemas["PayrollPaymentItemResponse"]["properties"]["amount"]
        assertTrue(
            amount["type"].asString() == "string" ||
                amount["type"].iterator().asSequence().any { it.asString() == "string" }
        )
        val month = schemas["PayrollPaymentItemResponse"]["properties"]["taxMonth"]
        assertEquals("2026-10", month["example"].asString())
        val cursor = schemas["SyncBootstrapResponse"]["properties"]["nextCursor"]
        assertTrue(
            cursor["type"].isArray &&
                cursor["type"].iterator().asSequence().any { it.asString() == "null" },
            cursor.toString(),
        )
        assertNull(schemas["ApiProblem"]["properties"]["code"]["enum"])
        assertEquals(
            LoginRequest::class.primaryConstructor!!.parameters.mapNotNull { it.name }.toSet(),
            schemas["PasswordSignInRequest"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("token", "headerName"),
            schemas["CsrfResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        assertEquals(
            setOf("companyId", "permissions"),
            schemas["CompanyAccessResponse"]["properties"].properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun negotiatedDownloadsAndRawUploadsAreNotDocumentedAsJsonDtos() {
        val document = contract()
        val paths = document["paths"]
        val payslip = paths["/api/v1/companies/{companyId}/payroll/payslips/{id}/pdf"]["get"]
        assertEquals(
            "binary",
            payslip["responses"]["200"]["content"]["application/pdf"]["schema"]["format"].asString(),
        )
        assertNotNull(payslip["responses"]["403"]["content"]["application/problem+json"])
        assertEquals(
            "binary",
            payslip["responses"]["206"]["content"]["application/pdf"]["schema"]["format"].asString(),
        )
        assertNull(payslip["responses"]["304"]["content"])
        val download =
            paths["/api/v1/companies/{companyId}/documents/revisions/{revisionId}/content"]["get"]
        for (status in listOf("200", "206")) {
            assertEquals(
                "binary",
                download["responses"][status]["content"]["application/octet-stream"]["schema"][
                        "format"]
                    .asString(),
            )
        }
        assertNull(download["responses"]["304"]["content"])
        assertNull(download["responses"]["416"]["content"])
        assertTrue(
            download["parameters"].iterator().asSequence().any {
                it["name"].asString() == "If-Range"
            }
        )
        val upload =
            paths["/api/v1/companies/{companyId}/documents/revisions/{revisionId}/chunks"]["post"]
        assertEquals(
            "binary",
            upload["requestBody"]["content"]["application/octet-stream"]["schema"]["format"]
                .asString(),
        )
        for (module in listOf("expenses", "payroll")) {
            val csv = paths["/api/v1/companies/{companyId}/$module/payments/{id}/export"]["get"]
            assertNotNull(csv["responses"]["200"]["content"]["text/csv"])
            assertNull(csv["responses"]["200"]["content"]["application/json"])
        }
    }

    @Test
    fun expenseCostCentersExposeOnlyBoundedEditingReferences() {
        val api = contract()
        val operation = api["paths"]["/api/v1/companies/{companyId}/expenses/cost-centers"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters = operation["parameters"].associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(120, parameters.getValue("query")["schema"]["maxLength"].asInt())
        assertEquals(44, parameters.getValue("after")["schema"]["maxLength"].asInt())
        val reference =
            api["components"]["schemas"]["ExpenseCostCenterSnapshotResponse"]["properties"]
        assertEquals(
            setOf("id", "code", "name", "version"),
            reference.properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun expenseDraftReferenceLookupBoundsEverySetAndExposesOnlyDisplayMetadata() {
        val api = contract()
        val operation =
            api["paths"][
                "/api/v1/companies/{companyId}/expenses/me/employments/{employmentId}/draft-references"][
                "get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters = operation["parameters"].associateBy { it["name"].asString() }
        for ((name, maximum) in
            listOf("categoryIds" to 20, "costCenterIds" to 20, "receiptRevisionIds" to 60)) {
            val parameter = parameters.getValue(name)
            assertFalse(parameter["required"].asBoolean())
            assertEquals(maximum, parameter["schema"]["maxItems"].asInt())
            assertTrue(parameter["schema"]["uniqueItems"].asBoolean())
            assertEquals("uuid", parameter["schema"]["items"]["format"].asString())
        }
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("employmentId", "categories", "costCenters", "receipts"),
            schemas["SelfExpenseDraftReferencesResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("id", "code", "name", "active"),
            schemas["ExpenseDraftLabelReferenceResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("revisionId", "documentId", "fileName", "mediaType", "size", "status"),
            schemas["ExpenseDraftReceiptReferenceResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
    }

    @Test
    fun administrativeExpenseDraftingHasBoundedCompanyAdmittedMetadataRoutes() {
        val api = contract()
        val base = "/api/v1/companies/{companyId}/expenses/drafting/employees"
        for (suffix in
            listOf("", "/{employmentId}/receipts", "/{employmentId}/references")) assertTrue(
            api["paths"][base + suffix]["get"]["x-hris-company-admission"].asBoolean()
        )
        val directory =
            api["paths"][base]["get"]["parameters"].associateBy { it["name"].asString() }
        assertEquals(50, directory.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(120, directory.getValue("query")["schema"]["maxLength"].asInt())
        assertEquals(32, directory.getValue("after")["schema"]["maxLength"].asInt())
        val receipts =
            api["paths"]["$base/{employmentId}/receipts"]["get"]["parameters"].associateBy {
                it["name"].asString()
            }
        assertEquals(50, receipts.getValue("limit")["schema"]["maximum"].asInt())
        val references =
            api["paths"]["$base/{employmentId}/references"]["get"]["parameters"].associateBy {
                it["name"].asString()
            }
        for ((name, maximum) in
            listOf("categoryIds" to 20, "costCenterIds" to 20, "receiptRevisionIds" to 60)) {
            assertEquals(maximum, references.getValue(name)["schema"]["maxItems"].asInt())
            assertTrue(references.getValue(name)["schema"]["uniqueItems"].asBoolean())
        }
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("companyDate", "timezone", "employees"),
            schemas["ExpenseDraftDirectoryResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("id", "employeeNumber", "legalName", "status", "version"),
            schemas["ExpenseDraftEmployeeResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf(
                "documentId",
                "revisionId",
                "revisionNumber",
                "title",
                "fileName",
                "mediaType",
                "size",
                "validatedAt",
            ),
            schemas["ExpenseDraftReceiptOptionResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
        assertEquals(
            setOf("employmentId", "categories", "costCenters", "receipts"),
            schemas["ExpenseDraftReferencesResponse"]["properties"]
                .properties()
                .map { it.key }
                .toSet(),
        )
    }

    @Test
    fun selfExpenseDiscoveryHasItsOwnMinimalCompanyAdmittedContract() {
        val api = contract()
        val operation = api["paths"]["/api/v1/companies/{companyId}/expenses/me/employments"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters = operation["parameters"].associateBy { it["name"].asString() }
        assertEquals(50, parameters.getValue("limit")["schema"]["maximum"].asInt())
        assertEquals(32, parameters.getValue("after")["schema"]["maxLength"].asInt())
        val directory = api["components"]["schemas"]["SelfExpenseDirectoryResponse"]["properties"]
        assertEquals(
            setOf("accountId", "companyId", "asOf", "timezone", "employments"),
            directory.properties().map { it.key }.toSet(),
        )
        val reference = api["components"]["schemas"]["ExpenseEmploymentResponse"]["properties"]
        assertEquals(
            setOf("id", "employeeNumber", "status", "version"),
            reference.properties().map { it.key }.toSet(),
        )
    }

    @Test
    fun expenseRecordsDefaultToSelfAndExcludeLiveReviewAffordances() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/expenses/claims/{id}/record"]["get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val parameters =
            operation["parameters"].iterator().asSequence().associateBy { it["name"].asString() }
        assertEquals("SELF", parameters.getValue("scope")["schema"]["default"].asString())
        assertEquals(1, parameters.getValue("historyLimit")["schema"]["minimum"].asInt())
        assertEquals(20, parameters.getValue("historyLimit")["schema"]["maximum"].asInt())
        val schemas = api["components"]["schemas"]
        assertEquals(
            setOf("claim", "submission", "history"),
            schemas["ExpenseClaimRecordResponse"]["properties"].properties().map { it.key }.toSet(),
        )
        val submitted = schemas["ExpenseSubmissionRecordResponse"]["properties"]
        for (name in
            listOf("duplicateReceiptDigests", "canReadClaim", "availableActions")) assertFalse(
            submitted.has(name)
        )
        for (name in
            listOf("lines", "approval", "reviews", "claimVersion", "claimStatus")) assertTrue(
            submitted.has(name)
        )
    }

    @Test
    fun expensePaymentProgressDefaultsToOwnedReadsWithABoundedVersionedResponse() {
        val api = contract()
        val operation =
            api["paths"]["/api/v1/companies/{companyId}/expenses/claims/{id}/payment-progress"][
                "get"]
        assertTrue(operation["x-hris-company-admission"].asBoolean())
        val scope = operation["parameters"].single { it["name"].asString() == "scope" }
        assertEquals("SELF", scope["schema"]["default"].asString())
        val progress = api["components"]["schemas"]["ExpenseClaimPaymentsResponse"]["properties"]
        assertEquals(
            setOf("claimId", "version", "attempts"),
            progress.properties().map { it.key }.toSet(),
        )
        assertEquals(0, progress["version"]["minimum"].asInt())
        assertEquals(60, progress["version"]["maximum"].asInt())
        assertEquals(20, progress["attempts"]["maxItems"].asInt())
    }

    @Test
    fun expenseReadScopesAreExplicitAndPreserveTheAuthorizedDefault() {
        val paths = contract()["paths"]
        for (suffix in
            listOf(
                "/claims",
                "/claims/{id}",
                "/claims/{id}/drafts",
                "/claims/{id}/history",
                "/claims/{id}/submissions",
                "/claims/{id}/payments",
                "/submissions/{id}",
                "/submissions/{submissionId}/receipts/{revisionId}/content",
            )) {
            val operation = paths["/api/v1/companies/{companyId}/expenses$suffix"]["get"]
            val scope = operation["parameters"].single { it["name"].asString() == "scope" }
            assertEquals("query", scope["in"].asString())
            assertFalse(scope["required"].asBoolean())
            assertEquals("AUTHORIZED", scope["schema"]["default"].asString())
            assertEquals(
                setOf("AUTHORIZED", "SELF"),
                scope["schema"]["enum"].iterator().asSequence().map { it.asString() }.toSet(),
            )
            assertTrue(operation["x-hris-company-admission"].asBoolean())
        }
    }

    @Test
    fun payslipReadScopesAreExplicitAndPreserveTheAuthorizedDefault() {
        val paths = contract()["paths"]
        val search =
            paths["/api/v1/companies/{companyId}/payroll/payslips"]["get"]["parameters"].single {
                it["name"].asString() == "query"
            }
        assertEquals("query", search["in"].asString())
        assertFalse(search["required"].asBoolean())
        assertEquals("string", search["schema"]["type"].asString())
        for (suffix in listOf("", "/{id}", "/{id}/payments", "/{id}/pdf")) {
            val operation = paths["/api/v1/companies/{companyId}/payroll/payslips$suffix"]["get"]
            val scope = operation["parameters"].single { it["name"].asString() == "scope" }
            assertEquals("query", scope["in"].asString())
            assertFalse(scope["required"].asBoolean())
            assertEquals("AUTHORIZED", scope["schema"]["default"].asString())
            assertEquals(
                setOf("AUTHORIZED", "SELF"),
                scope["schema"]["enum"].iterator().asSequence().map { it.asString() }.toSet(),
            )
            assertTrue(operation["x-hris-company-admission"].asBoolean())
        }
    }

    @Test
    fun documentationRequiresACurrentAuthenticatedAccount() {
        val browser = client()
        assertEquals(401, get(browser, "/api/v1/openapi").statusCode())
        val account = java.util.UUID.randomUUID()
        database()
            .update(
                "insert into accounts(id,email,display_name,password_hash) select ?,?,'Documentation fixture',password_hash from accounts where email='admin@example.test'",
                account,
                "$account@example.test",
            )
        login(browser, "$account@example.test")
        assertEquals(200, get(browser, "/api/v1/openapi").statusCode())
        database()
            .update("update accounts set security_version=security_version+1 where id=?", account)
        assertEquals(401, get(browser, "/api/v1/openapi").statusCode())
    }

    @Test
    fun languageHeadersRetainOneCanonicalDescription() {
        val browser = client()
        login(browser)
        val original = get(browser, "/api/v1/openapi")
        assertEquals(200, original.statusCode(), original.body().take(500))
        for (language in listOf("id-ID", "en-GB", "fr-FR")) {
            val response =
                browser.send(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/v1/openapi"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Accept-Language", language)
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            assertEquals(200, response.statusCode())
            assertEquals(original.body(), response.body())
        }
    }
}
