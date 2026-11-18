package com.emniko.blockpedia.anvil

/** Survival anvils refuse any single step that costs 40 levels or more ("Too Expensive!"). */
const val MAX_STEP_COST = 39

/** Extra cost an item carries from earlier anvil uses: 0, 1, 3, 7, 15, 31… */
fun priorWorkPenalty(uses: Int): Int = (1 shl uses) - 1

/** Something put into the anvil: the gear itself, or one enchanted book. */
data class Piece(val enchants: Map<String, Int>, val uses: Int = 0, val isItem: Boolean = false)

/** What putting [right] (a book) onto [left] gives, and the enchantment part of its cost. */
data class Merged(val enchants: Map<String, Int>, val enchantCost: Int)

/**
 * Java rules: each enchantment on the book costs (resulting level × book multiplier).
 * Equal levels go up by one (up to the maximum), otherwise the higher level stays.
 * An enchantment that clashes with one on the target is dropped and costs 1 level.
 */
fun mergeBook(left: Map<String, Int>, right: Map<String, Int>): Merged {
    val out = LinkedHashMap(left)
    var cost = 0
    for ((id, level) in right) {
        val e = Enchants[id]
        if (out.keys.any { !Enchants.compatible(it, id) }) {
            cost += 1
            continue
        }
        val current = out[id] ?: 0
        val next = if (current == level) minOf(level + 1, e.max) else maxOf(current, level)
        out[id] = next
        cost += next * e.bookMult
    }
    return Merged(out, cost)
}

/**
 * One item in the plan: either a starting piece ([piece] ≥ 0) or the result of combining
 * [left] (left anvil slot) with [right] (right anvil slot) for [stepCost] levels.
 */
class PlanNode(
    val piece: Int,
    val left: PlanNode?,
    val right: PlanNode?,
    val enchants: Map<String, Int>,
    val uses: Int,
    val stepCost: Int,
    /** Levels spent on this node and everything under it. */
    val total: Int,
    /** Most expensive single step under this node. */
    val maxStep: Int,
) {
    val isLeaf get() = piece >= 0

    /** The combining steps in an order they can be done, deepest first. */
    fun steps(): List<PlanNode> {
        val out = ArrayList<PlanNode>()
        fun walk(n: PlanNode) {
            if (n.isLeaf) return
            walk(n.left!!)
            walk(n.right!!)
            out += n
        }
        walk(this)
        return out
    }
}

object AnvilPlanner {
    /** Above this many pieces the search gets slow on a phone. */
    const val MAX_PIECES = 13

    /**
     * Cheapest way (fewest total levels) to combine every piece into one, never paying more
     * than [limit] for a step. Ties go to the lower final penalty, then the cheaper worst step.
     * Returns null if no order works. The item, if any, always stays in the left slot.
     *
     * Exact search over all combining trees: for every subset of pieces and every possible
     * number of anvil uses on the result, it keeps the cheapest way to build that subset.
     * The enchantments of a subset don't depend on the order, so this finds the true minimum.
     */
    fun plan(pieces: List<Piece>, limit: Int = MAX_STEP_COST): PlanNode? {
        val n = pieces.size
        require(n in 1..MAX_PIECES) { "Between 1 and $MAX_PIECES pieces" }
        require(pieces.count { it.isItem } <= 1) { "Only one item" }
        val item = pieces.indexOfFirst { it.isItem }
        val full = (1 shl n) - 1
        val best = arrayOfNulls<HashMap<Int, PlanNode>>(full + 1)
        pieces.forEachIndexed { i, p ->
            best[1 shl i] = hashMapOf(p.uses to PlanNode(i, null, null, p.enchants, p.uses, 0, 0, 0))
        }

        for (mask in 1..full) {
            if (mask and (mask - 1) == 0) continue
            val withItem = item >= 0 && (mask shr item) and 1 == 1
            val table = HashMap<Int, PlanNode>()
            var left = (mask - 1) and mask
            while (left > 0) {
                val right = mask xor left
                val itemOk = !withItem || (left shr item) and 1 == 1
                val lt = best[left]
                val rt = best[right]
                if (itemOk && lt != null && rt != null) {
                    val merged = mergeBook(lt.values.first().enchants, rt.values.first().enchants)
                    for (l in lt.values) for (r in rt.values) {
                        val step = priorWorkPenalty(l.uses) + priorWorkPenalty(r.uses) + merged.enchantCost
                        if (step > limit) continue
                        val uses = maxOf(l.uses, r.uses) + 1
                        val total = l.total + r.total + step
                        val worst = maxOf(step, l.maxStep, r.maxStep)
                        val cur = table[uses]
                        if (cur == null || total < cur.total || (total == cur.total && worst < cur.maxStep)) {
                            table[uses] = PlanNode(-1, l, r, merged.enchants, uses, step, total, worst)
                        }
                    }
                }
                left = (left - 1) and mask
            }
            if (table.isNotEmpty()) best[mask] = table
        }

        return best[full]?.values?.minWithOrNull(
            compareBy<PlanNode>({ it.total }, { it.uses }, { it.maxStep }),
        )
    }
}
