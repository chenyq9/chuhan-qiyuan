package com.chuhan.qiyuan.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuhan.qiyuan.ai.AIAttempt
import com.chuhan.qiyuan.ai.LLMClient
import com.chuhan.qiyuan.ai.MoveRecord
import com.chuhan.qiyuan.ai.Persona
import com.chuhan.qiyuan.ai.PromptBuilder
import com.chuhan.qiyuan.data.AppSettings
import com.chuhan.qiyuan.data.SettingsRepository
import com.chuhan.qiyuan.engine.Board
import com.chuhan.qiyuan.engine.Move
import com.chuhan.qiyuan.engine.Piece
import com.chuhan.qiyuan.engine.PieceType
import com.chuhan.qiyuan.engine.Side
import com.chuhan.qiyuan.engine.XiangqiRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ChatMsg(val who: String, val text: String) // who: user / ai / system
data class InvalidState(val attempt: AIAttempt, val reason: String)
data class GameOver(val humanWon: Boolean?, val reason: String) // null = 和棋

class GameViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    var settings by mutableStateOf(settingsRepo.load())
        private set

    private var llm: LLMClient = LLMClient(settings.endpoint, settings.apiKey, settings.model, settings.temperature, settings.timeoutSec)

    // ---------- 对局状态 ----------
    var board by mutableStateOf(Board.initial())
        private set
    var turn by mutableStateOf(Side.RED)
        private set
    var humanSide by mutableStateOf(Side.RED)
        private set
    var personaId by mutableStateOf(settings.personaId)
    val aiSide: Side get() = humanSide.opposite()

    val history = mutableStateListOf<MoveRecord>()
    val chat = mutableStateListOf<ChatMsg>()
    private val posKeys = mutableStateListOf<String>()

    var selected by mutableStateOf<Pair<Int, Int>?>(null)
    var aiThinking by mutableStateOf(false)
        private set
    var chatThinking by mutableStateOf(false)
        private set
    var pendingInvalid by mutableStateOf<InvalidState?>(null)
        private set
    var pendingUndo by mutableStateOf(false)
        private set
    var pendingResign by mutableStateOf(false)
        private set
    var networkError by mutableStateOf<String?>(null)
        private set
    var gameOver by mutableStateOf<GameOver?>(null)
        private set
    var playForAiMode by mutableStateOf(false)
    var statsText by mutableStateOf("")
        private set
    var screen by mutableStateOf("menu") // menu / game / settings

    private var aiJob: Job? = null
    private var lastInvalid: InvalidState? = null

    init {
        loadStats()
        restore()
    }

    // ---------- 人设 / 记忆 ----------
    private fun persona(): Persona = Persona.byId(personaId)

    private fun chatMemory(): List<Pair<String, String>> =
        chat.takeLast(12).map { m ->
            (when (m.who) { "user" -> "对手"; "ai" -> "你"; else -> "裁判" }) to m.text
        }

    // ---------- 聊天流 ----------
    private fun addMsg(who: String, text: String) {
        chat.add(ChatMsg(who, text))
        if (chat.size > 200) chat.removeRange(0, chat.size - 200)
    }
    private fun addUser(t: String) = addMsg("user", t)
    private fun addAI(t: String) = addMsg("ai", t)
    private fun addSystem(t: String) = addMsg("system", t)

    // ---------- 新局 ----------
    fun newGame(side: Side) {
        aiJob?.cancel(); aiThinking = false
        humanSide = side
        board = Board.initial()
        history.clear(); chat.clear(); posKeys.clear()
        posKeys.add(board.key())
        turn = Side.RED
        selected = null; pendingInvalid = null; lastInvalid = null
        pendingUndo = false; pendingResign = false; networkError = null
        gameOver = null; playForAiMode = false
        addSystem(if (side == Side.RED) "新对局开始，你执红先行。祝好运！" else "新对局开始，你执黑后行，AI 先走。")
        screen = "game"
        persist()
        if (turn == aiSide) triggerAI(null)
    }

    fun gotoScreen(s: String) { screen = s }

    // ---------- 点子走子 ----------
    fun onCellTap(r: Int, c: Int) {
        if (gameOver != null || aiThinking || pendingInvalid != null) return
        val activeSide = if (playForAiMode) aiSide else humanSide
        if (turn != activeSide) return
        val sel = selected
        if (sel == null) {
            val p = board[r, c]
            if (p != null && p.side == activeSide) selected = r to c
            return
        }
        if (r == sel.first && c == sel.second) { selected = null; return }
        val move = Move(sel.first, sel.second, r, c)
        if (move in XiangqiRules.legalMoves(board, activeSide)) {
            selected = null
            executeMove(move, bySide = activeSide, byHumanHand = true)
        } else {
            val p = board[r, c]
            selected = if (p != null && p.side == activeSide) r to c else null
        }
    }

    private fun executeMove(move: Move, bySide: Side, byHumanHand: Boolean) {
        val captured = board[move.toRow, move.toCol]
        board = board.moved(move)
        history.add(MoveRecord(bySide, move, captured))
        posKeys.add(board.key())
        val capTxt = if (captured != null) "（吃 ${captured.char()}）" else ""
        if (captured != null) addSystem("吃掉${captured.char()}！")
        turn = turn.opposite()
        persist()

        if (XiangqiRules.isInCheck(board, turn)) addSystem("将军！${turn.cn()}的将/帅被攻击")
        if (!XiangqiRules.hasAnyLegalMove(board, turn)) {
            endGame(bySide, if (XiangqiRules.isInCheck(board, turn)) "绝杀" else "困毙")
            return
        }
        if (posKeys.count { it == board.key() } >= 3) {
            endGame(null, "重复局面")
            return
        }
        if (byHumanHand && playForAiMode) {
            playForAiMode = false
            addSystem("（你替 AI 走了这步）")
        }
        if (turn == aiSide && gameOver == null) triggerAI(null)
    }

    // ---------- AI 调度 ----------
    private fun triggerAI(invalid: InvalidState?) {
        if (gameOver != null) return
        aiThinking = true
        val b = board; val h = history.toList(); val mem = chatMemory(); val per = persona()
        aiJob = viewModelScope.launch {
            try {
                val msgs = PromptBuilder.moveMessages(b, aiSide, h, mem, per, invalid?.reason, invalid?.attempt)
                val text = llm.chat(msgs, if (settings.moveMaxTokens > 0) settings.moveMaxTokens else -1)
                handleAIMoveReply(text)
            } catch (e: CancellationException) {
                // 被取消（悔棋/新局），静默
            } catch (e: Exception) {
                networkError = e.message ?: "未知网络错误"
            } finally {
                aiThinking = false
            }
        }
    }

    private fun handleAIMoveReply(text: String) {
        val attempt = PromptBuilder.parseMoveResponse(text)
        attempt.chat?.let { addAI(it) }
        when {
            attempt.resign -> { pendingResign = true; persist(); return }
            attempt.undoRequest -> {
                if (history.isNotEmpty() && history.last().side == aiSide) { pendingUndo = true; persist(); return }
            }
        }
        val f = attempt.from?.let { Move.parseCoord(it) }
        val t = attempt.to?.let { Move.parseCoord(it) }
        if (f == null || t == null) {
            val st = InvalidState(attempt, if (attempt.parsedAsJson) "JSON 里缺少 from/to" else "回复里没有可识别的走法")
            lastInvalid = st; pendingInvalid = st
            addSystem("AI 没有给出可用走法，棋局挂起：你可以在下方教育它、让它重想，或替它走。")
            persist(); return
        }
        val move = Move(f.first, f.second, t.first, t.second)
        val reason = XiangqiRules.validateMove(board, aiSide, move)
        if (reason != null) {
            val st = InvalidState(attempt, reason)
            lastInvalid = st; pendingInvalid = st
            addSystem("AI 想走 ${move.coordNotation()}，但非法：$reason。棋局挂起。")
        } else {
            pendingInvalid = null
            executeMove(move, bySide = aiSide, byHumanHand = false)
        }
        persist()
    }

    /** 用户教育后让 AI 重新思考 */
    fun retryAI() {
        val inv = lastInvalid
        pendingInvalid = null
        triggerAI(inv)
    }

    /** 用户替 AI 走：进入代走模式，点棋盘执行 */
    fun startPlayForAi() {
        pendingInvalid = null
        playForAiMode = true
        addSystem("代走模式：请点选 AI 的棋子帮它走一步。")
    }

    fun dismissNetworkError() { networkError = null }
    fun retryAfterError() { networkError = null; triggerAI(lastInvalid) }
    fun aiResignByError() { networkError = null; endGame(humanSide, "AI 连接失败判负") }

    // ---------- 悔棋 ----------
    fun canUserUndo(): Boolean = history.isNotEmpty() && !aiThinking && gameOver == null && pendingInvalid == null

    fun userUndo() {
        if (!canUserUndo()) return
        aiJob?.cancel(); aiThinking = false
        val n = if (history.size >= 2 && history.last().side == aiSide) 2 else 1
        repeat(n) {
            if (history.isNotEmpty()) history.removeAt(history.size - 1)
            if (posKeys.isNotEmpty()) posKeys.removeAt(posKeys.size - 1)
        }
        board = rebuildBoard()
        turn = if (history.isEmpty()) Side.RED else history.last().side.opposite()
        selected = null
        pendingInvalid = null; lastInvalid = null
        addSystem("悔棋：撤销了最近 $n 步，轮到你重走。")
        persist()
    }

    fun approveUndo() {
        pendingUndo = false
        if (history.isNotEmpty() && history.last().side == aiSide) {
            history.removeAt(history.size - 1)
            if (posKeys.isNotEmpty()) posKeys.removeAt(posKeys.size - 1)
            board = rebuildBoard()
            turn = if (history.isEmpty()) Side.RED else history.last().side.opposite()
            addSystem("你同意了 AI 悔棋，它重新思考这一步。")
            persist()
            triggerAI(null)
        }
    }

    fun rejectUndo() {
        pendingUndo = false
        addSystem("你拒绝了 AI 的悔棋申请，它必须继续走下去。")
        persist()
        if (turn == aiSide && !aiThinking && gameOver == null) triggerAI(null)
    }

    private fun rebuildBoard(): Board {
        var b = Board.initial()
        for (rec in history) b = b.moved(rec.move)
        return b
    }

    // ---------- 认输 ----------
    fun humanResign() {
        if (gameOver == null) endGame(aiSide, "你认输了")
    }
    fun acceptAIResign() {
        pendingResign = false
        endGame(humanSide, "AI 认输")
    }
    fun declineAIResign() {
        pendingResign = false
        addSystem("你没接受 AI 的认输，它得继续下。")
        persist()
        if (turn == aiSide && !aiThinking && gameOver == null) triggerAI(null)
    }

    private fun endGame(winnerSide: Side?, reason: String) {
        gameOver = when (winnerSide) {
            null -> GameOver(null, reason)
            aiSide -> GameOver(false, reason)
            else -> GameOver(true, reason)
        }
        aiJob?.cancel(); aiThinking = false
        val p = getApplication<Application>().getSharedPreferences("stats", Context.MODE_PRIVATE)
        when (winnerSide) {
            null -> p.edit().putInt("d", p.getInt("d", 0) + 1).apply()
            humanSide -> p.edit().putInt("w", p.getInt("w", 0) + 1).apply()
            else -> p.edit().putInt("l", p.getInt("l", 0) + 1).apply()
        }
        addSystem("对局结束：$reason")
        loadStats()
        persist()
    }

    private fun loadStats() {
        val p = getApplication<Application>().getSharedPreferences("stats", Context.MODE_PRIVATE)
        val w = p.getInt("w", 0); val l = p.getInt("l", 0); val d = p.getInt("d", 0)
        statsText = if (w + l + d == 0) "还没有战绩，来一局？" else "战绩：胜 $w · 负 $l · 和 $d"
    }

    // ---------- 聊天 / 请教 ----------
    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty() || chatThinking || gameOver != null) return
        addUser(t)
        chatThinking = true
        val b = board; val h = history.toList(); val mem = chatMemory(); val per = persona()
        viewModelScope.launch {
            try {
                val reply = llm.chat(PromptBuilder.chatMessages(b, aiSide, h, mem, per, t), settings.chatMaxTokens)
                addAI(reply.trim())
                when (PromptBuilder.scanIntent(reply)) {
                    "undo" -> if (history.isNotEmpty() && history.last().side == aiSide && !aiThinking) pendingUndo = true
                    "resign" -> pendingResign = true
                }
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                addSystem("（消息发送失败：${e.message}）")
            } finally {
                chatThinking = false
            }
        }
    }

    fun askAdvice() {
        if (chatThinking || gameOver != null) return
        chatThinking = true
        val b = board; val h = history.toList(); val mem = chatMemory(); val per = persona(); val hs = humanSide
        viewModelScope.launch {
            try {
                val reply = llm.chat(PromptBuilder.adviceMessages(b, aiSide, hs, h, mem, per), 800)
                addAI(reply.trim())
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                addSystem("（请教失败：${e.message}）")
            } finally {
                chatThinking = false
            }
        }
    }

    // ---------- 设置 ----------
    fun saveSettings(s: AppSettings) {
        settings = s
        settingsRepo.save(s)
        personaId = s.personaId
        llm.update(s.endpoint, s.apiKey, s.model, s.temperature, s.timeoutSec)
    }

    fun testConnection(endpoint: String, key: String, model: String, temp: Double, timeout: Long, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val c = LLMClient(endpoint, key, model, temp, timeout)
            val r = c.testConnection()
            onResult(r ?: "连接正常 ✓（模型已回复）")
        }
    }

    // ---------- 持久化 ----------
    private fun gamePrefs() = getApplication<Application>().getSharedPreferences("game", Context.MODE_PRIVATE)

    private fun hasSavedGame(): Boolean = gamePrefs().getString("json", null) != null

    private fun persist() {
        val o = JSONObject()
        o.put("board", board.key())
        o.put("turn", turn.name)
        o.put("human", humanSide.name)
        o.put("persona", personaId)
        val h = JSONArray()
        for (r in history) {
            val ro = JSONObject(); ro.put("s", r.side.name); ro.put("m", r.move.coordNotation()); h.put(ro)
        }
        o.put("history", h)
        val cs = JSONArray()
        for (m in chat) {
            val mo = JSONObject(); mo.put("w", m.who); mo.put("t", m.text); cs.put(mo)
        }
        o.put("chat", cs)
        gameOver?.let { g ->
            val go = JSONObject()
            go.put("w", g.humanWon?.toString() ?: "draw")
            go.put("r", g.reason)
            o.put("over", go)
        }
        gamePrefs().edit().putString("json", o.toString()).apply()
    }

    private fun restore() {
        val raw = gamePrefs().getString("json", null) ?: return
        try {
            val o = JSONObject(raw)
            val bstr = o.optString("board")
            if (bstr.length != 90) return
            val map = HashMap<Char, Piece>()
            for (t in PieceType.values()) {
                map[t.redChar] = Piece(Side.RED, t)
                map[t.blackChar] = Piece(Side.BLACK, t)
            }
            val b = Board.initial()
            var i = 0
            for (r in 0..9) for (c in 0..8) { b.place(r, c, map[bstr[i]]); i++ }
            board = b
            turn = Side.valueOf(o.optString("turn", "RED"))
            humanSide = Side.valueOf(o.optString("human", "RED"))
            personaId = o.optString("persona", settings.personaId)
            val h = o.optJSONArray("history") ?: JSONArray()
            for (k in 0 until h.length()) {
                val ro = h.getJSONObject(k)
                val m = parseNotation(ro.optString("m")) ?: continue
                history.add(MoveRecord(Side.valueOf(ro.optString("s", "RED")), m, null))
            }
            val cs = o.optJSONArray("chat") ?: JSONArray()
            for (k in 0 until cs.length()) {
                val mo = cs.getJSONObject(k)
                chat.add(ChatMsg(mo.optString("w", "system"), mo.optString("t", "")))
            }
            o.optJSONObject("over")?.let { g ->
                gameOver = when (g.optString("w")) {
                    "true" -> GameOver(true, g.optString("r"))
                    "false" -> GameOver(false, g.optString("r"))
                    else -> GameOver(null, g.optString("r"))
                }
            }
            if (gameOver == null && turn == aiSide) triggerAI(null)
        } catch (e: Exception) {
            // 损坏则忽略，使用默认新局
        }
    }

    private fun parseNotation(s: String): Move? {
        val parts = s.split("-")
        if (parts.size != 2) return null
        val f = Move.parseCoord(parts[0]) ?: return null
        val t = Move.parseCoord(parts[1]) ?: return null
        return Move(f.first, f.second, t.first, t.second)
    }
}