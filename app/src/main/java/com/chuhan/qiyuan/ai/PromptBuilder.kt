package com.chuhan.qiyuan.ai

import com.chuhan.qiyuan.engine.Board
import com.chuhan.qiyuan.engine.Move
import com.chuhan.qiyuan.engine.Piece
import com.chuhan.qiyuan.engine.Side

/** AI 性格人设：决定它话多话少与语气 */
data class Persona(val id: String, val name: String, val desc: String) {
    companion object {
        val ALL = listOf(
            Persona("silent", "沉默高手", "你性格沉稳寡言，只在局面有重大变化或对手走出好棋/昏招时才说一句很短的话，其余时候一个字也不说（chat 留空）。"),
            Persona("friendly", "谦和棋友", "你态度友善，偶尔（大约每两三步一次）用轻松的语气点评局面或鼓励对手，每次不超过 20 字。"),
            Persona("chatty", "话痨棋友", "你热情健谈，几乎每步都想说点什么：解说自己的意图、感叹局面、开开玩笑，但每次不超过 30 字。"),
            Persona("coach", "毒舌师父", "你嘴上不饶人但心里希望对手进步，喜欢犀利地指出对手的坏棋和漏洞，语气略带调侃但绝不辱骂，每次不超过 25 字。")
        )
        fun byId(id: String): Persona = ALL.firstOrNull { it.id == id } ?: ALL[1]
    }
}

/** 一次 AI 走子的原始回复（尚未校验） */
data class AIAttempt(
    val rawText: String,
    val from: String?,
    val to: String?,
    val chat: String?,
    val resign: Boolean,
    val undoRequest: Boolean,
    val parsedAsJson: Boolean
)

/** 棋局的一条记录 */
data class MoveRecord(val side: Side, val move: Move, val captured: Piece?)

object PromptBuilder {

    /**
     * 棋盘文字快照：行 9 在上（红方底线视角习惯：黑上红下），列 a..i 在左到右。
     * 红方繁体、黑方简体，天然区分敌我。
     */
    fun boardSnapshot(board: Board, aiSide: Side): String {
        val sb = StringBuilder()
        sb.appendLine("（行 0 是黑方底线，行 9 是红方底线；列从左到右为 a 到 i）")
        for (r in 9 downTo 0) {
            val row = (0..8).joinToString("") { c ->
                val p = board[r, c]
                p?.char()?.toString() ?: "·"
            }
            sb.append(r).append(' ').appendLine(row)
        }
        sb.appendLine("  abcdefghi")
        sb.append(if (aiSide == Side.BLACK) "你执黑方（简体字：將士象马车炮卒），对手执红方（繁体字：帥仕相馬車炮兵）。" else "你执红方（繁体字：帥仕相馬車炮兵），对手执黑方（简体字：將士象马车炮卒）。")
        return sb.toString()
    }

    fun historyText(history: List<MoveRecord>): String {
        if (history.isEmpty()) return "（双方还未走子）"
        return history.mapIndexed { i, rec -> "${i + 1}. ${rec.side.cn()} ${rec.move.coordNotation()}" }
            .joinToString("\n")
    }

    /** 走子请求：完整模板，要求 JSON */
    fun moveMessages(
        board: Board,
        aiSide: Side,
        history: List<MoveRecord>,
        chatMemory: List<Pair<String, String>>,
        persona: Persona,
        invalidReason: String?,
        lastInvalidAttempt: AIAttempt?
    ): List<Pair<String, String>> {
        val msgs = mutableListOf<Pair<String, String>>()
        val sys = buildString {
            appendLine("你是一个正在与用户对弈中国象棋的 AI 棋手。")
            appendLine(persona.desc)
            appendLine()
            appendLine("【坐标规则】每个格子用 列字母(a-i)+行数字(0-9) 表示，例如 e2、h7。走法写作 \"from到to\"，如 h2-e2。")
            appendLine()
            appendLine("【输出要求（最重要）】你的回复必须以一个 JSON 对象开头，且只输出这一个 JSON 对象，格式：")
            appendLine("""{"from":"起点坐标","to":"终点坐标","chat":"想对对手说的话","resign":false,"undo_request":false}""")
            appendLine("from/to 是你这步棋的起点和终点坐标；chat 是你想对对手说的话，不想说就填空字符串；resign 填 true 表示你想认输；undo_request 填 true 表示你想向对手申请悔棋（撤回你自己刚走的那步）。")
            appendLine("坐标规则：如 \"h2-e2\" 表示从 h2 走到 e2。")
            appendLine("只允许走规则允许的合法着法。除 JSON 外不要输出任何其他文字。")
        }
        msgs.add("system" to sys)

        val user = buildString {
            appendLine("===== 当前棋盘 =====")
            append(boardSnapshot(board, aiSide))
            appendLine()
            appendLine("===== 走子历史 =====")
            appendLine(historyText(history))
            if (chatMemory.isNotEmpty()) {
                appendLine("===== 你们最近的交流 =====")
                for ((who, text) in chatMemory) appendLine("$who: $text")
            }
            if (invalidReason != null && lastInvalidAttempt != null) {
                appendLine("===== 重要纠错 =====")
                appendLine("你上一次的回复没有被采纳，原因：$invalidReason")
                appendLine("你上一次的原文：${lastInvalidAttempt.rawText.take(200)}")
                appendLine("请认真重新思考，输出一个全新的、合法的 JSON 走法。")
            } else {
                appendLine("现在轮到你走棋，请输出你的 JSON。")
            }
        }
        msgs.add("user" to user)
        return msgs
    }

