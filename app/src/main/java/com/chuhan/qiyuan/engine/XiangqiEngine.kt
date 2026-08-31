package com.chuhan.qiyuan.engine

/** 棋子阵营 */
enum class Side {
    RED, BLACK;

    fun opposite(): Side = if (this == RED) BLACK else RED
    fun cn(): String = if (this == RED) "红方" else "黑方"
}

/**
 * 棋子类型。redChar/blackChar 同时用于 UI 显示与 AI 提示词中的阵营区分：
 * 红方用繁体，黑方用简体，模型不会搞混敌我。
 */
enum class PieceType(val redChar: Char, val blackChar: Char, val cnName: String) {
    GENERAL('帥', '將', "将/帅"),
    ADVISOR('仕', '士', "士"),
    ELEPHANT('相', '象', "象"),
    HORSE('馬', '马', "马"),
    CHARIOT('車', '车', "车"),
    CANNON('炮', '炮', "炮"),
    PAWN('兵', '卒', "兵/卒");
}

data class Piece(val side: Side, val type: PieceType) {
    fun char(): Char = if (side == Side.RED) type.redChar else type.blackChar
}

data class Move(val fromRow: Int, val fromCol: Int, val toRow: Int, val toCol: Int) {
    /** 程序坐标：列 a-i，行 0-9（0 为黑方底线）。用于 AI 通信。 */
    fun coordNotation(): String = coordOf(fromRow, fromCol) + "-" + coordOf(toRow, toCol)

    companion object {
        fun coordOf(r: Int, c: Int): String = "${'a' + c}$r"

        fun parseCoord(s: String): Pair<Int, Int>? {
            if (s.length != 2) return null
            val c = s[0] - 'a'
            val r = s[1] - '0'
            if (c !in 0..8 || r !in 0..9) return null
            return r to c
        }

        /** 从自由文本里抠出 "xx-yy" 形式的走法（解析容错的最后一级） */
        fun parseLoose(text: String): Move? {
            val regex = Regex("([a-i][0-9])\\s*-\\s*([a-i][0-9])")
            val m = regex.find(text) ?: return null
            val from = parseCoord(m.groupValues[1]) ?: return null
            val to = parseCoord(m.groupValues[2]) ?: return null
            return Move(from.first, from.second, to.first, to.second)
        }
    }
}

/**
 * 棋盘。row 0 = 黑方底线（棋盘顶部），row 9 = 红方底线（棋盘底部）。
 * 不可变风格：所有"走子"都返回新实例，方便 Compose 状态管理与悔棋。
 */
class Board private constructor(private val cells: Array<Array<Piece?>>) {

    operator fun get(r: Int, c: Int): Piece? = cells[r][c]

    fun copy(): Board {
        val b = Array(10) { arrayOfNulls<Piece>(9) }
        for (r in 0..9) for (c in 0..8) b[r][c] = cells[r][c]
        return Board(b)
    }

    /** 内部可变操作：仅在 copy 出的新实例上调用 */
    fun place(r: Int, c: Int, p: Piece?) {
        if (r in 0..9 && c in 0..8) cells[r][c] = p
    }

    fun moved(from: Move): Board {
        val b = copy()
        b.place(from.toRow, from.toCol, cells[from.fromRow][from.fromCol])
        b.place(from.fromRow, from.fromCol, null)
        return b
    }

    /** 局面指纹（用棋子字符，天然区分红繁/黑简），用于重复局面判和 */
    fun key(): String = buildString {
        for (r in 0..9) for (c in 0..8) append(cells[r][c]?.char() ?: '.')
    }

