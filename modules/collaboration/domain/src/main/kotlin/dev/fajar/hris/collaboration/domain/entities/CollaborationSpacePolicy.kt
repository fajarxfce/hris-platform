package dev.fajar.hris.collaboration.domain.entities

/** Organization chat availability is independent of the currently selected HR company. */
data class CollaborationSpacePolicy(
    val enabled: Boolean = false,
    val minimumAndroidBuild: Int = 0,
    val minimumIosBuild: Int = 0,
) {
    init {
        require(minimumAndroidBuild in 0..999999999 && minimumIosBuild in 0..999999999)
    }
}
