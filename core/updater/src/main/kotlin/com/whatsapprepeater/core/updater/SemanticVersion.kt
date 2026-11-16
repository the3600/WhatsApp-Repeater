package com.whatsapprepeater.core.updater

/**
 * Representación de versión semántica MAJOR.MINOR.PATCH (ej: v1.2.0 -> 1.2.0).
 */
data class SemanticVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
) : Comparable<SemanticVersion> {

  override fun compareTo(other: SemanticVersion): Int {
    if (major != other.major) return major.compareTo(other.major)
    if (minor != other.minor) return minor.compareTo(other.minor)
    return patch.compareTo(other.patch)
  }

  override fun toString(): String = "$major.$minor.$patch"

  companion object {
    fun parseOrNull(raw: String): SemanticVersion? {
      val clean = raw.trim().removePrefix("v").removePrefix("V").substringBefore("-")
      val parts = clean.split('.')
      if (parts.isEmpty() || parts.size > 3) return null
      val major = parts.getOrNull(0)?.toIntOrNull() ?: return null
      val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
      val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
      if (major < 0 || minor < 0 || patch < 0) return null
      return SemanticVersion(major, minor, patch)
    }
  }
}

