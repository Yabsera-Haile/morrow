package app.morrow.diary.domain

/** Turning typed or pasted text into goals. */
object GoalText {
    private val listMarker = Regex("""^\s*(?:[-*•–—]\s+|\d+[.)]\s+)?(?:\[[ xX]?]\s*)?""")

    /** Removes list markers people paste from notes apps: "- ", "• ", "1. ", "[ ] ". */
    fun clean(line: String): String = line.replace(listMarker, "").trim()

    /** Every non-blank line as a goal. */
    fun splitLines(text: String): List<String> = text.lines().map(::clean).filter(String::isNotEmpty)

    /**
     * For the "add a goal" box: everything before the last line break is finished and becomes
     * goals; what comes after it is still being typed. Pressing Enter therefore adds the goal,
     * and pasting a list adds all of it at once.
     */
    fun takeFinishedLines(text: String): Pair<List<String>, String> {
        val lastBreak = text.lastIndexOf('\n')
        if (lastBreak < 0) return emptyList<String>() to text
        return splitLines(text.substring(0, lastBreak)) to text.substring(lastBreak + 1)
    }
}
