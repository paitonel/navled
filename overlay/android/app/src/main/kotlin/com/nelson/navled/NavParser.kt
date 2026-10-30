package com.nelson.navled

object NavParser {
    private val distRe = Regex("""(\d+(?:[.,]\d+)?)\s*(km|m|mi|ft|yd)\b""")

    // Returns Pair(direction, metres) or null if no turn found
    fun parse(raw: String): Pair<Int, Int>? {
        val t = raw.lowercase()
        val dir = when {
            t.contains("u-turn") || t.contains("u turn") -> 4
            t.contains("left") -> 1
            t.contains("right") -> 2
            t.contains("straight") || t.contains("continue") -> 3
            else -> return null
        }
        val m = distRe.find(t)
        val metres = if (m == null) 500 else {
            val v = m.groupValues[1].replace(',', '.').toDouble()
            when (m.groupValues[2]) {
                "km" -> v * 1000
                "mi" -> v * 1609.34
                "ft" -> v * 0.3048
                "yd" -> v * 0.9144
                else -> v
            }.toInt()
        }
        return Pair(dir, metres)
    }
}
