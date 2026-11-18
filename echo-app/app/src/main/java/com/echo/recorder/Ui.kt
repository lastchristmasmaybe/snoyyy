package com.echo.recorder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Serif = FontFamily.SansSerif

@Composable
private fun Wordmark(count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.GraphicEq, null, tint = Ink, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        Text("echo", fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-1).sp, color = Ink)
        Spacer(Modifier.weight(1f))
        Text("$count memories", color = TextDim, fontSize = 14.sp)
    }
}

@Composable
private fun PageTitle(title: String, sub: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-1).sp, color = Ink)
    Text(sub, color = TextDim, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
}

@Composable
fun EchoRoot() {
    var tab by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { Updater.check() } }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(-1) }
    val items by Repository.items.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = NavSurface, tonalElevation = 0.dp) {
                listOf(
                    Triple("Record", Icons.Default.Mic, 0),
                    Triple("Memories", Icons.Default.MenuBook, 1),
                    Triple("Ask", Icons.Default.ChatBubbleOutline, 2),
                    Triple("Settings", Icons.Default.WbSunny, 3),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; Haptics.tick(ctx) },
                        icon = { Icon(icon, label) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Ink, indicatorColor = NavPill,
                            unselectedIconColor = TextDim, selectedTextColor = Ink, unselectedTextColor = TextDim,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            update?.let { u ->
                Row(
                    Modifier.fillMaxWidth().background(Ink).clickable(enabled = progress < 0) {
                        progress = 0
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Updater.download(ctx, u) { progress = it } }
                            progress = -1
                            if (f != null) Updater.install(ctx, f)
                        }
                    }.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdate, null, tint = Cream)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (progress >= 0) "Downloading update… $progress%" else "Update ready (build ${u.build}) — tap to install",
                        color = Cream, fontWeight = FontWeight.Medium,
                    )
                }
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Wordmark(items.size)
                when (tab) {
                    0 -> HomeScreen()
                    1 -> MemoriesScreen()
                    2 -> AskScreen()
                    else -> SettingsScreen { update = it }
                }
            }
        }
    }
}

// ───────────────────────── Record ─────────────────────────

@Composable
private fun HeroCard() {
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
        .background(Brush.verticalGradient(listOf(HeroTop, Hero)))) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            // electric-purple glow rising from the bottom
            drawRect(Brush.radialGradient(
                listOf(Violet.copy(alpha = 0.75f), Violet.copy(alpha = 0.18f), Color.Transparent),
                Offset(w * 0.78f, h * 1.05f), w * 0.8f))
            // fine dot grid
            val step = 18.dp.toPx()
            var y = step / 2
            while (y < h) {
                var x = step / 2
                while (x < w) { drawCircle(Color.White.copy(alpha = 0.07f), 1.2f, Offset(x, y)); x += step }
                y += step
            }
            // sparkle badge
            drawCircle(Violet, 14.dp.toPx(), Offset(w - 36.dp.toPx(), 36.dp.toPx()))
            drawCircle(Color.White, 4.dp.toPx(), Offset(w - 36.dp.toPx(), 36.dp.toPx()))
        }
        Column(Modifier.padding(22.dp)) {
            Text("ROOM FOR YOUR THOUGHTS", color = NavPill.copy(alpha = 0.9f), fontSize = 12.sp, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            Text("A little less\nto carry.", color = Color.White, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 38.sp, letterSpacing = (-1.5).sp)
        }
    }
}

