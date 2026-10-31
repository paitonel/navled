package com.nelson.navled

object NavParser {
    // South Africa drives on the left: roundabout exit 1 = left, 2 = straight, 3+ = right.
    // Set to false for right-hand traffic countries.
    const val LEFT_HAND_TRAFFIC = true

    private val distRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""")
    private val hazardWords = listOf(
        "speed camera", "red light camera", "traffic camera", "police",
        "accident", "crash", "hazard", "road closed"
    )

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

    // Modes: 1 left, 2 right, 3 straight, 4 u-turn, 5/6/7 roundabout left/straight/right,
    // 8 arrived, 9 faster route, 10 hazard. Returns Pair(mode, metres) or null.
    fun parse(raw: String): Pair<Int, Int>? {
        val t = raw.lowercase()
        val metres = distance(t)

        if (t.contains("arrived")) return Pair(8, 0)
        if (t.contains("faster route")) return Pair(9, 0)
        if (hazardWords.any { t.contains(it) }) return Pair(10, metres ?: 0)

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
            return Pair(mode, metres ?: 1500)
        }

        val dir = when {
            t.contains("u-turn") || t.contains("u turn") -> 4
            t.contains("left") -> 1
            t.contains("right") -> 2
            t.contains("straight") || t.contains("continue") -> 3
            else -> return null
        }
        return Pair(dir, metres ?: 1500)
    }
}
