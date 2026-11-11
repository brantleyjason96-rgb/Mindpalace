package com.mindpalace.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan 02 §3.5 (untagged): the policy check that runs before anything touches the database. */
class AiProposalValidatorTest {

    private fun response(proposalsJson: String, summary: String = "null") =
        """{"summary":$summary,"proposals":[$proposalsJson]}"""

    private fun proposal(kind: String, value: String, evidence: String, confidence: String) =
        """{"kind":"$kind","value":"$value","evidence":"$evidence","confidence":$confidence}"""

    @Test
    fun `evidence must be a literal substring`() {
        val noteText = "Call Dylan about the quiz"
        val paraphrased = response(proposal("PERSON", "Dylan", "Dylan is mentioned", "0.80"))
        assertTrue(AiProposalValidator.validate(paraphrased, noteText).proposals.isEmpty())

        val exact = response(proposal("PERSON", "Dylan", "Dylan", "0.80"))
        assertEquals(1, AiProposalValidator.validate(exact, noteText).proposals.size)
    }

    @Test
    fun `evidence match is case-sensitive`() {
        val noteText = "Call Dylan about the quiz"
        val shouting = response(proposal("PERSON", "Dylan", "DYLAN", "0.80"))
        assertTrue(AiProposalValidator.validate(shouting, noteText).proposals.isEmpty())
    }

    @Test
    fun `confidence below 0point5 or above 1 is dropped`() {
        val noteText = "note"
        assertTrue(AiProposalValidator.validate(response(proposal("TAG", "x", "note", "0.49")), noteText).proposals.isEmpty())
        assertTrue(AiProposalValidator.validate(response(proposal("TAG", "x", "note", "1.01")), noteText).proposals.isEmpty())
        assertEquals(1, AiProposalValidator.validate(response(proposal("TAG", "x", "note", "0.50")), noteText).proposals.size)
        assertEquals(1, AiProposalValidator.validate(response(proposal("TAG", "x", "note", "1.00")), noteText).proposals.size)
    }

    @Test
    fun `unknown kind is dropped`() {
        val raw = response(proposal("LINK2", "x", "note", "0.80"))
        assertTrue(AiProposalValidator.validate(raw, "note").proposals.isEmpty())
    }

    @Test
    fun `malformed json yields an empty result`() {
        val result = AiProposalValidator.validate("{", "note")
        assertNull(result.summary)
        assertTrue(result.proposals.isEmpty())
    }

    @Test
    fun `summary is dropped for notes of 50 words or fewer`() {
        val fifty = (1..50).joinToString(" ") { "word" }
        val fiftyOne = (1..51).joinToString(" ") { "word" }

        assertNull(AiProposalValidator.validate("""{"summary":"a summary","proposals":[]}""", fifty).summary)
        assertEquals("a summary", AiProposalValidator.validate("""{"summary":"a summary","proposals":[]}""", fiftyOne).summary)
    }

    @Test
    fun `blank value or blank evidence is dropped`() {
        val noteText = "note"
        assertTrue(AiProposalValidator.validate(response(proposal("TAG", "", "note", "0.80")), noteText).proposals.isEmpty())
        assertTrue(AiProposalValidator.validate(response(proposal("TAG", "x", "", "0.80")), noteText).proposals.isEmpty())
    }
}
