import com.chuhan.qiyuan.engine.*
import com.chuhan.qiyuan.ai.*

var pass = 0
var fail = 0
val failures = mutableListOf<String>()
fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) pass++ else { fail++; failures.add(name + "  " + detail) }
}

fun setupBoard(pieces: List<Triple<Int, Int, Piece>>): Board {
    val b = Board.initial().copy()
    for (r in 0..9) for (c in 0..8) b.place(r, c, null)
    for ((r, c, p) in pieces) b.place(r, c, p)
    return b
}
fun P(side: Side, t: PieceType) = Piece(side, t)
fun goes(b: Board, r: Int, c: Int, tr: Int, tc: Int) =
    XiangqiRules.pieceMoves(b, r, c).any { it.toRow == tr && it.toCol == tc }

// ============ 一、棋子走法 ============
fun testPieceRules() {
    val red = Side.RED
    val m1 = setupBoard(listOf(Triple(7, 1, P(red, PieceType.HORSE))))
    check("马走日字-左上", goes(m1, 7, 1, 5, 0))
    check("马走日字-右下", goes(m1, 7, 1, 8, 3))
    check("马不越界", !goes(m1, 7, 1, 6, -1) && !goes(m1, 7, 1, 9, 1))
    val m1b = setupBoard(listOf(Triple(7, 1, P(red, PieceType.HORSE)), Triple(6, 1, P(red, PieceType.PAWN))))
    check("蹩马腿-上方向被封", !goes(m1b, 7, 1, 5, 0) && !goes(m1b, 7, 1, 5, 2))
    check("蹩马腿-不影响下方向", goes(m1b, 7, 1, 9, 0))

    val e1 = setupBoard(listOf(Triple(9, 2, P(red, PieceType.ELEPHANT))))
    check("象走田字", goes(e1, 9, 2, 7, 0) && goes(e1, 9, 2, 7, 4))
    val e2 = setupBoard(listOf(Triple(5, 4, P(red, PieceType.ELEPHANT))))
    check("象不过河", !goes(e2, 5, 4, 3, 2) && !goes(e2, 5, 4, 3, 6))
    val e3 = setupBoard(listOf(Triple(9, 2, P(red, PieceType.ELEPHANT)), Triple(8, 3, P(red, PieceType.PAWN))))
    check("塞象眼", !goes(e3, 9, 2, 7, 4) && goes(e3, 9, 2, 7, 0))

    val a1 = setupBoard(listOf(Triple(9, 4, P(red, PieceType.ADVISOR)), Triple(9, 3, P(red, PieceType.GENERAL))))
    check("士不出九宫", XiangqiRules.pieceMoves(a1, 9, 4).all { XiangqiRules.inPalace(Side.RED, it.toRow, it.toCol) })
    check("将不出九宫", XiangqiRules.pieceMoves(a1, 9, 3).all { XiangqiRules.inPalace(Side.RED, it.toRow, it.toCol) })

    val p1 = setupBoard(listOf(Triple(6, 4, P(red, PieceType.PAWN))))
    check("红兵未过河不能横走", !goes(p1, 6, 4, 6, 3) && !goes(p1, 6, 4, 6, 5))
    val p2 = setupBoard(listOf(Triple(4, 4, P(red, PieceType.PAWN))))
    check("红兵过河能横走", goes(p2, 4, 4, 4, 5))
    check("红兵不能后退", !goes(p2, 4, 4, 5, 4))
    val p3 = setupBoard(listOf(Triple(3, 4, P(Side.BLACK, PieceType.PAWN))))
    check("黑卒未过河不能横走", !goes(p3, 3, 4, 3, 3))
    val p4 = setupBoard(listOf(Triple(5, 4, P(Side.BLACK, PieceType.PAWN))))
    check("黑卒过河能横走", goes(p4, 5, 4, 5, 3))

    val c1 = setupBoard(listOf(
        Triple(7, 1, P(red, PieceType.CANNON)),
        Triple(7, 3, P(Side.BLACK, PieceType.PAWN)),
        Triple(7, 5, P(Side.BLACK, PieceType.PAWN))))
    check("炮平移不受挡", goes(c1, 7, 1, 7, 2))
    check("炮不隔子吃", !goes(c1, 7, 1, 7, 3))
    check("炮隔一子可吃", goes(c1, 7, 1, 7, 5))
    check("炮隔子后不能平移", !goes(c1, 7, 1, 7, 6))

    val r1 = setupBoard(listOf(Triple(7, 1, P(red, PieceType.CHARIOT)), Triple(7, 4, P(Side.BLACK, PieceType.PAWN))))
    check("车直走", goes(r1, 7, 1, 7, 3))
    check("车吃子", goes(r1, 7, 1, 7, 4))
    check("车不越子", !goes(r1, 7, 1, 7, 5))
}

