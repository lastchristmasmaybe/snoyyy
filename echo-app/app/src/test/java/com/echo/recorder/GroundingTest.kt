package com.echo.recorder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingTest {
    private val transcript = "[00:02] Hey buddy, can you tell me which things are done and what still needs to be done on the website launch"
    private val tt = Grounding.tokenSet(transcript)

    @Test fun keepsPointsThatCameFromThisRecording() {
        assertTrue(Grounding.supported("Asks which website launch things are done and what still needs doing", tt))
    }

    @Test fun dropsDetailsFromAnotherRecording() {
        // Regression: notes from an older recording leaked into the newer one.
        assertFalse(Grounding.supported("Received an access token from Shitesh for the identity service", tt))
        assertFalse(Grounding.supported("Rohan will send the invoice by Friday", tt))
    }

    @Test fun summaryKeepsOnlySupportedSentences() {
        val s = "The speaker asks what is done and what still needs to be done on the website launch. Shitesh sent an access token for the identity service."
        val out = Grounding.summary(s, tt)
        assertTrue(out, out.contains("website launch"))
        assertFalse(out, out.contains("Shitesh"))
    }

    @Test fun aNameThatWasNeverSpokenIsNotAccepted() {
        assertFalse(Grounding.nameSpoken("Shitesh", transcript))
        assertTrue(Grounding.nameSpoken("Rohan Mehta", "I spoke to Rohan yesterday"))
    }

    @Test fun veryShortRecordingsDoNotGetPaddedNotes() {
        val short = Grounding.tokenSet("[00:00] you stop it")
        val notes = listOf("Discussed the quarterly budget and hiring plan", "Speaker tells someone to stop it")
        assertEquals(listOf("Speaker tells someone to stop it"), Grounding.filter(notes, short))
    }
}
