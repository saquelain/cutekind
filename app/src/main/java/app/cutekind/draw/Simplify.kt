package app.cutekind.draw

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ramer-Douglas-Peucker: drops points that sit (almost) on the line between their neighbours.
 * A finger stroke keeps its shape with a fraction of the points, so drawings send in fewer messages.
 */
fun simplify(p: List<Int>, epsilon: Float = 3f): List<Int> {
    val n = p.size / 2
    if (n <= 2) return p
    val keep = BooleanArray(n).also { it[0] = true; it[n - 1] = true }
    val stack = ArrayDeque<Pair<Int, Int>>().apply { add(0 to n - 1) }
    while (stack.isNotEmpty()) {
        val (a, b) = stack.removeLast()
        val ax = p[2 * a].toFloat(); val ay = p[2 * a + 1].toFloat()
        val dx = p[2 * b] - ax; val dy = p[2 * b + 1] - ay
        val len = hypot(dx, dy)
        var worst = -1; var far = epsilon
        for (i in a + 1 until b) {
            val px = p[2 * i] - ax; val py = p[2 * i + 1] - ay
            val d = if (len == 0f) hypot(px, py) else abs(dx * py - dy * px) / len
            if (d > far) { far = d; worst = i }
        }
        if (worst >= 0) { keep[worst] = true; stack.add(a to worst); stack.add(worst to b) }
    }
    return buildList { for (i in 0 until n) if (keep[i]) { add(p[2 * i]); add(p[2 * i + 1]) } }
}