/** Purple gauge card (reference style): share of memories the AI has fully processed. */
@Composable
private fun GaugeCard(percent: Int, modifier: Modifier = Modifier) {
    Box(modifier.height(150.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.verticalGradient(listOf(Violet, Color(0xFF6D3FD6)))), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(110.dp)) {
            val st = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color.White.copy(alpha = 0.25f), 150f, 240f, false, style = st)
            drawArc(Color.White, 150f, 240f * percent / 100f, false, style = st)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$percent", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
            Text("memory health", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val ui by RecorderState.state.collectAsStateWithLifecycle()
    val items by Repository.items.collectAsStateWithLifecycle()
    val hasKeys = Settings(ctx).groqKey.isNotBlank()
    val active = ui.status != RecorderState.Status.Idle

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) RecorderService.send(ctx, RecorderService.ACTION_START)
    }
    fun toggle() {
        if (active) { RecorderService.send(ctx, RecorderService.ACTION_STOP); return }
        val need = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.launch(need.toTypedArray())
    }

    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item { Spacer(Modifier.height(14.dp)); HeroCard(); Spacer(Modifier.height(18.dp)) }
        item {
            Text(
                if (active) (if (ui.status == RecorderState.Status.Paused) "PAUSED" else "LISTENING") else "MAKE A MEMORY",
                color = TextDim, fontSize = 13.sp, letterSpacing = 2.sp,
            )
            Text(
                RecorderService.fmt(ui.elapsedMs),
                fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 52.sp, letterSpacing = (-1).sp, color = Ink,
            )
            Waveform(ui.levels, ui.speakers, active, Modifier.fillMaxWidth().height(if (active) 64.dp else 12.dp))
            if (active && ui.voices > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 6.dp)) {
                    for (k in 0 until ui.voices) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(SpeakerColors[k % 3]))
                            Spacer(Modifier.width(5.dp))
                            Text("Voice ${k + 1}", color = TextDim, fontSize = 13.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AnimatedVisibility(active) {
                    SmallRound(Icons.Default.Bookmark, "Bookmark") { RecorderService.send(ctx, RecorderService.ACTION_BOOKMARK) }
                }
                RecordButton(ui.status == RecorderState.Status.Recording, active) { toggle() }
                AnimatedVisibility(active) {
                    val paused = ui.status == RecorderState.Status.Paused
                    SmallRound(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Pause") {
                        RecorderService.send(ctx, if (paused) RecorderService.ACTION_RESUME else RecorderService.ACTION_PAUSE)
                    }
                }
            }
            Text(if (active) "Tap to stop" else "Tap to record", color = Ink, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (active) "Keeps recording when your phone locks.${if (ui.bookmarks.isNotEmpty()) "  🔖 ${ui.bookmarks.size}" else "  Shake to bookmark."}"
                else "Say it now. Find it later.\nAI turns it into searchable memories.",
                color = TextDim, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
        }
        item {
            val done = items.count { it.aiStatus == "done" }
            val pct = if (items.isEmpty()) 0 else done * 100 / items.size
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GaugeCard(pct, Modifier.weight(1f))
                Column(
                    Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(24.dp)).background(NavPill).padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Captured", color = TextDim, fontSize = 13.sp)
                    Text("${items.sumOf { it.durationMs } / 60000}", color = Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text("minutes · ${items.size} memories", color = TextDim, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        item {
            Text("Smart suggestions", color = TextDim, fontSize = 13.sp, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(AiAgent.insights(items, hasKeys)) { InsightCard(it) }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun RecordButton(pulsing: Boolean, active: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val ring by t.animateFloat(1f, 1.5f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "ring")
    val ringAlpha by t.animateFloat(0.4f, 0f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "ra")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "press")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (pulsing) Box(Modifier.size(104.dp).scale(ring).alpha(ringAlpha).clip(CircleShape).background(RecRed))
        Box(Modifier.size(136.dp).clip(CircleShape).background(RecRed.copy(alpha = 0.14f)))
        Box(
            Modifier.size(104.dp).scale(press)
                .shadow(18.dp, CircleShape, ambientColor = RecRed, spotColor = RecRed)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RecRedSoft, RecRed, Color(0xFFB81A30))))
                .clickable(interactionSource = src, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (active) Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
            else Icon(Icons.Default.GraphicEq, "Record", tint = Color.White, modifier = Modifier.size(46.dp))
        }
    }
}

@Composable
fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(NavPill).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Ink) }
}

@Composable
val SpeakerColors = listOf(Color(0xFF2F7BFF), Color(0xFFFF8A3D), Color(0xFF12B886))

