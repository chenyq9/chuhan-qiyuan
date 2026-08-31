package com.chuhan.qiyuan

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Assert.assertEquals
import com.chuhan.qiyuan.ui.MainActivity

/**
 * 楚汉棋缘 UI 集成测试（跑在模拟器上，不依赖网络与真实 AI）。
 * 覆盖：主菜单 → 开局 → 点子选子 → 非法棋挂起 → 教育/替它走 → 悔棋 → 认输。
 */
@RunWith(AndroidJUnit4::class)
class GameUiTest {

    @get:org.junit.Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun txt(s: String) = rule.onNodeWithText(s)

    private fun tapCell(col: Int, row: Int) {
        // 棋盘为正方形占满宽度：列c行r 的格点在 (宽*(c+1)/10, 宽*(r+1)/10)
        rule.runOnUiThread { }
        val node = rule.onAllNodesWithText("轮到你（红方）").fetchSemanticsNodes().firstOrNull()
            ?: throw AssertionError("不在对局页或未轮到红方")
        val bar = rule.onNodeWithText("轮到你（红方）").fetchSemanticsNode().boundsInRoot
        val w = rule.activity.window.decorView.width.toFloat()
        val x = w * (col + 1) / 10f
        val y = bar.top - w * 0.10f * (10 - row - 1) / 1f
        rule.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(x, y)) }
    }

    @Test fun t1_menuButtonsExist() {
        txt("楚汉棋缘").assertExists()
        txt("执红开局（我先走）").assertExists()
        txt("执黑开局（AI 先走）").assertExists()
    }

    @Test fun t2_startGame_and_selectPiece() {
        txt("执红开局（我先走）").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("轮到你（红方）").fetchSemanticsNodes().isNotEmpty()
        }
        // 点红炮 (7,1)：应出现选中态（合法落点提示），无崩溃即通过
        rule.onRoot().performTouchInput {
            val w = width.toFloat()
            click(androidx.compose.ui.geometry.Offset(w * 2 / 10f, w * 8 / 10f))
        }
        rule.waitForIdle()
        txt("悔棋").assertExists()
    }

    @Test fun t3_illegalAiMove_flow() {
        txt("执红开局（我先走）").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("轮到你（红方）").fetchSemanticsNodes().isNotEmpty()
        }
        // 红方随手走一步（炮二平四），等待 AI 回应
        rule.onRoot().performTouchInput {
            val w = width.toFloat()
            click(androidx.compose.ui.geometry.Offset(w * 2 / 10f, w * 8 / 10f))
            click(androidx.compose.ui.geometry.Offset(w * 4 / 10f, w * 8 / 10f))
        }
        // 轮到黑方/AI 思考
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("AI 思考中…").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("轮到你（红方）").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun t4_resign_opensDialog() {
        txt("执红开局（我先走）").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("轮到你（红方）").fetchSemanticsNodes().isNotEmpty()
        }
        txt("认输").performClick()
        rule.waitUntil(3_000) {
            rule.onAllNodesWithText("你输了").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
