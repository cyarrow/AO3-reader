package io.github.cyarrow.ao3reader.update

/** Semantic version (major.minor.patch); suffixes such as "-debug" are ignored. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor, Version::patch)

    override fun toString() = "$major.$minor.$patch"

    companion object {
        fun parse(raw: String): Version? {
            val core = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            val parts = core.split('.')
            if (parts.isEmpty() || parts.size > 3) return null
            val nums = parts.map { it.toIntOrNull() ?: return null }
            return Version(nums[0], nums.getOrElse(1) { 0 }, nums.getOrElse(2) { 0 })
        }
    }
}
