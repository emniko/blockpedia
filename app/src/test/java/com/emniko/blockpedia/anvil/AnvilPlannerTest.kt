package com.emniko.blockpedia.anvil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnvilPlannerTest {

    private fun book(id: String, level: Int = Enchants[id].max) = Piece(mapOf(id to level))

    /** Tries every order of combining, for checking the planner on small inputs. */
    private fun bruteForce(pieces: List<Piece>, limit: Int = MAX_STEP_COST): Int? {
        data class Node(val enchants: Map<String, Int>, val uses: Int, val isItem: Boolean)
        var best: Int? = null
        fun go(nodes: List<Node>, spent: Int) {
            if (best != null && spent >= best!!) return
            if (nodes.size == 1) {
                best = spent
                return
            }
            for (i in nodes.indices) for (j in nodes.indices) {
                if (i == j || nodes[j].isItem) continue
                val l = nodes[i]
                val r = nodes[j]
                val m = mergeBook(l.enchants, r.enchants)
                val step = priorWorkPenalty(l.uses) + priorWorkPenalty(r.uses) + m.enchantCost
                if (step > limit) continue
                val rest = nodes.filterIndexed { k, _ -> k != i && k != j }
                go(rest + Node(m.enchants, maxOf(l.uses, r.uses) + 1, l.isItem), spent + step)
            }
        }
        go(pieces.map { Node(it.enchants, it.uses, it.isItem) }, 0)
        return best
    }

    /** Replays the plan's steps and checks every step is legal and the totals add up. */
    private fun checkPlan(pieces: List<Piece>, plan: PlanNode) {
        var sum = 0
        for (s in plan.steps()) {
            val l = s.left!!
            val r = s.right!!
            assertTrue("item never goes in the right slot", !(r.isLeaf && pieces[r.piece].isItem))
            val m = mergeBook(l.enchants, r.enchants)
            val cost = priorWorkPenalty(l.uses) + priorWorkPenalty(r.uses) + m.enchantCost
            assertEquals(cost, s.stepCost)
            assertTrue(cost <= MAX_STEP_COST)
            assertEquals(m.enchants, s.enchants)
            sum += cost
        }
        assertEquals(sum, plan.total)
    }

    @Test
    fun pickaxeHandWorkedExample() {
        // Efficiency V (5) + Unbreaking III (3) + Mending (2) = 10 levels of enchantments, and the
        // cheapest tree has to pay 2 more in penalties and 2 for re-applying Mending: 14.
        val pieces = listOf(Piece(emptyMap(), isItem = true), book("efficiency"), book("unbreaking"), book("mending"))
        val plan = AnvilPlanner.plan(pieces)!!
        assertEquals(14, plan.total)
        checkPlan(pieces, plan)
        assertEquals(mapOf("efficiency" to 5, "unbreaking" to 3, "mending" to 1), plan.enchants)
    }

    @Test
    fun matchesBruteForce() {
        val cases = listOf(
            listOf(
                Piece(emptyMap(), isItem = true),
                book("sharpness"), book("looting"), book("unbreaking"), book("mending"), book("fire_aspect"),
            ),
            listOf(
                Piece(mapOf("protection" to 4), uses = 2, isItem = true),
                book("feather_falling"), book("depth_strider"), book("unbreaking"), book("mending"), book("protection"),
            ),
            listOf(
                Piece(mapOf("sharpness" to 4, "unbreaking" to 2), uses = 1, isItem = true),
                book("sharpness", 4), book("unbreaking", 2), book("looting"), book("sweeping_edge"),
            ),
            // Books only (no item), e.g. merging books for later.
            listOf(book("power"), book("punch"), book("flame"), book("unbreaking"), book("mending")),
        )
        for (pieces in cases) {
            val plan = AnvilPlanner.plan(pieces)
            val brute = bruteForce(pieces)
            assertEquals(brute, plan?.total)
            checkPlan(pieces, plan!!)
        }
    }

    @Test
    fun levelsMergeLikeTheGame() {
        assertEquals(mapOf("sharpness" to 5), mergeBook(mapOf("sharpness" to 4), mapOf("sharpness" to 4)).enchants)
        assertEquals(5, mergeBook(mapOf("sharpness" to 4), mapOf("sharpness" to 4)).enchantCost)
        assertEquals(mapOf("sharpness" to 5), mergeBook(mapOf("sharpness" to 5), mapOf("sharpness" to 5)).enchants)
        assertEquals(mapOf("sharpness" to 4), mergeBook(mapOf("sharpness" to 4), mapOf("sharpness" to 2)).enchants)
        // Clashing enchantment is dropped and costs 1.
        val clash = mergeBook(mapOf("sharpness" to 5), mapOf("smite" to 5))
        assertEquals(mapOf("sharpness" to 5), clash.enchants)
        assertEquals(1, clash.enchantCost)
    }

    @Test
    fun fullSwordFitsAndTooExpensiveIsReported() {
        val sword = listOf(Piece(emptyMap(), isItem = true)) +
            listOf("sharpness", "looting", "unbreaking", "mending", "fire_aspect", "knockback", "sweeping_edge")
                .map { book(it) }
        val plan = AnvilPlanner.plan(sword)
        assertNotNull(plan)
        checkPlan(sword, plan!!)
        assertTrue(plan.steps().all { it.stepCost <= MAX_STEP_COST })

        // An item already used 6 times pays 63 per step: nothing fits.
        assertNull(AnvilPlanner.plan(listOf(Piece(emptyMap(), uses = 6, isItem = true), book("mending"))))
    }
}
