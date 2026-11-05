package app.morrow.diary.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalTextTest {
    @Test fun `list markers from notes apps are removed`() {
        assertEquals("Send the invoice", GoalText.clean("- Send the invoice"))
        assertEquals("Send the invoice", GoalText.clean("• Send the invoice"))
        assertEquals("Send the invoice", GoalText.clean("  * Send the invoice "))
        assertEquals("Send the invoice", GoalText.clean("1. Send the invoice"))
        assertEquals("Send the invoice", GoalText.clean("2) Send the invoice"))
        assertEquals("Send the invoice", GoalText.clean("- [ ] Send the invoice"))
        assertEquals("Send the invoice", GoalText.clean("[x] Send the invoice"))
    }

    @Test fun `ordinary text is left alone`() {
        assertEquals("Run 5k after work", GoalText.clean("Run 5k after work"))
        assertEquals("2026 taxes", GoalText.clean("2026 taxes"))
        assertEquals("-5 kg by June", GoalText.clean("-5 kg by June"))
    }

    @Test fun `a pasted list becomes one goal per line, skipping blanks`() {
        val pasted = "- Send the invoice\n\n- Run 5k after work\r\n•  Call the bank\n"
        assertEquals(listOf("Send the invoice", "Run 5k after work", "Call the bank"), GoalText.splitLines(pasted))
    }

    @Test fun `pressing Enter finishes the line`() {
        assertEquals(listOf("Buy groceries") to "", GoalText.takeFinishedLines("Buy groceries\n"))
    }

    @Test fun `text without a line break is still being typed`() {
        assertEquals(emptyList<String>() to "Buy gro", GoalText.takeFinishedLines("Buy gro"))
    }

    @Test fun `pasting keeps the unfinished last line in the box`() {
        assertEquals(listOf("One", "Two") to "Thr", GoalText.takeFinishedLines("One\nTwo\nThr"))
    }

    @Test fun `an empty Enter adds nothing`() {
        assertEquals(emptyList<String>() to "", GoalText.takeFinishedLines("\n"))
    }
}
