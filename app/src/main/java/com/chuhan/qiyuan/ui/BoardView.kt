package com.chuhan.qiyuan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.chuhan.qiyuan.engine.Board
import com.chuhan.qiyuan.engine.Piece
import com.chuhan.qiyuan.engine.Side
import kotlin.math.abs

/**
 * Canvas 自绘中国象棋棋盘：木纹底 + 棋盘线 + 立体棋子。
 * 所有外观 100% 代码生成，无图片资源。
 */
@Composable
fun BoardView(
    board: Board,
    humanSide: Side,
    selected: Pair<Int, Int>?,
    legalTargets: List<Pair<Int, Int>>,
    lastMove: Pair<Pair<Int, Int>, Pair<Int, Int>>?,
    onTap: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val sizePx = with(density) { maxWidth.toPx() }
        val cell = sizePx / 10f
        val flip = humanSide == Side.BLACK
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .testTag("board")
                .pointerInput(board, selected, flip) {
                    detectTapGestures { offset ->
                        val gc = ((offset.x - cell / 2) / cell).toInt()
                        val gr = ((offset.y - cell / 2) / cell).toInt()
                        if (gr in 0..9 && gc in 0..8) {
                            val r = if (flip) 9 - gr else gr
                            val c = if (flip) 8 - gc else gc
                            onTap(r, c)
                        }
                    }
                }
        ) {
            drawBoard()
            // 上一步高亮
            lastMove?.let { (f, t) ->
                for (p in listOf(f, t)) {
                    val g = toGrid(p, flip)
                    drawCircle(
                        color = Gold.copy(alpha = 0.35f),
                        radius = cell * 0.42f,
                        center = gridCenter(g.first, g.second)
                    )
                }
            }
            // 选中高亮
            selected?.let {
                val g = toGrid(it, flip)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Gold.copy(alpha = 0.9f), Gold.copy(alpha = 0.0f)),
                        center = gridCenter(g.first, g.second),
                        radius = cell * 0.62f
                    ),
                    radius = cell * 0.62f,
                    center = gridCenter(g.first, g.second)
                )
            }
            // 棋子
            for (r in 0..9) for (c in 0..8) {
                val p = board[r, c] ?: continue
                val g = toGrid(r to c, flip)
                drawPiece(p, g.first, g.second)
            }
            // 合法落点提示
            for (t in legalTargets) {
                val g = toGrid(t, flip)
                drawCircle(
                    color = Color(0xFF6BBF59),
                    radius = cell * 0.12f,
                    center = gridCenter(g.first, g.second)
                )
            }
        }
    }
}

private fun DrawScope.toGrid(pos: Pair<Int, Int>, flip: Boolean): Pair<Int, Int> =
    if (flip) (9 - pos.first) to (8 - pos.second) else pos

private fun DrawScope.cellSize(): Float = size.width / 10f

private fun DrawScope.gridCenter(gr: Int, gc: Int): Offset {
    val cell = cellSize()
    return Offset(cell * (gc + 1), cell * (gr + 1))
}

private fun DrawScope.drawBoard() {
    val cell = cellSize()
    val w = size.width
    val h = cell * 11f

    // 木质背景
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFD9A961), Color(0xFFC89653))),
        size = Size(w, h),
        cornerRadius = CornerRadius(cell * 0.15f)
    )
    // 木纹（伪随机曲线，确定性种子保证每次绘制一致）
    var seed = 42L
    fun rnd(): Float { seed = seed * 6364136223846793005L + 1442695040888963407L; return ((seed ushr 33).toInt() and 0x7fffffff) / 2147483647f }
    for (i in 0 until 26) {
        val y = rnd() * h
        val amp = cell * (0.02f + rnd() * 0.06f)
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(0f, y)
        var x = 0f
        var yy = y
        while (x < w) {
            x += cell * 0.4f
            yy += (rnd() - 0.5f) * amp
            path.lineTo(x, yy)
        }
        drawPath(path, Color(0x228A5A2B), style = Stroke(width = cell * 0.03f))
    }

    // 网格线：10 行 9 列
    val lineColor = Color(0xFF4A2F14)
    val stroke = Stroke(width = cell * 0.035f)
    fun pt(gr: Int, gc: Int) = gridCenter(gr, gc)
    for (gr in 0..9) drawLine(lineColor, pt(gr, 0), pt(gr, 8), stroke.width)
    for (gc in 0..8) {
        if (gc == 0 || gc == 8) drawLine(lineColor, pt(0, gc), pt(9, gc), stroke.width)
        else {
            drawLine(lineColor, pt(0, gc), pt(4, gc), stroke.width)
            drawLine(lineColor, pt(5, gc), pt(9, gc), stroke.width)
        }
    }
    // 九宫斜线
    drawLine(lineColor, pt(0, 3), pt(2, 5), stroke.width)
    drawLine(lineColor, pt(0, 5), pt(2, 3), stroke.width)
    drawLine(lineColor, pt(7, 3), pt(9, 5), stroke.width)
    drawLine(lineColor, pt(7, 5), pt(9, 3), stroke.width)
    // 外框加粗
    drawRoundRect(
        color = lineColor,
        topLeft = pt(0, 0) - Offset(cell * 0.12f, cell * 0.12f),
        size = Size(cell * 8.24f, cell * 9.24f),
        cornerRadius = CornerRadius(cell * 0.05f),
        style = Stroke(width = cell * 0.06f)
    )
}

private fun DrawScope.drawPiece(p: Piece, gr: Int, gc: Int) {
    val cell = cellSize()
    val center = gridCenter(gr, gc)
    val radius = cell * 0.42f
    val baseColor = if (p.side == Side.RED) Color(0xFFF7E7C6) else Color(0xFFF2E3C2)
    val edgeColor = if (p.side == Side.RED) Color(0xFF8A4A2A) else Color(0xFF3E3A33)
    // 投影
    drawCircle(color = Color(0x33000000), radius = radius * 1.04f, center = center + Offset(cell * 0.03f, cell * 0.045f))
    // 主体
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(baseColor.copy(alpha = 1f), Color(0xFFD9BE8C)),
            center = center - Offset(radius * 0.3f, radius * 0.3f),
            radius = radius * 1.5f
        ),
        radius = radius,
        center = center
    )
    // 描边双环
    drawCircle(color = edgeColor, radius = radius, center = center, style = Stroke(cell * 0.035f))
    drawCircle(color = edgeColor.copy(alpha = 0.6f), radius = radius * 0.82f, center = center, style = Stroke(cell * 0.02f))
    // 棋子字
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = radius * 1.15f
            color = if (p.side == Side.RED) 0xFFB32218.toInt() else 0xFF232A31.toInt()
            isFakeBoldText = true
            setShadowLayer(radius * 0.05f, 0f, radius * 0.03f, 0x66000000.toInt())
        }
        canvas.nativeCanvas.drawText(p.char().toString(), center.x, center.y + radius * 0.36f, paint)
    }
}

private operator fun Offset.minus(o: Offset) = Offset(x - o.x, y - o.y)