package jp.local.kakeibo.update

private val VERSION_PATTERN = Regex("^v?(\\d+(?:\\.\\d+)*)$")

internal fun isVersionNewer(candidate: String, current: String): Boolean {
    val candidateParts = parseVersion(candidate) ?: return false
    val currentParts = parseVersion(current) ?: return false
    val size = maxOf(candidateParts.size, currentParts.size)

    for (index in 0 until size) {
        val candidatePart = candidateParts.getOrElse(index) { 0L }
        val currentPart = currentParts.getOrElse(index) { 0L }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}

private fun parseVersion(value: String): List<Long>? {
    val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
    return match.groupValues[1]
        .split('.')
        .map { it.toLongOrNull() ?: return null }
}
