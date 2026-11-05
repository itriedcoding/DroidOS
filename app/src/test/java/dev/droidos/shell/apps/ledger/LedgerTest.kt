package dev.droidos.shell.apps.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerTest {

    @Test
    fun amountsBecomeIntegerCents() {
        assertEquals(1234L, Money.parseToCents("12.34"))
        assertEquals(1200L, Money.parseToCents("12"))
        assertEquals(1250L, Money.parseToCents("12.5"))
        assertEquals(5L, Money.parseToCents("0.05"))
        assertEquals(123456L, Money.parseToCents("1,234.56"))
        assertEquals(4200L, Money.parseToCents("+42"))
        assertEquals(-999L, Money.parseToCents("-9.99"))
    }

    @Test
    fun badAmountsAreRejectedInsteadOfRounded() {
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("12.345"))
        assertNull(Money.parseToCents("twelve"))
        assertNull(Money.parseToCents("1.2.3"))
        assertNull(Money.parseToCents("."))
    }

    @Test
    fun formattingGroupsThousandsAndKeepsTwoDecimals() {
        assertEquals("12.34", Money.format(1234))
        assertEquals("1,234.56", Money.format(123456))
        assertEquals("0.05", Money.format(5))
        assertEquals("-9.99", Money.format(-999))
        assertEquals("1.00", Money.format(100))
    }

    @Test
    fun summarySeparatesIncomeFromSpending() {
        val entries = listOf(
            Entry(cents = 500_00, category = "Other", note = "salary", income = true, createdAt = 1),
            Entry(cents = 12_34, category = "Food", note = "lunch", income = false, createdAt = 2),
            Entry(cents = 7_66, category = "Food", note = "coffee", income = false, createdAt = 3),
            Entry(cents = 100_00, category = "Other", note = "refund", income = true, createdAt = 4)
        )
        val summary = LedgerMath.summarise(entries)
        assertEquals(600_00L, summary.incomeCents)
        assertEquals(20_00L, summary.expenseCents)
        assertEquals(580_00L, summary.balanceCents)
    }

    @Test
    fun emptyLedgerBalancesToZero() {
        val summary = LedgerMath.summarise(emptyList())
        assertEquals(0L, summary.balanceCents)
        assertEquals("0.00", Money.format(summary.balanceCents))
    }
}
