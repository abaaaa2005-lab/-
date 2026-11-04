package com.vse.launcher.util

/**
 * Раскладывает иконки по сетке: обычные занимают 1×1, большие папки — 2×2.
 * Иконки идут по порядку, каждая встаёт в первое свободное место, где помещается.
 */
object GridPlacer {
    data class Place(val itemIndex: Int, val row: Int, val col: Int, val span: Int)

    /** Возвращает размещения и количество поместившихся элементов (идущих подряд с начала списка). */
    fun place(items: List<String>, rows: Int, cols: Int, isBig: (String) -> Boolean): Pair<List<Place>, Int> {
        if (rows <= 0 || cols <= 0) return emptyList<Place>() to 0
        val occ = Array(rows) { BooleanArray(cols) }
        val res = ArrayList<Place>(items.size)
        items.forEachIndexed { i, id ->
            val span = if (isBig(id) && rows >= 2 && cols >= 2) 2 else 1
            var placed = false
            loop@ for (r in 0..rows - span) {
                for (c in 0..cols - span) {
                    if (fits(occ, r, c, span)) {
                        for (dr in 0 until span) for (dc in 0 until span) occ[r + dr][c + dc] = true
                        res += Place(i, r, c, span)
                        placed = true
                        break@loop
                    }
                }
            }
            if (!placed) return res to i
        }
        return res to items.size
    }

    private fun fits(occ: Array<BooleanArray>, r: Int, c: Int, span: Int): Boolean {
        for (dr in 0 until span) for (dc in 0 until span) if (occ[r + dr][c + dc]) return false
        return true
    }
}
