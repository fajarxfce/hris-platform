package dev.fajar.hris.people.data.models

val requiredEmployeeCsvColumns =
    setOf("employee_number", "legal_name", "nationality", "start_date", "contract")
val employeeCsvColumns =
    listOf(
        "employee_number",
        "legal_name",
        "nationality",
        "start_date",
        "contract",
        "end_date",
        "email",
        "birth_date",
        "manager_id",
        "branch_id",
        "department_id",
        "position_id",
        "cost_center_id",
    )