// ============ 二、将军 / 将死 / 困毙 ============
fun testCheckAndMate() {
    val f1 = setupBoard(listOf(Triple(9, 4, P(Side.RED, PieceType.GENERAL)), Triple(0, 4, P(Side.BLACK, PieceType.GENERAL))))
    check("将帅对脸=被将(红)", XiangqiRules.isInCheck(f1, Side.RED))
    check("将帅对脸=被将(黑)", XiangqiRules.isInCheck(f1, Side.BLACK))
    val f2 = setupBoard(listOf(
        Triple(9, 4, P(Side.RED, PieceType.GENERAL)), Triple(0, 4, P(Side.BLACK, PieceType.GENERAL)),
        Triple(5, 4, P(Side.RED, PieceType.PAWN))))
    check("有子遮挡解除对脸", !XiangqiRules.isInCheck(f2, Side.RED))

    val b3 = setupBoard(listOf(
        Triple(9, 4, P(Side.RED, PieceType.GENERAL)), Triple(9, 3, P(Side.RED, PieceType.ADVISOR)),
        Triple(7, 3, P(Side.RED, PieceType.CHARIOT)), Triple(0, 4, P(Side.BLACK, PieceType.CHARIOT))))
    check("黑车叫将", XiangqiRules.isInCheck(b3, Side.RED))
    check("送将-不挡无效", XiangqiRules.validateMove(b3, Side.RED, Move(7, 3, 6, 3)) != null)
    check("仕斜走到将线恰好垫将合法", XiangqiRules.validateMove(b3, Side.RED, Move(9, 3, 8, 4)) == null)
    check("垫车解将合法", XiangqiRules.validateMove(b3, Side.RED, Move(7, 3, 7, 4)) == null)

    val mate = setupBoard(listOf(
        Triple(0, 4, P(Side.BLACK, PieceType.GENERAL)),
        Triple(0, 0, P(Side.RED, PieceType.CHARIOT)), Triple(0, 8, P(Side.RED, PieceType.CHARIOT)),
        Triple(1, 0, P(Side.RED, PieceType.CHARIOT)), Triple(9, 4, P(Side.RED, PieceType.GENERAL))))
    check("将死-黑被将军", XiangqiRules.isInCheck(mate, Side.BLACK))
    check("将死-黑无合法棋", !XiangqiRules.hasAnyLegalMove(mate, Side.BLACK))

    val stale = setupBoard(listOf(
        Triple(0, 4, P(Side.BLACK, PieceType.GENERAL)),
        Triple(0, 3, P(Side.BLACK, PieceType.ADVISOR)), Triple(0, 5, P(Side.BLACK, PieceType.ADVISOR)),
        Triple(1, 4, P(Side.BLACK, PieceType.ELEPHANT)),
        Triple(2, 3, P(Side.RED, PieceType.CHARIOT)), Triple(2, 5, P(Side.RED, PieceType.CHARIOT)),
        Triple(3, 3, P(Side.RED, PieceType.HORSE)), Triple(9, 4, P(Side.RED, PieceType.GENERAL))))
    check("困毙-黑未被将军", !XiangqiRules.isInCheck(stale, Side.BLACK))
    check("困毙-黑无合法棋", !XiangqiRules.hasAnyLegalMove(stale, Side.BLACK))
}

