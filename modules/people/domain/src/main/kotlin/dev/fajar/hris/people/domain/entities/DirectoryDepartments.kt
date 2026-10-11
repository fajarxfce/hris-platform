package dev.fajar.hris.people.domain.entities

/**
 * A bounded lookup. More matches require a narrower search rather than loading an organization
 * tree.
 */
data class DirectoryDepartments(val items: List<DirectoryDepartment>, val hasMore: Boolean)