    companion object {
        fun initial(): Board {
            val b = Array(10) { arrayOfNulls<Piece>(9) }
            val back = listOf(
                PieceType.CHARIOT, PieceType.HORSE, PieceType.ELEPHANT, PieceType.ADVISOR,
                PieceType.GENERAL, PieceType.ADVISOR, PieceType.ELEPHANT, PieceType.HORSE,
                PieceType.CHARIOT
            )
            back.forEachIndexed { c, t -> b[0][c] = Piece(Side.BLACK, t) }
            b[2][1] = Piece(Side.BLACK, PieceType.CANNON)
            b[2][7] = Piece(Side.BLACK, PieceType.CANNON)
            for (c in 0..8 step 2) b[3][c] = Piece(Side.BLACK, PieceType.PAWN)
            back.forEachIndexed { c, t -> b[9][c] = Piece(Side.RED, t) }
            b[7][1] = Piece(Side.RED, PieceType.CANNON)
            b[7][7] = Piece(Side.RED, PieceType.CANNON)
            for (c in 0..8 step 2) b[6][c] = Piece(Side.RED, PieceType.PAWN)
            return Board(b)
        }
    }
}

/**
 * 本地规则引擎（裁判）。全部走法生成与合法性判定在此完成，AI 的走法必须经此校验。
 */
object XiangqiRules {

    fun inBoard(r: Int, c: Int) = r in 0..9 && c in 0..8

    fun inPalace(side: Side, r: Int, c: Int) =
        c in 3..5 && (if (side == Side.RED) r in 7..9 else r in 0..2)

    private fun ownHalf(side: Side, r: Int) = if (side == Side.RED) r >= 5 else r <= 4