@Composable
fun Waveform(levels: List<Float>, speakers: List<Int>, active: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val bars = 52
        val gap = 4.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        val data = levels.takeLast(bars)
        val spk = speakers.takeLast(bars)
        val off = bars - data.size
        for (i in 0 until bars) {
            val v = if (i < off) 0.06f else data[i - off].coerceIn(0.06f, 1f)
            val h = size.height * v
            val x = i * (bw + gap)
            val sp = if (i >= off) spk.getOrElse(i - off) { -1 } else -1
            val base = if (sp >= 0) SpeakerColors[sp % 3] else if (active) Color(0xFF9AA597) else Hairline
            val c = Brush.verticalGradient(listOf(base.copy(alpha = 0.7f), base))
            drawRoundRect(c, Offset(x, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun InsightCard(i: Insight) {
    Column(
        Modifier.width(260.dp).clip(RoundedCornerShape(20.dp)).background(CreamCard)
            .border(1.dp, Hairline, RoundedCornerShape(20.dp)).padding(16.dp),
    ) {
        Text("${i.emoji}  ${i.title}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink)
        Spacer(Modifier.height(4.dp))
        Text(i.body, color = TextDim, fontSize = 13.sp, maxLines = 3)
    }
}

// ───────────────────────── Memories ─────────────────────────

@Composable
fun MemoriesScreen() {
    val items by Repository.items.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val shown = remember(items, query) {
        if (query.isBlank()) items
        else items.filter {
            listOfNotNull(it.title, it.summary, it.transcript).any { t -> t.contains(query, ignoreCase = true) } ||
                it.tags.any { t -> t.contains(query, ignoreCase = true) }
        }
    }
    val todo = items.flatMap { r -> r.actionItems.map { it } }.take(5)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { PageTitle("Your memory, growing.", "Find a thought. Follow its connections.") }
        if (todo.isNotEmpty()) item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CreamCard)
                .border(1.dp, Hairline, RoundedCornerShape(18.dp)).padding(16.dp)) {
                Row {
                    Text("Action items", color = Plum, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${todo.size} open", color = TextDim, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                todo.forEach { Text("☐  $it", color = Ink, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        item {
            OutlinedTextField(
                query, { query = it }, placeholder = { Text("Search your memory…") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavPill, unfocusedContainerColor = NavPill,
                    focusedBorderColor = Ink, unfocusedBorderColor = Color.Transparent, cursorColor = Ink,
                ),
            )
        }
        if (shown.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text(if (items.isEmpty()) "No memories yet. Tap the red button." else "Nothing matches that.", color = TextDim)
            }
        }
        items(shown, key = { it.id }) { RecordingCard(it) }
    }
}

@Composable
fun RecordingCard(r: Recording) {
    val ctx = LocalContext.current
    val playing by Player.playingId.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = CreamCard,
            icon = { Icon(Icons.Default.DeleteForever, null, tint = RecRed) },
            title = { Text("Delete this memory?") },
            text = { Text("The audio, transcript and summary are removed from this phone. This can't be undone.") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    if (playing == r.id) Player.stop()
                    Repository.delete(r.id)
                }, colors = ButtonDefaults.buttonColors(containerColor = RecRed)) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Ink) } },
        )
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CreamCard)
            .border(1.dp, Hairline, RoundedCornerShape(22.dp)).clickable { open = !open }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(r.createdAt)),
                color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            AiStatusIcon(r.aiStatus)
            Spacer(Modifier.width(8.dp))
            Text((if (r.voices >= 2) "${r.voices} voices · " else "") + RecorderService.fmt(r.durationMs), color = TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(r.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = Serif, maxLines = if (open) 6 else 2)
        r.summary?.let { Text(it, color = TextDim, fontSize = 14.sp, maxLines = if (open) 12 else 2, modifier = Modifier.padding(top = 4.dp)) }
        if (r.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(r.tags) { t ->
                    Text("#$t", fontSize = 12.sp, color = Plum,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(NavPill).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp)) {
                if (r.actionItems.isNotEmpty()) {
                    Text("Action items", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    r.actionItems.forEach { Text("☐  $it", color = Ink, fontSize = 14.sp) }
                    Spacer(Modifier.height(8.dp))
                }
                if (r.people.isNotEmpty()) {
                    Text("People", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    r.people.forEach { Text("🗣  $it", color = Ink, fontSize = 14.sp) }
                    Spacer(Modifier.height(8.dp))
                }
                r.transcript?.let {
                    Text("Transcript", color = Plum, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(it, color = Ink, fontSize = 14.sp)
                    Text("AI draft · check the audio.", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                }
                if (r.bookmarks.isNotEmpty())
                    Text("Bookmarks: " + r.bookmarks.joinToString { RecorderService.fmt(it) }, color = TextDim, fontSize = 13.sp)
                if (r.aiStatus.startsWith("error")) Text(r.aiStatus, color = RecRed, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (playing == r.id) "Stop" else "Play") { Haptics.click(ctx); runCatching { Player.toggle(r) } }
            if (r.aiStatus == "local" || r.aiStatus.startsWith("error"))
                Pill("Run AI") { EchoApp.appScope.launchAi(ctx.applicationContext, r.id) }
            Pill("Delete", danger = true) { confirmDelete = true }
        }
    }
}

@Composable
private fun Pill(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, color = if (danger) RecRed else Ink,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(NavPill).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
fun AiStatusIcon(status: String) {
    when {
        status == "working" -> CircularProgressIndicator(Modifier.size(16.dp), color = Plum, strokeWidth = 2.dp)
        status == "done" -> Icon(Icons.Default.AutoAwesome, "AI done", tint = Plum, modifier = Modifier.size(16.dp))
        status.startsWith("error") -> Icon(Icons.Default.ErrorOutline, "AI error", tint = RecRed, modifier = Modifier.size(16.dp))
        else -> {}
    }
}

// ───────────────────────── Ask ─────────────────────────

@Composable
fun AskScreen() {
    val ctx = LocalContext.current
    val items by Repository.items.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        PageTitle("Ask your past self.", "Answers grounded in what you recorded.")
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)) {
            if (busy) CircularProgressIndicator(color = Plum)
            else Text(
                answer.ifEmpty { "Ask anything about what you have recorded.\nExample: \"what did Rohan want from me?\"" },
                fontFamily = Serif, fontSize = 22.sp, lineHeight = 30.sp,
                color = if (answer.isEmpty()) TextDim else Ink,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            OutlinedTextField(
                q, { q = it }, placeholder = { Text("Ask your brain anything…") },
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CreamCard, unfocusedContainerColor = CreamCard,
                    focusedBorderColor = Ink, unfocusedBorderColor = Hairline, cursorColor = Ink,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = {
                    val key = Settings(ctx).groqKey
                    if (key.isBlank()) { answer = "Add your free Groq key in Settings first."; return@Button }
                    busy = true
                    scope.launch {
                        answer = runCatching { withContext(Dispatchers.IO) { AiAgent.ask(key, q, items) } }
                            .getOrElse { "Couldn't reach AI: " + (it.message ?: "error") }
                        busy = false
                    }
                },
                shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
            ) { Text("Ask", fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ───────────────────────── Settings ─────────────────────────

@Composable
fun SettingsScreen(onUpdate: (UpdateInfo?) -> Unit) {
    val ctx = LocalContext.current
    val s = remember { Settings(ctx) }
    var groq by remember { mutableStateOf(s.groqKey) }
    var shake by remember { mutableStateOf(s.shakeBookmark) }
    var hap by remember { mutableStateOf(s.haptics) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(msg) { if (msg.isNotEmpty()) { kotlinx.coroutines.delay(4000); msg = "" } }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { PageTitle("Make yourself at home.", "Your voice, your pace, your data.") }
        item {
            Text("AI connection · Groq free account", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("No card, no cost. Get a key at console.groq.com. If you hit the free limit Echo waits and retries; it never switches to a paid plan.", color = TextDim, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                groq, { groq = it }, label = { Text("Groq API key") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CreamCard, unfocusedContainerColor = CreamCard,
                    focusedBorderColor = Ink, unfocusedBorderColor = Hairline, cursorColor = Ink,
                ),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { s.groqKey = groq; msg = "Key saved" },
                    shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text("Save key") }
                Pill("Test") {
                    s.groqKey = groq; msg = "Testing…"
                    scope.launch {
                        msg = runCatching { withContext(Dispatchers.IO) { AiAgent.test(groq); "Connected ✓" } }
                            .getOrElse { "Failed: " + (it.message ?: "error") }
                    }
                }
            }
            if (msg.isNotEmpty()) Text(msg, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        item { SwitchRow("Shake to bookmark", "Shake the phone while recording to mark a moment", shake) { shake = it; s.shakeBookmark = it } }
        item { SwitchRow("Haptic feedback", "Tactile confirmation for start, stop and bookmarks", hap) { hap = it; s.haptics = it } }
        item {
            OutlinedButton(
                onClick = {
                    runCatching {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
                    }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            ) { Text("Never let battery saver stop recording", color = Ink) }
        }
        item {
            Button(
                onClick = {
                    msg = "Checking…"
                    scope.launch {
                        val u = withContext(Dispatchers.IO) { Updater.check() }
                        onUpdate(u)
                        msg = if (u == null) "You're on the latest build" else "Update found — see the banner at the top"
                    }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("Check for updates (build ${BuildConfig.VERSION_CODE})") }
        }
        item {
            Text("Where your data goes", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "Recordings and memories are stored on this phone. To transcribe, Echo uploads a recording's audio to Groq; to summarise and answer, it sends the transcript and recent summaries. There is no Echo server.",
                color = TextDim, fontSize = 13.sp,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CreamCard)
            .border(1.dp, Hairline, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = Ink, fontSize = 15.sp)
            Text(sub, color = TextDim, fontSize = 13.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Cream))
    }
}
