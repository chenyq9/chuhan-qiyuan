package com.chuhan.qiyuan

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.chuhan.qiyuan.ui.MainActivity

/**
 * 楚汉棋缘 UI 集成测试（跑在模拟器上，不依赖网络与真实 AI 回复）。
 * 棋盘通过 testTag("board") 定位，点击坐标 = 棋盘尺寸 * (格+1)/10。
 */
@RunWith(AndroidJUnit4::class)
class GameUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun txt(s: String) = rule.onNodeWithText(s)
    private fun exists(s: String) = rule.onAllNodesWithText(s).fetchSemanticsNodes().isNotEmpty()

    private fun tapCell(col: Int, row: Int) {
        rule.onNodeWithTag("board").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width * (col + 1) / 10f, height * (row + 1) / 10f))
        }
    }

    private fun startRedGame() {
        txt("执红开局（我先走）").performClick()
        rule.waitUntil(5_000) { exists("轮到你（红方）") }
    }

    @Test
    fun t1_menuButtonsExist() {
        txt("楚汉棋缘").assertExists()
        txt("执红开局（我先走）").assertExists()
        txt("执黑开局（AI 先走）").assertExists()
        txt("设置（API 密钥 / 模型 / 人设）").assertExists()
    }

    @Test
    fun t2_startGame_selectPiece() {
        startRedGame()
        tapCell(1, 7)          // 红炮：点中应进入选中态，不崩溃
        rule.waitForIdle()
        txt("悔棋").assertExists()
        txt("请教").assertExists()
    }

    @Test
    fun t3_moveAndAwaitAi() {
        startRedGame()
        tapCell(1, 7)          // 红炮 (7,1)
        rule.waitForIdle()
        tapCell(3, 7)          // 平移到 (7,3)：合法走法，落子后轮到 AI
        rule.waitUntil(20_000) {
            exists("AI 思考中…") || exists("AI（黑方）") ||
                exists("网络出错") || exists("AI 走了一步不合法的棋")
        }
    }

    @Test
    fun t4_resign_endsGame() {
        startRedGame()
        txt("认输").performClick()
        rule.waitUntil(5_000) { exists("你输了") }
        txt("回主页").assertExists()
        txt("再来一局").assertExists()
    }
}