    private val ORTHO = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** 单子伪合法走法（不含自将过滤） */
    fun pieceMoves(board: Board, r: Int, c: Int): List<Move> {
        val p = board[r, c] ?: return emptyList()
        val moves = mutableListOf<Move>()
        fun add(tr: Int, tc: Int) {
            if (!inBoard(tr, tc)) return
            val target = board[tr, tc]
            if (target == null || target.side != p.side) moves.add(Move(r, c, tr, tc))
        }
        when (p.type) {
            PieceType.CHARIOT -> for ((dr, dc) in ORTHO) {
                var tr = r + dr; var tc = c + dc
                while (inBoard(tr, tc)) {
                    val t = board[tr, tc]
                    if (t == null) moves.add(Move(r, c, tr, tc))
                    else { if (t.side != p.side) moves.add(Move(r, c, tr, tc)); break }
                    tr += dr; tc += dc
                }
            }

            PieceType.CANNON -> for ((dr, dc) in ORTHO) {
                var tr = r + dr; var tc = c + dc
                var jumped = false
                while (inBoard(tr, tc)) {
                    val t = board[tr, tc]
                    if (!jumped) {
                        if (t == null) moves.add(Move(r, c, tr, tc)) else jumped = true
                    } else if (t != null) {
                        if (t.side != p.side) moves.add(Move(r, c, tr, tc))
                        break
                    }
                    tr += dr; tc += dc
                }
            }

            PieceType.HORSE -> for ((dr, dc) in listOf(2 to 1, 2 to -1, -2 to 1, -2 to -1, 1 to 2, 1 to -2, -1 to 2, -1 to -2)) {
                val legR = if (kotlin.math.abs(dr) == 2) r + dr / 2 else r
                val legC = if (kotlin.math.abs(dc) == 2) c + dc / 2 else c
                if (inBoard(legR, legC) && board[legR, legC] == null) add(r + dr, c + dc)
            }

            PieceType.ELEPHANT -> for ((dr, dc) in listOf(2 to 2, 2 to -2, -2 to 2, -2 to -2)) {
                val tr = r + dr; val tc = c + dc
                val eyeR = r + dr / 2; val eyeC = c + dc / 2
                if (inBoard(tr, tc) && ownHalf(p.side, tr) && board[eyeR, eyeC] == null) add(tr, tc)
            }

            PieceType.ADVISOR -> for ((dr, dc) in listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)) {
                val tr = r + dr; val tc = c + dc
                if (inPalace(p.side, tr, tc)) add(tr, tc)
            }

            PieceType.GENERAL -> for ((dr, dc) in ORTHO) {
                val tr = r + dr; val tc = c + dc
                if (inPalace(p.side, tr, tc)) add(tr, tc)
            }

            PieceType.PAWN -> {
                val fwd = if (p.side == Side.RED) -1 else 1
                add(r + fwd, c)
                val crossed = if (p.side == Side.RED) r <= 4 else r >= 5
                if (crossed) { add(r, c + 1); add(r, c - 1) }
            }
        }
        return moves
    }

    fun findGeneral(board: Board, side: Side): Pair<Int, Int>? {
        for (r in 0..9) for (c in 0..8) {
            val p = board[r, c]
            if (p != null && p.side == side && p.type == PieceType.GENERAL) return r to c
        }
        return null
    }

    /** side 是否被将军（含将帅照面） */
    fun isInCheck(board: Board, side: Side): Boolean {
        val g = findGeneral(board, side) ?: return true
        val opp = findGeneral(board, side.opposite())
        if (opp != null && opp.second == g.second) {
            var clear = true
            for (r in minOf(g.first, opp.first) + 1 until maxOf(g.first, opp.first)) {
                if (board[r, g.second] != null) { clear = false; break }
            }
            if (clear) return true
        }
        for (r in 0..9) for (c in 0..8) {
            val p = board[r, c] ?: continue
            if (p.side != side) {
                for (m in pieceMoves(board, r, c)) {
                    if (m.toRow == g.first && m.toCol == g.second) return true
                }
            }
        }
        return false
    }

    fun piecesOf(board: Board, side: Side): List<Pair<Int, Int>> = buildList {
        for (r in 0..9) for (c in 0..8) {
            val p = board[r, c]
            if (p != null && p.side == side) add(r to c)
        }
    }

    /** 完全合法走法（走后自将不被将军） */
    fun legalMoves(board: Board, side: Side): List<Move> =
        piecesOf(board, side).flatMap { (r, c) -> pieceMoves(board, r, c) }
            .filter { m -> !isInCheck(board.moved(m), side) }

    fun hasAnyLegalMove(board: Board, side: Side): Boolean {
        for ((r, c) in piecesOf(board, side)) {
            for (m in pieceMoves(board, r, c)) {
                if (!isInCheck(board.moved(m), side)) return true
            }
        }
        return false
    }

    /**
     * 校验 AI 给出的走法并给出人话原因。返回 null 表示合法。
     */
    fun validateMove(board: Board, side: Side, move: Move): String? {
        val (fr, fc) = move.fromRow to move.fromCol
        val (tr, tc) = move.toRow to move.toCol
        if (!inBoard(fr, fc) || !inBoard(tr, tc)) return "坐标超出棋盘"
        val p = board[fr, fc] ?: return "起点 ${Move.coordOf(fr, fc)} 上没有棋子"
        if (p.side != side) return "${Move.coordOf(fr, fc)} 上的 ${p.char()} 不是你的棋子"
        if (fr == tr && fc == tc) return "起点和终点相同"
        val target = board[tr, tc]
        if (target != null && target.side == side) return "终点 ${Move.coordOf(tr, tc)} 上有自己的棋子 ${target.char()}"
        if (move !in pieceMoves(board, fr, fc)) {
            return when (p.type) {
                PieceType.HORSE -> "${p.char()} 不能这样走（可能被蹩了马腿）"
                PieceType.ELEPHANT -> "${p.char()} 不能这样走（可能塞了象眼或不能过河）"
                PieceType.CANNON -> "${p.char()} 的走法不对（炮要隔子吃子）"
                PieceType.CHARIOT -> "${p.char()} 不能越子行走"
                PieceType.ADVISOR -> "${p.char()} 只能在九宫内斜走一格"
                PieceType.GENERAL -> "${p.char()} 只能在九宫内直走一格"
                PieceType.PAWN -> "${p.char()} 只能向前走，过河后才能横走"
            }
        }
        if (isInCheck(board.moved(move), side)) return "这样走会让你的将/帅被将军"
        return null
    }
}