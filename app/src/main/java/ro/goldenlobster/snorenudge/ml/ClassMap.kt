package ro.goldenlobster.snorenudge.ml

import java.io.InputStream

/** YAMNet class map: index -> display name, parsed from yamnet_class_map.csv (index,mid,display_name). */
class ClassMap(val names: List<String>) {
    private val byName: Map<String, Int> =
        HashMap<String, Int>().also { m -> names.forEachIndexed { i, n -> m.putIfAbsent(n, i) } }

    val size: Int get() = names.size

    fun indexOf(displayName: String): Int = byName[displayName] ?: -1

    companion object {
        fun parse(stream: InputStream): ClassMap {
            val rows = ArrayList<Pair<Int, String>>()
            stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                var first = true
                for (line in lines) {
                    if (line.isBlank()) continue
                    if (first) {
                        first = false
                        if (line.startsWith("index")) continue
                    }
                    val f = splitCsv(line)
                    if (f.size < 3) continue
                    val idx = f[0].trim().toIntOrNull() ?: continue
                    rows.add(idx to f[2])
                }
            }
            val max = rows.maxOfOrNull { it.first } ?: -1
            val arr = Array(max + 1) { "" }
            for ((i, n) in rows) arr[i] = n
            return ClassMap(arr.toList())
        }

        /** Minimal RFC 4180 line splitter (quotes, doubled quotes). */
        internal fun splitCsv(line: String): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        sb.append('"'); i++
                    }
                    c == '"' -> inQuotes = !inQuotes
                    c == ',' && !inQuotes -> {
                        out.add(sb.toString()); sb.setLength(0)
                    }
                    else -> sb.append(c)
                }
                i++
            }
            out.add(sb.toString())
            return out
        }
    }
}
