package com.chuhan.qiyuan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.chuhan.qiyuan.ai.Persona
import com.chuhan.qiyuan.engine.Side
import com.chuhan.qiyuan.engine.XiangqiRules
import kotlinx.coroutines.launch

// ========================= 主菜单 =========================
@Composable
fun MenuScreen(vm: GameViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("楚汉棋缘", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Gold)
        Text("与大模型棋友对弈", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text(vm.statsText, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = { vm.newGame(Side.RED) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("执红开局（我先走）", fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { vm.newGame(Side.BLACK) },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("执黑开局（AI 先走）", fontSize = 16.sp) }
        Spacer(Modifier.height(12.dp))
        if (vm.history.isNotEmpty() || vm.gameOver != null) {
            OutlinedButton(
                onClick = { vm.gotoScreen("game") },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("继续上局", fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = { vm.gotoScreen("settings") }) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("设置（API 密钥 / 模型 / 人设）")
        }
        if (vm.settings.apiKey.isBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("提示：首次使用请先到设置里填写 API Key", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

// ========================= 对局页 =========================
@Composable
fun GameScreen(vm: GameViewModel) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeSide = if (vm.playForAiMode) vm.aiSide else vm.humanSide
    val legalTargets = remember(vm.selected, vm.board, vm.turn, vm.playForAiMode) {
        val sel = vm.selected
        if (sel != null && vm.turn == activeSide && !vm.aiThinking && vm.pendingInvalid == null)
            XiangqiRules.legalMoves(vm.board, activeSide)
                .filter { it.fromRow == sel.first && it.fromCol == sel.second }
                .map { it.toRow to it.toCol }
        else emptyList()
    }
    val lastMove = vm.history.lastOrNull()?.let {
        Pair(Pair(it.move.fromRow, it.move.fromCol), Pair(it.move.toRow, it.move.toCol))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.gotoScreen("menu") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = if (vm.gameOver != null) "对局结束" else if (vm.turn == vm.humanSide) "轮到你（${vm.humanSide.cn()}）" else if (vm.aiThinking) "AI 思考中…" else "AI（${vm.aiSide.cn()}）",
                fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center
            )
            Text(Persona.byId(vm.personaId).name, fontSize = 12.sp, color = Gold)
            Spacer(Modifier.width(8.dp))
        }

        // 棋盘
        BoardView(
            board = vm.board,
            humanSide = vm.humanSide,
            selected = vm.selected,
            legalTargets = legalTargets,
            lastMove = lastMove,
            onTap = { r, c -> vm.onCellTap(r, c) },
            modifier = Modifier.fillMaxWidth().aspectRatio(10f / 11f).padding(horizontal = 4.dp)
        )

        // 操作按钮行
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { vm.userUndo() }, enabled = vm.canUserUndo(), modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Undo, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("悔棋", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { vm.askAdvice() }, enabled = !vm.chatThinking && vm.gameOver == null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("请教", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { vm.humanResign() }, enabled = vm.gameOver == null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Flag, contentDescription = null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("认输", fontSize = 13.sp)
            }
        }

        // 聊天流
        LaunchedEffect(vm.chat.size) {
            if (vm.chat.isNotEmpty()) listState.animateScrollToItem(vm.chat.size - 1)
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(vm.chat) { m ->
                when (m.who) {
                    "system" -> Text(
                        m.text, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                    )
                    "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(m.text, fontSize = 13.sp, color = Color(0xFF171412),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                                .background(Gold)
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(m.text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        // 输入行
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var input by remember { mutableStateOf("") }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("和棋友聊聊，或教它下棋…", fontSize = 13.sp) },
                maxLines = 3,
                enabled = vm.gameOver == null && !vm.chatThinking
            )
            IconButton(
                onClick = { if (input.isNotBlank()) { vm.sendChat(input); input = "" } },
                enabled = !vm.chatThinking && vm.gameOver == null
            ) {
                if (vm.chatThinking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = Gold)
            }
        }
    }

    // ---------- 各种弹窗 ----------
    vm.pendingInvalid?.let { inv ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 走了一步不合法的棋") },
            text = { Text("问题：${inv.reason}\n\n它说：${inv.attempt.rawText.take(160)}\n\n你可以在聊天框里教训它，然后点「重新思考」；或者直接替它走。") },
            confirmButton = { TextButton(onClick = { vm.retryAI() }) { Text("重新思考") } },
            dismissButton = { TextButton(onClick = { vm.startPlayForAi() }) { Text("替它走") } }
        )
    }
    if (vm.pendingUndo) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 申请悔棋") },
            text = { Text("它想撤回自己刚才那步。同意吗？") },
            confirmButton = { TextButton(onClick = { vm.approveUndo() }) { Text("同意") } },
            dismissButton = { TextButton(onClick = { vm.rejectUndo() }) { Text("拒绝") } }
        )
    }
    if (vm.pendingResign) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 想认输") },
            text = { Text("接受它的认输吗？") },
            confirmButton = { TextButton(onClick = { vm.acceptAIResign() }) { Text("接受") } },
            dismissButton = { TextButton(onClick = { vm.declineAIResign() }) { Text("不接受") } }
        )
    }
    vm.networkError?.let { err ->
        AlertDialog(
            onDismissRequest = { vm.dismissNetworkError() },
            title = { Text("网络出错") },
            text = { Text(err) },
            confirmButton = { TextButton(onClick = { vm.retryAfterError() }) { Text("重试") } },
            dismissButton = { TextButton(onClick = { vm.aiResignByError() }) { Text("判 AI 认输") } }
        )
    }
    vm.gameOver?.let { over ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(when (over.humanWon) { true -> "你赢了！🎉"; false -> "你输了"; null -> "和棋" }) },
            text = { Text(over.reason) },
            confirmButton = { TextButton(onClick = { vm.newGame(vm.humanSide) }) { Text("再来一局") } },
            dismissButton = { TextButton(onClick = { vm.gotoScreen("menu") }) { Text("回主页") } }
        )
    }
}

