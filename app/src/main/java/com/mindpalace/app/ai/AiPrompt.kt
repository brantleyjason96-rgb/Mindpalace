package com.mindpalace.app.ai

/**
 * Prompt + GBNF grammar for the per-note night-loop completion (D13/D22/D23). One note in, one
 * JSON blob out: an optional plain-language summary (only for notes over 50 words — a 40-word
 * note is basically its own summary already) plus a list of label proposals. The model never
 * invents a person/place/category outside what's listed or quoted from the note itself.
 */
object AiPrompt {
    const val PROMPT_VERSION = "v1"
    const val SUMMARY_MIN_WORDS = 50

    // Mirrors the bundled llama.cpp json.gbnf conventions (ws-after-token style), narrowed to
    // exactly the shape AiProposalResponse expects so the validator never has to guess.
    val GRAMMAR = """
        root       ::= "{" ws "\"summary\":" ws summary "," ws "\"proposals\":" ws proposals "}" ws
        summary    ::= ("null" | string) ws
        proposals  ::= "[" ws (proposal ("," ws proposal)*)? "]" ws
        proposal   ::= "{" ws "\"kind\":" ws kind "," ws "\"value\":" ws string "," ws "\"evidence\":" ws string "," ws "\"confidence\":" ws confidence ws "}" ws
        kind       ::= ("\"CATEGORY\"" | "\"TAG\"" | "\"TOPIC\"" | "\"PERSON\"" | "\"PLACE\"") ws
        confidence ::= ("0." [0-9] [0-9] | "1.00") ws
        string     ::= "\"" ( [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}) )* "\"" ws
        ws         ::= | " " | "\n" [ \t]{0,20}
    """.trimIndent()

    fun systemPrompt(categories: List<String>, topics: List<String>): String {
        val categoryList = categories.joinToString(", ").ifBlank { "(none yet)" }
        val topicList = topics.joinToString(", ").ifBlank { "(none yet)" }
        return """
            You are a filing assistant for a personal notes app. You never invent facts and only
            suggest labels clearly supported by the note text below. Respond with JSON only,
            matching the required schema exactly.

            Pick at most one category from this exact list (or omit a CATEGORY proposal entirely
            if none fit): $categoryList
            Known topics — reuse one of these names when it fits, or propose a short new one:
            $topicList

            For every proposal, "evidence" must be a short quote copied exactly from the note text,
            not paraphrased. Only propose PERSON or PLACE for names actually written in the note.
            Set "confidence" between 0.00 and 1.00 for how sure you are.

            Only include a "summary" (one or two plain sentences) if the note has more than
            $SUMMARY_MIN_WORDS words; otherwise set "summary" to null.
        """.trimIndent()
    }

    fun userPrompt(noteText: String): String = noteText

    fun needsSummary(noteText: String): Boolean =
        noteText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size > SUMMARY_MIN_WORDS
}
