package com.smartsiddur.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConditionTest {

    private fun eval(expr: String, vararg flags: String) = Condition.parse(expr).evaluate(flags.toSet())

    @Test
    fun singleFlag() {
        assertTrue(eval("rosh_chodesh", "rosh_chodesh"))
        assertFalse(eval("rosh_chodesh", "chanukah"))
    }

    @Test
    fun andBindsTighterThanOr() {
        // a | b & c  ==  a | (b & c)
        assertTrue(eval("a | b & c", "a"))
        assertFalse(eval("a | b & c", "b"))
        assertTrue(eval("a | b & c", "b", "c"))
    }

    @Test
    fun notAndParentheses() {
        assertTrue(eval("!tachanun_day"))
        assertFalse(eval("!tachanun_day", "tachanun_day"))
        assertTrue(eval("tachanun & (day_2 | day_5)", "tachanun", "day_5"))
        assertFalse(eval("tachanun & (day_2 | day_5)", "tachanun", "day_3"))
        assertTrue(eval("(yom_tov | chol_hamoed) & !rosh_hashana", "chol_hamoed"))
        assertFalse(eval("(yom_tov | chol_hamoed) & !rosh_hashana", "yom_tov", "rosh_hashana"))
    }

    @Test
    fun blankMeansAlways() {
        assertTrue(Condition.isSatisfied(null, emptySet()))
        assertTrue(Condition.isSatisfied("  ", emptySet()))
    }

    @Test
    fun flagNames() {
        assertEquals(setOf("a", "b", "c"), Condition.parse("a & !(b | c)").flagNames())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnbalancedParentheses() {
        Condition.parse("(a | b")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTrailingOperator() {
        Condition.parse("a &")
    }
}
