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
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun EchoRoot() {
    var tab by remember { mutableIntStateOf(0) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) { update = withContext(Dispatchers.IO) { Updater.check() } }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(-1) }

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            NavigationBar(containerColor = Surface1) {
                listOf(
                    Triple("Record", Icons.Default.Mic, 0),
                    Triple("Library", Icons.Default.GraphicEq, 1),
                    Triple("Ask", Icons.Default.AutoAwesome, 2),
                    Triple("Settings", Icons.Default.Tune, 3),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; Haptics.tick(ctx) },
                        icon = { Icon(icon, label) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, indicatorColor = RecRed,
                            unselectedIconColor = TextDim, selectedTextColor = Color.White, unselectedTextColor = TextDim,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            update?.let { u ->
                Row(
                    Modifier.fillMaxWidth().background(Violet).clickable(enabled = progress < 0) {
                        progress = 0
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Updater.download(ctx, u) { progress = it } }
                            progress = -1
                            if (f != null) Updater.install(ctx, f)
                        }
                    }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.SystemUpdate, null, tint = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text(if (progress >= 0) "Downloading update… $progress%" else "Update ready (build ${u.build}) — tap to install", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
            when (tab) {
                0 -> HomeScreen()
                1 -> LibraryScreen()
                2 -> AskScreen()
                else -> SettingsScreen { update = it }
            }
        }
    }
}

// ───────────────────────── Home ─────────────────────────

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

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Echo", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            AiPill()
        }

        Spacer(Modifier.weight(1f))
        Text(
            RecorderService.fmt(ui.elapsedMs),
            fontSize = 64.sp, fontWeight = FontWeight.Light,
            color = if (ui.status == RecorderState.Status.Paused) TextDim else Color.White,
        )
        Text(
            when (ui.status) {
                RecorderState.Status.Idle -> "Tap to record. Keeps going when your phone locks."
                RecorderState.Status.Recording -> "Recording · shake to bookmark"
                RecorderState.Status.Paused -> "Paused"
            },
            color = TextDim, fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        Waveform(ui.levels, active, Modifier.fillMaxWidth().height(110.dp))
        Spacer(Modifier.height(28.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            AnimatedVisibility(active) {
                SmallRound(Icons.Default.Bookmark, "Bookmark") {
                    RecorderService.send(ctx, RecorderService.ACTION_BOOKMARK)
                }
            }
            RecordButton(active && ui.status == RecorderState.Status.Recording, active) { toggle() }
            AnimatedVisibility(active) {
                val paused = ui.status == RecorderState.Status.Paused
                SmallRound(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Pause") {
                    RecorderService.send(ctx, if (paused) RecorderService.ACTION_RESUME else RecorderService.ACTION_PAUSE)
                }
            }
        }
        if (ui.bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("🔖 ${ui.bookmarks.size} bookmark(s)", color = TextDim, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))

        Text("Smart suggestions", color = TextDim, fontSize = 13.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(AiAgent.insights(items, hasKeys)) { InsightCard(it) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun AiPill() {
    val t = rememberInfiniteTransition(label = "ai")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Surface2).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(Violet))
        Spacer(Modifier.width(8.dp))
        Text("AI always on", fontSize = 12.sp, color = Color.White)
    }
}

@Composable
fun RecordButton(pulsing: Boolean, active: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val ring by t.animateFloat(1f, 1.45f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "ring")
    val ringAlpha by t.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1300), RepeatMode.Restart), label = "ra")
    Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(Modifier.size(110.dp).scale(ring).alpha(ringAlpha).clip(CircleShape).background(RecRed))
        }
        Box(
            Modifier.size(112.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(RecRedSoft, RecRed, Color(0xFFD10A26))))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (active) Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
            else Icon(Icons.Default.Mic, "Record", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(54.dp).clip(CircleShape).background(Surface2).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = Color.White) }
}

@Composable
fun Waveform(levels: List<Float>, active: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val bars = 56
        val gap = 4.dp.toPx()
        val bw = (size.width - gap * (bars - 1)) / bars
        val data = levels.takeLast(bars)
        val off = bars - data.size
        for (i in 0 until bars) {
            val v = if (i < off) 0.04f else data[i - off].coerceIn(0.04f, 1f)
            val h = size.height * v
            val x = i * (bw + gap)
            val c = if (active) Brush.verticalGradient(listOf(RecRedSoft, RecRed)) else Brush.verticalGradient(listOf(Surface2, Surface2))
            drawRoundRect(c, Offset(x, (size.height - h) / 2), Size(bw, h), CornerRadius(bw / 2))
        }
    }
}

@Composable
fun InsightCard(i: Insight) {
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(18.dp)).background(Surface1).padding(14.dp),
    ) {
        Text("${i.emoji}  ${i.title}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Text(i.body, color = TextDim, fontSize = 12.sp, maxLines = 3)
    }
}

// ───────────────────────── Library ─────────────────────────

@Composable
fun LibraryScreen() {
    val items by Repository.items.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("Library", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        val todo = items.flatMap { r -> r.actionItems.map { it } }.take(5)
        if (todo.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1).padding(14.dp)) {
                Text("✅ Open action items", fontWeight = FontWeight.SemiBold)
                todo.forEach { Text("• $it", color = TextDim, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(12.dp))
        }
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No recordings yet. Hit the red button.", color = TextDim)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(items, key = { it.id }) { RecordingCard(it) }
            }
        }
    }
}