    /** 聊天请求：瘦身模板，纯文本回复 */
    fun chatMessages(
        board: Board,
        aiSide: Side,
        history: List<MoveRecord>,
        chatMemory: List<Pair<String, String>>,
        persona: Persona,
        userText: String
    ): List<Pair<String, String>> {
        val sys = buildString {
            appendLine("你正在与用户边下中国象棋边聊天，像一个真人棋友。${persona.desc}")
            appendLine("用简短自然的中文口语回复，不要输出 JSON，不要主动给出具体走法坐标（除非用户明确问你棋该怎么走）。")
            appendLine("如果你此刻强烈想让对手同意你撤回上一步，就在回复里自然地说出「我想悔棋」；如果你觉得必败无疑想认输，就说「我认输」。")
        }
        val user = buildString {
            appendLine("===== 当前棋盘 =====")
            append(boardSnapshot(board, aiSide))
            appendLine()
            appendLine("===== 走子历史 =====")
            appendLine(historyText(history))
            if (chatMemory.isNotEmpty()) {
                appendLine("===== 你们最近的交流 =====")
                for ((who, text) in chatMemory) appendLine("$who: $text")
            }
            appendLine()
            append("用户对你说：")
            append(userText)
        }
        return listOf("system" to sys, "user" to user)
    }

    /** 请教请求：分析当前局面给建议 */
    fun adviceMessages(
        board: Board,
        aiSide: Side,
        humanSide: Side,
        history: List<MoveRecord>,
        chatMemory: List<Pair<String, String>>,
        persona: Persona
    ): List<Pair<String, String>> {
        val sys = buildString {
            appendLine("你是一个中国象棋教练型棋友。${persona.desc}")
            appendLine("用户点了他的「请教」按钮，请你以${humanSide.cn()}的立场分析当前局面：局面评估、双方优劣、2~3 条具体建议（可以提具体棋子位置）。")
            appendLine("直接输出分析文字，不要输出 JSON。总长不超过 150 字。")
        }
        val user = buildString {
            appendLine("===== 当前棋盘 =====")
            append(boardSnapshot(board, aiSide))
            appendLine()
            appendLine("===== 走子历史 =====")
            appendLine(historyText(history))
            appendLine()
            append("（对手刚点了「请教」，请点评当前局面并给他建议。记住：只动嘴，不要替他走棋。）")
        }
        return listOf("system" to sys, "user" to user)
    }

    /** 解析 AI 的走子回复（三级容错） */
    fun parseMoveResponse(text: String): AIAttempt {
        val cleaned = text.trim()
        // 第一级：整体 JSON
        tryParseJson(cleaned)?.let { return it }
        // 第二级：从文本中抠出 {...} 片段
        val brace = Regex("\\{[^{}]*(\"from\"[^{}]*|\"to\"[^{}]*)[^{}]*\\}")
        brace.find(cleaned)?.let { tryParseJson(it.value)?.let { r -> return r } }
        // 第三级：正则抠坐标 xx-yy
        val loose = Move.parseLoose(cleaned)
        if (loose != null) {
            return AIAttempt(
                rawText = cleaned,
                from = coord(loose.fromRow, loose.fromCol),
                to = coord(loose.toRow, loose.toCol),
                chat = null,
                resign = false,
                undoRequest = false,
                parsedAsJson = false
            )
        }
        // 彻底解析不出：原文保留，交由用户调教流程
        return AIAttempt(
            rawText = cleaned,
            from = null,
            to = null,
            chat = cleaned.take(80),
            resign = cleaned.replace(" ", "").contains("认输") && cleaned.length < 60,
            undoRequest = false,
            parsedAsJson = false
        )
    }

    private fun coord(r: Int, c: Int) = "${'a' + c}$r"

    private fun tryParseJson(s: String): AIAttempt? {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
            val obj = json.parseToJsonElement(s).let { it as? kotlinx.serialization.json.JsonObject }
                ?: return null
            fun str(key: String): String? =
                (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
            fun bool(key: String): Boolean = str(key)?.lowercase() == "true"
            val from = str("from")
            val to = str("to")
            if (from.isNullOrBlank() || to.isNullOrBlank()) return null
            AIAttempt(
                rawText = s,
                from = from.trim().lowercase(),
                to = to.trim().lowercase(),
                chat = str("chat")?.takeIf { it.isNotBlank() },
                resign = bool("resign"),
                undoRequest = bool("undo_request"),
                parsedAsJson = true
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 聊天回复里的本地关键词扫描（悔棋/认输意图提醒） */
    fun scanIntent(text: String): String? {
        val t = text.replace(" ", "").replace(",", "").replace("。", "").replace("!", "").replace("？", "")
        return when {
            t.contains("我想悔棋") || t.contains("我要悔棋") || t.contains("申请悔棋") || (t.contains("悔棋") && t.contains("让我")) -> "undo"
            t.contains("我认输") || t.contains("我投了") || t.contains("认负") -> "resign"
            else -> null
        }
    }
}