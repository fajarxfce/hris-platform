package dev.fajar.hris

abstract class DocumentEmployeesApiFixture : SelfDocumentApiFixture() {
    protected fun directoryFixture(): SelfFixture {
        val (administrator, reader) = selfFixture("documents.read")
        database()
            .update(
                "insert into membership_permissions(company_id,account_id,permission) values(?,?,'people.profile.read')",
                reader.company,
                reader.actor.accountId,
            )
        return SelfFixture(
            administrator,
            reader.copy(
                actor =
                    reader.actor.copy(permissions = setOf("documents.read", "people.profile.read"))
            ),
        )
    }
}