@Composable
fun RecordingCard(r: Recording) {
    val ctx = LocalContext.current
    val playing by Player.playingId.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1)
            .clickable { open = !open }.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(RecRed).clickable {
                    Haptics.click(ctx); runCatching { Player.toggle(r) }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing == r.id) Icons.Default.Stop else Icons.Default.PlayArrow, "Play", tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    RecorderService.fmt(r.durationMs) + " · " +
                        SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()).format(Date(r.createdAt)),
                    color = TextDim, fontSize = 12.sp,
                )
            }
            AiStatusIcon(r.aiStatus)
        }
        if (r.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(r.tags) { t ->
                    Text("#$t", fontSize = 11.sp, color = Violet,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Surface2).padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
        }
        AnimatedVisibility(open) {
            Column(Modifier.padding(top = 12.dp)) {
                r.summary?.let { Text("Summary", color = TextDim, fontSize = 12.sp); Text(it); Spacer(Modifier.height(8.dp)) }
                if (r.actionItems.isNotEmpty()) {
                    Text("Action items", color = TextDim, fontSize = 12.sp)
                    r.actionItems.forEach { Text("• $it") }
                    Spacer(Modifier.height(8.dp))
                }
                if (r.people.isNotEmpty()) { Text("People", color = TextDim, fontSize = 12.sp); r.people.forEach { Text("🗣 $it") }; Spacer(Modifier.height(8.dp)) }
                r.transcript?.let { Text("Transcript", color = TextDim, fontSize = 12.sp); Text(it, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
                if (r.bookmarks.isNotEmpty())
                    Text("Bookmarks: " + r.bookmarks.joinToString { RecorderService.fmt(it) }, color = TextDim, fontSize = 12.sp)
                if (r.aiStatus.startsWith("error")) Text(r.aiStatus, color = RecRedSoft, fontSize = 12.sp)
                if (r.aiStatus == "local" || r.aiStatus.startsWith("error")) {
                    TextButton(onClick = { EchoApp.appScope.launchAi(ctx.applicationContext, r.id) }) { Text("Run AI again") }
                }
                TextButton(onClick = { if (playing == r.id) Player.stop(); Repository.delete(r.id) }) {
                    Text("Delete", color = RecRedSoft)
                }
            }
        }
    }
}

@Composable
fun AiStatusIcon(status: String) {
    when {
        status == "working" -> CircularProgressIndicator(Modifier.size(20.dp), color = Violet, strokeWidth = 2.dp)
        status == "done" -> Icon(Icons.Default.AutoAwesome, "AI done", tint = Violet)
        status.startsWith("error") -> Icon(Icons.Default.ErrorOutline, "AI error", tint = RecRedSoft)
        else -> {}
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
    LaunchedEffect(msg) { if (msg.isNotEmpty()) { kotlinx.coroutines.delay(3000); msg = "" } }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Spacer(Modifier.height(16.dp)); Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        item {
            Text("AI brain", fontWeight = FontWeight.SemiBold)
            Text("One free Groq key powers transcripts, summaries, action items and Ask. Get it at console.groq.com. The key stays on this device; recording audio is uploaded to Groq only for transcription.", color = TextDim, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(groq, { groq = it; s.groqKey = it }, label = { Text("Groq API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        }
        item {
            var dg by remember { mutableStateOf(s.deepgramKey) }
            Text("Who said what", fontWeight = FontWeight.SemiBold)
            Text("Optional free Deepgram key (deepgram.com) separates speakers. Echo then names them from context.", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(dg, { dg = it; s.deepgramKey = it }, label = { Text("Deepgram API key") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
        }
        item { SwitchRow("Shake to bookmark", "Sense a shake while recording and drop a bookmark", shake) { shake = it; s.shakeBookmark = it } }
        item { SwitchRow("Haptic feedback", "Tactile confirmation for start, stop and bookmarks", hap) { hap = it; s.haptics = it } }
        item {
            OutlinedButton(onClick = {
                runCatching {
                    ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + ctx.packageName)))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Never let battery saver stop recording") }
        }
        item {
            Button(onClick = {
                msg = "Checking…"
                scope.launch {
                    val u = withContext(Dispatchers.IO) { Updater.check() }
                    onUpdate(u)
                    msg = if (u == null) "You're on the latest build" else "Update found"
                }
            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text("Check for updates (build ${BuildConfig.VERSION_CODE})")
            }
            if (msg.isNotEmpty()) Text(msg, color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface1).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(sub, color = TextDim, fontSize = 12.sp) }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = RecRed))
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Ask your past self", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Answers come only from what you recorded.", color = TextDim, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (busy) CircularProgressIndicator(color = Violet)
            else Text(answer.ifEmpty { "Example: \"What did I decide about the launch?\"" },
                color = if (answer.isEmpty()) TextDim else Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Ask your recordings…") }, modifier = Modifier.weight(1f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val key = Settings(ctx).groqKey
                if (key.isBlank()) { answer = "Add your Groq key in Settings first."; return@Button }
                busy = true
                scope.launch {
                    answer = runCatching { withContext(Dispatchers.IO) { AiAgent.ask(key, q, items) } }
                        .getOrElse { "Couldn't reach AI: " + (it.message ?: "error") }
                    busy = false
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = RecRed)) { Text("Ask") }
        }
    }
}