// ========================= 设置页 =========================
@Composable
fun SettingsScreen(vm: GameViewModel) {
    var endpoint by remember { mutableStateOf(vm.settings.endpoint) }
    var apiKey by remember { mutableStateOf(vm.settings.apiKey) }
    var model by remember { mutableStateOf(vm.settings.model) }
    var temperature by remember { mutableStateOf(vm.settings.temperature.toFloat()) }
    var timeout by remember { mutableStateOf(vm.settings.timeoutSec.toString()) }
    var personaId by remember { mutableStateOf(vm.settings.personaId) }
    var testResult by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.gotoScreen(if (vm.history.isNotEmpty()) "game" else "menu") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text("设置", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("API 端点", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("API Key（仅存本机）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("模型名", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        Text("Temperature：%.2f".format(temperature), fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..1.5f)
        Text("超时（秒）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        OutlinedTextField(value = timeout, onValueChange = { timeout = it }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(14.dp))
        Text("AI 性格（决定它话多话少）", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Persona.ALL.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { personaId = p.id }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = personaId == p.id, onClick = { personaId = p.id })
                Text(p.name, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(p.desc.take(24) + "…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    vm.saveSettings(
                        vm.settings.copy(
                            endpoint = endpoint.trim(), apiKey = apiKey.trim(), model = model.trim(),
                            temperature = temperature.toDouble(), timeoutSec = timeout.toLongOrNull() ?: 60,
                            personaId = personaId
                        )
                    )
                    testResult = "已保存 ✓"
                },
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
            OutlinedButton(
                onClick = {
                    testResult = "测试中…"
                    vm.testConnection(endpoint.trim(), apiKey.trim(), model.trim(), temperature.toDouble(), timeout.toLongOrNull() ?: 60) { testResult = it }
                },
                modifier = Modifier.weight(1f)
            ) { Text("测试连接") }
        }
        if (testResult.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(testResult, fontSize = 13.sp, color = if (testResult.contains("正常") || testResult.contains("保存")) Gold else MaterialTheme.colorScheme.error)
        }
    }
}