package spam.blocker.service.bot

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import spam.blocker.service.checker.CheckContext

class CallerIdCategoryTest {
    private val context = mockk<Context>(relaxed = true)

    private fun parser() = ParseQueryResult(
        negativeSig = """"spam"\s*:\s*true""",
        positiveSig = "",
        categorySig = """"tag"\s*:\s*"([^"]+)""",
    )

    @Test
    fun `a bank label survives an undetermined verdict`() {
        val actionContext = ActionContext(
            lastOutput = """{"spam":null,"tag":"中国银行"}""".toByteArray(),
        )
        assertTrue(parser().execute(context, actionContext))
        val result = actionContext.racingResult!!
        assertFalse(result.determined)
        assertFalse(result.isSpam)
        assertEquals("中国银行", result.category)
    }

    @Test
    fun `an unknown follow-up parser cannot erase a spam verdict`() {
        val actionContext = ActionContext(
            lastOutput = """{"spam":true,"tag":"商业营销"}""".toByteArray(),
        )
        val parse = parser()
        assertTrue(parse.execute(context, actionContext))
        actionContext.lastOutput = """{"spam":null,"tag":"中国银行"}""".toByteArray()
        assertTrue(parse.execute(context, actionContext))
        val result = actionContext.racingResult!!
        assertTrue(result.determined)
        assertTrue(result.isSpam)
        assertEquals("商业营销", result.category)
    }

    @Test
    fun `caller metadata prefers an identified category and retains spam priority`() {
        val check = CheckContext(rawNumber = "075595566", simSlot = null, checkers = emptyList())
        check.rememberCallerIdResult(ApiQueryResult())
        check.rememberCallerIdResult(ApiQueryResult(category = "中国银行"))
        assertEquals("中国银行", check.callerIdResult()?.category)
        check.rememberCallerIdResult(ApiQueryResult(determined = true, isSpam = true, category = "商业营销"))
        check.rememberCallerIdResult(ApiQueryResult(category = "中国银行"))
        assertEquals("商业营销", check.callerIdResult()?.category)
    }
}