// ============ 三、validateMove 人话报错 ============
fun testValidate() {
    val init = Board.initial()
    check("越界报错", XiangqiRules.validateMove(init, Side.RED, Move(10, 0, 9, 0)) != null)
    check("空起点报错", XiangqiRules.validateMove(init, Side.RED, Move(5, 4, 5, 5))?.contains("没有棋子") == true)
    check("别人的子报错", XiangqiRules.validateMove(init, Side.RED, Move(0, 1, 2, 2))?.contains("不是你的棋子") == true)
    check("终点己子报错", XiangqiRules.validateMove(init, Side.RED, Move(9, 0, 9, 1))?.contains("自己的棋子") == true)
    val leg = setupBoard(listOf(Triple(9, 1, P(Side.RED, PieceType.HORSE)), Triple(8, 1, P(Side.RED, PieceType.PAWN))))
    check("蹩腿报错", XiangqiRules.validateMove(leg, Side.RED, Move(9, 1, 7, 2))?.contains("蹩") == true)
    val can = setupBoard(listOf(Triple(7, 1, P(Side.RED, PieceType.CANNON)), Triple(7, 4, P(Side.BLACK, PieceType.PAWN))))
    check("炮无隔子吃报错", XiangqiRules.validateMove(can, Side.RED, Move(7, 1, 7, 4))?.contains("炮") == true)
    check("合法走法返回null", XiangqiRules.validateMove(init, Side.RED, Move(7, 1, 7, 4)) == null)
    check("初始局面红方有合法棋", XiangqiRules.legalMoves(init, Side.RED).isNotEmpty())
    check("legalMoves与validate一致", XiangqiRules.legalMoves(init, Side.RED).all { XiangqiRules.validateMove(init, Side.RED, it) == null })
}

// ============ 四、AI 回复三级解析 ============
fun testParser() {
    val j1 = PromptBuilder.parseMoveResponse("{\"from\":\"h2\",\"to\":\"e2\",\"chat\":\"稳住\",\"resign\":false,\"undo_request\":false}")
    check("一级-纯JSON", j1.parsedAsJson && j1.from == "h2" && j1.to == "e2" && j1.chat == "稳住")
    val j2 = PromptBuilder.parseMoveResponse("```json\n{\"from\":\"b0\",\"to\":\"c2\",\"chat\":\"\",\"resign\":false,\"undo_request\":false}\n```")
    check("二级-markdown包裹", j2.parsedAsJson && j2.from == "b0" && j2.to == "c2")
    val j3 = PromptBuilder.parseMoveResponse("我想想…… {\"from\":\"h2\",\"to\":\"e2\",\"chat\":\"\",\"resign\":false,\"undo_request\":false} 就这么走")
    check("二级-夹在闲聊里", j3.parsedAsJson && j3.from == "h2")
    val j4 = PromptBuilder.parseMoveResponse("{\"from\":\"a0\",\"to\":\"a1\",\"undo_request\":true}")
    check("悔棋申请字段", j4.undoRequest)
    val t1 = PromptBuilder.parseMoveResponse("这步棋走得妙啊，佩服佩服。")
    check("纯聊天不误判", !t1.parsedAsJson && t1.from == null && t1.chat != null)
    val t2 = PromptBuilder.parseMoveResponse("我看 h2-e2 不错")
    check("三级-正则抠坐标", !t2.parsedAsJson && t2.from == "h2" && t2.to == "e2")
    val t3 = PromptBuilder.parseMoveResponse("我认输，这局输了。")
    check("认输识别", t3.from == null && t3.resign)
    val t4 = PromptBuilder.parseMoveResponse("<think>让我想想</think>{\"from\":\"h2\",\"to\":\"e2\",\"chat\":\"\",\"resign\":false,\"undo_request\":false}")
    check("带think标签可解析", t4.parsedAsJson && t4.from == "h2")
    val t5 = PromptBuilder.parseMoveResponse("？？？")
    check("彻底失败原文保留", !t5.parsedAsJson && t5.from == null && t5.rawText == "？？？")
}

// ============ 五、棋盘数据结构 ============
fun testBoard() {
    check("初始32子", Board.initial().key().count { it != '.' } == 32)
    val b1 = Board.initial()
    val b2 = b1.moved(Move(7, 1, 7, 4))
    check("原局面不被修改", b1[7, 1] != null && b1[7, 4] == null && b2[7, 1] == null && b2[7, 4] != null)
    check("局面指纹可区分", b1.key() != b2.key())
    check("坐标解析a0", Move.parseCoord("a0") == Pair(0, 0))
    check("坐标解析i9", Move.parseCoord("i9") == Pair(9, 8))
    check("非法坐标j0", Move.parseCoord("j0") == null)
    check("非法坐标a10", Move.parseCoord("a10") == null)
}

fun main() {
    testPieceRules()
    testCheckAndMate()
    testValidate()
    testParser()
    testBoard()
    println("==============================")
    println("PASS: " + pass + "   FAIL: " + fail)
    if (failures.isNotEmpty()) {
        println("--- 失败明细 ---")
        failures.forEach { println("FAIL: " + it) }
    }
}
