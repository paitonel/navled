package com.nelson.navled

// mode: 1 left, 2 right, 3 straight, 4 u-turn, 5/6/7 roundabout left/straight/right,
// 8 arrived, 9 faster route, 10 hazard, 13/14 keep left/right, 15/16 merge left/right.
// exit: roundabout exit number (0 = unknown).
// warn: 1 police, 2 camera, 3 pothole, 4 other, 5 accident, 6 roadworks, 7 traffic.
// variant: 0 normal, 1 slight, 2 sharp.
data class NavInfo(
    val mode: Int, val dist: Int,
    val exit: Int = 0, val warn: Int = 0, val variant: Int = 0
)

object NavParser {
    // South Africa drives on the left: roundabout exit 1 = left, 2 = straight, 3+ = right.
    const val LEFT_HAND_TRAFFIC = true

    private val distRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""")

    private val policeWords = listOf("police")
    private val cameraWords = listOf(
        "speed camera", "red light camera", "traffic camera", "mobile camera",
        "speed trap", "average speed"
    )
    private val potholeWords = listOf("pothole")
    private val accidentWords = listOf("accident", "crash", "collision")
    private val roadworksWords = listOf("roadwork", "road work", "construction", "road works")
    // "traffic" alone is NOT used: Maps writes things like "Heavy traffic" in every update.
    private val trafficWords = listOf("traffic jam", "congestion", "standstill traffic")
    private val otherHazardWords = listOf("hazard", "road closed", "object on road")

    private fun distance(t: String): Int? {
        val m = distRe.find(t) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDouble()
        return when (m.groupValues[2]) {
            "km" -> v * 1000
            "mi" -> v * 1609.34
            "ft" -> v * 0.3048
            "yd" -> v * 0.9144
            else -> v
        }.toInt()
    }

    private fun exitNumber(t: String): Int? {
        Regex("""(\d+)\s*(?:st|nd|rd|th)\s+exit""").find(t)?.let { return it.groupValues[1].toInt() }
        val words = mapOf("first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5)
        for ((w, n) in words) if (Regex("""\b$w\s+exit""").containsMatchIn(t)) return n
        return null
    }

    private fun warningType(t: String): Int = when {
        policeWords.any { t.contains(it) } -> 1
        cameraWords.any { t.contains(it) } -> 2
        potholeWords.any { t.contains(it) } -> 3
        accidentWords.any { t.contains(it) } -> 5
        roadworksWords.any { t.contains(it) } -> 6
        trafficWords.any { t.contains(it) } -> 7
        otherHazardWords.any { t.contains(it) } -> 4
        else -> 0
    }

    fun parse(raw: String): NavInfo? {
        val t = raw.lowercase()
        val metres = distance(t)

        if (t.contains("arrived")) return NavInfo(8, 0)
        if (t.contains("faster route")) return NavInfo(9, 0)

        val warn = warningType(t)
        if (warn != 0) return NavInfo(10, metres ?: 0, 0, warn)

        if (t.contains("roundabout") || t.contains("traffic circle")) {
            val n = exitNumber(t)
            val mode = when {
                n != null -> when {
                    n == 2 -> 6
                    n == 1 -> if (LEFT_HAND_TRAFFIC) 5 else 7
                    else -> if (LEFT_HAND_TRAFFIC) 7 else 5
                }
                t.contains("left") -> 5
                t.contains("right") -> 7
                else -> 6
            }
            return NavInfo(mode, metres ?: 1500, n ?: 0, 0)
        }

        val d = metres ?: 1500
        val variant = when {
            t.contains("slight") || t.contains("bear ") -> 1
            t.contains("sharp") -> 2
            else -> 0
        }

        return when {
            t.contains("u-turn") || t.contains("u turn") -> NavInfo(4, d)
            t.contains("keep left") -> NavInfo(13, d)
            t.contains("keep right") -> NavInfo(14, d)
            t.contains("merge") && t.contains("left") -> NavInfo(15, d)
            t.contains("merge") && t.contains("right") -> NavInfo(16, d)
            t.contains("left") -> NavInfo(1, d, 0, 0, variant)
            t.contains("right") -> NavInfo(2, d, 0, 0, variant)
            t.contains("straight") || t.contains("continue") || t.contains("merge") -> NavInfo(3, d)
            else -> null
        }
    }
}

