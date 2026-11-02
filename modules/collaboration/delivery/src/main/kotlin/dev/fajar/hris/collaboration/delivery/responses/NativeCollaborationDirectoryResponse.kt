package dev.fajar.hris.collaboration.delivery.responses

import dev.fajar.hris.collaboration.domain.entities.CollaborationNativeDevicePage

data class NativeCollaborationDirectoryResponse(
    val accountId: String,
    val nativeSessionId: String,
    val devices: List<Device>,
    val nextAfterCompanyId: String?,
) {
    constructor(
        page: CollaborationNativeDevicePage
    ) : this(
        page.accountId.toString(),
        page.nativeSessionId.toString(),
        page.devices.map {
            Device(
                it.companyId.toString(),
                it.deviceId.toString(),
                it.version,
                it.state.name,
                it.platform.name,
            )
        },
        page.nextAfterCompanyId?.toString(),
    )

    data class Device(
        val companyId: String,
        val deviceId: String,
        val version: Long,
        val state: String,
        val platform: String,
    )
}
