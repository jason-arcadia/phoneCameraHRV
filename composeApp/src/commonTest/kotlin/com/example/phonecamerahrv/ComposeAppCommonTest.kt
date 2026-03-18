package com.example.phonecamerahrv

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }
}

class PPGProcessorTest {

    private val processor = PPGProcessor()

    @Test
    fun calculateRMSSD_knownIntervals_returnsCorrectValue() {
        // diffs: [800-810=-10, 810-790=20] -> squared: [100, 400] -> mean: 250 -> sqrt(250)
        val rr = listOf(800.0, 810.0, 790.0)
        val expected = sqrt(250.0)
        val result = processor.calculateRMSSD(rr)
        assertEquals(expected, result, absoluteTolerance = 1e-9)
    }

    @Test
    fun calculateRMSSD_identicalIntervals_returnsZero() {
        val rr = listOf(800.0, 800.0, 800.0)
        assertEquals(0.0, processor.calculateRMSSD(rr))
    }

    @Test
    fun calculateRMSSD_singleInterval_returnsZero() {
        assertEquals(0.0, processor.calculateRMSSD(listOf(800.0)))
    }

    @Test
    fun calculateRMSSD_emptyList_returnsZero() {
        assertEquals(0.0, processor.calculateRMSSD(emptyList()))
    }

    @Test
    fun calculateRMSSD_twoIntervals_returnsAbsDiff() {
        // diffs: [900-800=100] -> squared: [10000] -> mean: 10000 -> sqrt(10000) = 100
        val rr = listOf(800.0, 900.0)
        assertEquals(100.0, processor.calculateRMSSD(rr), absoluteTolerance = 1e-9)
    }
}