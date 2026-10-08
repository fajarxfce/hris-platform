package dev.fajar.hris.identity.domain.entities

/** Application limits are independent of storage/window implementation. */
data class SignInAttemptPolicy(
    val windowSeconds: Long = 900,
    val perOrigin: Int = 100,
    val perAccount: Int = 10,
) {
    init {
        require(windowSeconds in 1..86400 && perOrigin in 1..100000 && perAccount in 1..100000)
    }
}
