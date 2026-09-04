package jp.local.kakeibo.gambling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class GamblingValidationTest {
    @Test
    fun `stake and payout must not be negative`() {
        assertEquals(
            GamblingInputError.NEGATIVE_STAKE,
            validateGamblingInput(stakeAmount = -1, payoutAmount = 100, gameType = "競馬"),
        )
        assertEquals(
            GamblingInputError.NEGATIVE_PAYOUT,
            validateGamblingInput(stakeAmount = 100, payoutAmount = -1, gameType = "競馬"),
        )
    }

    @Test
    fun `at least one amount must be positive`() {
        assertEquals(
            GamblingInputError.EMPTY_AMOUNTS,
            validateGamblingInput(stakeAmount = 0, payoutAmount = 0, gameType = "競馬"),
        )
        assertNull(validateGamblingInput(stakeAmount = 100, payoutAmount = 0, gameType = "競馬"))
        assertNull(validateGamblingInput(stakeAmount = 0, payoutAmount = 100, gameType = "競馬"))
    }

    @Test
    fun `game type must contain non-whitespace text`() {
        assertEquals(
            GamblingInputError.BLANK_GAME_TYPE,
            validateGamblingInput(stakeAmount = 100, payoutAmount = 0, gameType = "  \n"),
        )
    }

    @Test
    fun `required validation rejects invalid input`() {
        val error =
            assertThrows(IllegalArgumentException::class.java) {
                requireValidGamblingInput(stakeAmount = 0, payoutAmount = 0, gameType = "競馬")
            }

        assertEquals(GamblingInputError.EMPTY_AMOUNTS.message, error.message)
    }
}
