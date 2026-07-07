package io.github.toolicious.labler.printer.dither

/**
 * Binary morphology used to tidy an outline: set pixels are grouped into connected components
 * (8-connectivity) and the tiny isolated ones are dropped, so a speckly result becomes calmer while
 * connected lines are kept. Applied as an optional post-step on top of any outline method.
 */
object Morphology {

    /** Connected components smaller than this many pixels are treated as noise and removed. */
    const val SMOOTH_MIN_PX = 5

    /** How many pixels to peel from line ends, removing short fringes/spurs. */
    const val PRUNE_ROUNDS = 2

    /**
     * The full smoothing step: peel short fringes (spurs) off the lines, then drop isolated specks.
     * Pruning is what calms methods like Canny, whose noise hangs off real lines rather than being
     * isolated, while despeckle cleans up the region method's loose specks.
     */
    fun smooth(mask: BooleanArray, width: Int, height: Int): BooleanArray {
        val pruned = prune(mask, width, height, PRUNE_ROUNDS)
        return despeckle(pruned, width, height, SMOOTH_MIN_PX)
    }

    /** Removes endpoint pixels (0 or 1 set neighbor) [rounds] times, shortening dead-end branches. */
    fun prune(mask: BooleanArray, width: Int, height: Int, rounds: Int): BooleanArray {
        var cur = mask
        repeat(rounds) {
            val next = cur.copyOf()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    if (!cur[i]) continue
                    var neighbors = 0
                    for (oy in -1..1) for (ox in -1..1) {
                        if (ox == 0 && oy == 0) continue
                        val nx = x + ox
                        val ny = y + oy
                        if (nx in 0 until width && ny in 0 until height && cur[ny * width + nx]) neighbors++
                    }
                    if (neighbors <= 1) next[i] = false
                }
            }
            cur = next
        }
        return cur
    }

    fun despeckle(mask: BooleanArray, width: Int, height: Int, minSize: Int): BooleanArray {
        val n = mask.size
        val out = BooleanArray(n)
        val visited = BooleanArray(n)
        val stack = ArrayDeque<Int>()
        val component = ArrayList<Int>()
        for (start in 0 until n) {
            if (!mask[start] || visited[start]) continue
            component.clear()
            stack.addLast(start)
            visited[start] = true
            while (stack.isNotEmpty()) {
                val i = stack.removeLast()
                component.add(i)
                val cx = i % width
                val cy = i / width
                for (oy in -1..1) for (ox in -1..1) {
                    if (ox == 0 && oy == 0) continue
                    val nx = cx + ox
                    val ny = cy + oy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val j = ny * width + nx
                    if (mask[j] && !visited[j]) {
                        visited[j] = true
                        stack.addLast(j)
                    }
                }
            }
            if (component.size >= minSize) for (i in component) out[i] = true
        }
        return out
    }
}
