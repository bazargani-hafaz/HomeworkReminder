package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val Bg = Color(0xFF070910)
private val Card = Color(0xFF111522)
private val Card2 = Color(0xFF171C2B)
private val Muted = Color(0xFF8F98AE)
private val Accent = Color(0xFF806CFF)
private val Cyan = Color(0xFF4DDCFF)
private val Green = Color(0xFF50D890)
private val Red = Color(0xFFFF667D)

data class Homework(val id: Long, val subject: String, val title: String, val notes: String = "", val due: Long, val done: Boolean = false, val created: Long = System.currentTimeMillis())

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { HomeworkApp(this) }
    }
}

@Composable
fun HomeworkApp(context: Context) {
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Homework?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("همه") }
    var dark by remember { mutableStateOf(true) }
    val completed = tasks.count { it.done }
    val remaining = tasks.count { !it.done }
    val overdue = tasks.count { !it.done && it.due < System.currentTimeMillis() }
    val progress = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size
    val level = completed / 5 + 1
    val xp = completed * 20
    val streak = calculateStreak(tasks)

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                        Spacer(Modifier.height(16.dp))
                        when (tab) {
                            0 -> HomeScreen(tasks, remaining, completed, progress, streak, level, xp, overdue, onAdd = { editing = null; showEditor = true }, onDone = { id -> tasks = toggleTask(context, tasks, id) { scheduleReminder(context, it) } })
                            1 -> CalendarScreen(tasks, onAdd = { editing = null; showEditor = true }, onDone = { id -> tasks = toggleTask(context, tasks, id) { scheduleReminder(context, it) } })
                            2 -> CompletedScreen(tasks.filter { it.done }, onDone = { id -> tasks = toggleTask(context, tasks, id) { } })
                            else -> SettingsScreen(context, tasks, dark, { dark = it }, overdue)
                        }
                    }
                    FloatingActionButton(onClick = { editing = null; showEditor = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 82.dp), containerColor = Accent, contentColor = Color.White) { Icon(Icons.Default.Add, "افزودن") }
                    BottomBar(tab) { tab = it }
                }
            }
        }
        if (showEditor) HomeworkEditor(context, editing, { showEditor = false }) { task ->
            tasks = if (editing == null) tasks + task else tasks.map { if (it.id == task.id) task else it }
            saveTasks(context, tasks); scheduleReminder(context, task); showEditor = false
        }
    }
}

@Composable
fun HomeScreen(tasks: List<Homework>, remaining: Int, completed: Int, progress: Float, streak: Int, level: Int, xp: Int, overdue: Int, onAdd: () -> Unit, onDone: (Long) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("همه") }
    val visible = tasks.filter { !it.done && (query.isBlank() || it.title.contains(query, true) || it.subject.contains(query, true)) }.filter { filter == "همه" || it.subject == filter }.sortedBy { it.due }
    Text("سلام 👋", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("امروز آماده‌ای که کارهات رو جمع کنی؟", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(15.dp))
    WeekStrip()
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MiniStat("🔥", "$streak روز", "پشت‌سرهم", Accent, Modifier.weight(1f))
        MiniStat("🏆", "سطح $level", "$xp XP", Cyan, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
    ProgressCard(remaining, completed, tasks.size, progress)
    Spacer(Modifier.height(18.dp))
    OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جستجوی مشق...", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) }, shape = RoundedCornerShape(16.dp))
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { listOf("همه", "ریاضی", "علوم", "فارسی", "انگلیسی", "تاریخ").forEach { Chip(it, filter == it) { filter = it } } }
    Spacer(Modifier.height(13.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("تکالیف پیش رو", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (overdue > 0) Text("$overdue عقب‌افتاده", color = Red, fontSize = 11.sp)
    }
    Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
        items(visible, key = { it.id }) { TaskCard(it, onDone) }
        if (visible.isEmpty()) item { EmptyState("🎉", "همه‌چی مرتبه!", "مشق جدیدی برای نمایش نیست") }
    }
}

@Composable fun MiniStat(icon: String, value: String, label: String, color: Color, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(Card).padding(12.dp).then(modifier), verticalAlignment = Alignment.CenterVertically) { Text(icon, fontSize = 22.sp); Spacer(Modifier.width(8.dp)); Column { Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(label, color = color, fontSize = 10.sp) } }
}

@Composable fun WeekStrip() {
    val cal = Calendar.getInstance(); val start = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("ش","ی","د","س","چ","پ","ج").forEachIndexed { i, d ->
            val c = Calendar.getInstance().apply { timeInMillis = start.timeInMillis; add(Calendar.DAY_OF_YEAR, i) }
            val selected = c.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && c.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(39.dp)) {
                Text(d, color = if (selected) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp)); Box(Modifier.size(35.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Accent else Card), contentAlignment = Alignment.Center) { Text(gregorianDay(c), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            }
        }
    }
}

@Composable fun ProgressCard(remaining: Int, completed: Int, total: Int, progress: Float) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF211C4B), Color(0xFF0D1C2B)))).padding(18.dp)) {
        Column { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("پیشرفت امروز", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("$completed از $total تکلیف انجام شده", color = Muted, fontSize = 11.sp) }; Text("${(progress * 100).toInt()}%", color = Cyan, fontSize = 24.sp, fontWeight = FontWeight.Bold) }; Spacer(Modifier.height(13.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)), color = Cyan, trackColor = Color(0x332F3445)); Spacer(Modifier.height(8.dp)); Text("$remaining تکلیف باقی مانده", color = Color.White, fontSize = 11.sp) }
    }
}

@Composable fun TaskCard(task: Homework, onDone: (Long) -> Unit) {
    val overdue = !task.done && task.due < System.currentTimeMillis()
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(if (overdue) Color(0x22FF667D) else Color(0x22806CFF)), contentAlignment = Alignment.Center) { Text(subjectIcon(task.subject), fontSize = 22.sp) }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(task.subject, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(task.title, color = Muted, fontSize = 12.sp, maxLines = 1); Spacer(Modifier.height(4.dp)); Text("${dateLabel(task.due)} • ${fmt.format(Date(task.due))}", color = if (overdue) Red else Accent, fontSize = 10.sp) }
        IconButton(onClick = { onDone(task.id) }) { Icon(Icons.Default.CheckCircleOutline, null, tint = Green) }
    }
}

@Composable fun CalendarScreen(tasks: List<Homework>, onAdd: () -> Unit, onDone: (Long) -> Unit) {
    Text("تقویم 📅", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("برنامه تکالیفت رو یکجا ببین", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(16.dp))
    CalendarMonth(tasks)
    Spacer(Modifier.height(16.dp)); Text("برنامه هفته", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 110.dp)) { items(tasks.sortedBy { it.due }, key = { it.id }) { TaskCard(it, onDone) } }
}

@Composable fun CalendarMonth(tasks: List<Homework>) {
    val cal = Calendar.getInstance(); val month = cal.get(Calendar.MONTH); val year = cal.get(Calendar.YEAR); val first = Calendar.getInstance().apply { set(year, month, 1) }; val max = first.getActualMaximum(Calendar.DAY_OF_MONTH); val offset = (first.get(Calendar.DAY_OF_WEEK) + 1) % 7
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Card).padding(14.dp)) {
        Text(SimpleDateFormat("MMMM yyyy", Locale.US).format(first.time), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("ش","ی","د","س","چ","پ","ج").forEach { Text(it, color = Muted, fontSize = 10.sp, modifier = Modifier.width(35.dp)) } }
        val cells = List(offset) { 0 } + (1..max).toList(); val padded = cells + List((7 - cells.size % 7) % 7) { 0 }
        padded.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { week.forEach { day -> val count = if (day == 0) 0 else tasks.count { sameDay(it.due, year, month, day) }; Box(Modifier.size(35.dp).clip(CircleShape).background(if (day == cal.get(Calendar.DAY_OF_MONTH)) Accent else Color.Transparent), contentAlignment = Alignment.Center) { if (day > 0) Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(day.toString(), color = if (day == cal.get(Calendar.DAY_OF_MONTH)) Color.White else Color.LightGray, fontSize = 11.sp); if (count > 0) Text("•", color = Cyan, fontSize = 10.sp) } } } } }
    }
}

@Composable fun CompletedScreen(tasks: List<Homework>, onDone: (Long) -> Unit) {
    Text("انجام‌شده‌ها ✓", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("موفقیت‌هات رو ببین 💪", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF12352A), Color(0xFF10201D)))).padding(18.dp)) { Text("${tasks.size}", color = Green, fontSize = 30.sp, fontWeight = FontWeight.Bold); Text(" تکلیف با موفقیت انجام شده", color = Color.White, fontSize = 13.sp) }
    Spacer(Modifier.height(15.dp)); LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 110.dp)) { items(tasks, key = { it.id }) { TaskCard(it, onDone) }; if (tasks.isEmpty()) item { EmptyState("📚", "هنوز چیزی انجام نشده", "اولین مأموریتت رو کامل کن!") } }
}

@Composable fun SettingsScreen(context: Context, tasks: List<Homework>, dark: Boolean, setDark: (Boolean) -> Unit, overdue: Int) {
    Text("تنظیمات ⚙️", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("اپ رو مطابق سلیقه خودت تنظیم کن", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(18.dp))
    SettingRow(Icons.Default.DarkMode, "حالت تاریک", "ظاهر دارک و حرفه‌ای", dark, setDark); SettingRow(Icons.Default.Notifications, "اعلان‌ها", "یادآوری تکالیف", true) { }
    Spacer(Modifier.height(10.dp)); Text("ابزارها", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(4.dp));
    ActionRow(Icons.Default.Alarm, "مجوز آلارم دقیق", "برای یادآوری دقیق‌تر") { if (android.os.Build.VERSION.SDK_INT >= 31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) }
    ActionRow(Icons.Default.DeleteSweep, "پاک‌سازی انجام‌شده‌ها", "حذف تاریخچه تکالیف تمام‌شده") { val remaining = tasks.filter { !it.done }; saveTasks(context, remaining) }
    ActionRow(Icons.Default.Info, "درباره برنامه", "یادآور مشق • نسخه 2.0") { }
    Spacer(Modifier.height(12.dp)); Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(16.dp)) { Column { Text("وضعیت", color = Color.White, fontWeight = FontWeight.Bold); Text("${tasks.size} تکلیف • $overdue عقب‌افتاده", color = Muted, fontSize = 12.sp) } }
}

@Composable fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Accent); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(sub, color = Muted, fontSize = 11.sp) }; Switch(checked, onChange) } }
@Composable fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, action: () -> Unit) { Row(Modifier.fillMaxWidth().clickable { action() }.padding(vertical = 14.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Muted); Spacer(Modifier.width(12.dp)); Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(sub, color = Muted, fontSize = 11.sp) } } }

@Composable fun Chip(text: String, selected: Boolean, onClick: () -> Unit) { Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Accent else Card).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 7.dp)) { Text(text, color = Color.White, fontSize = 10.sp) } }
@Composable fun EmptyState(icon: String, title: String, sub: String) { Box(Modifier.fillMaxWidth().padding(35.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(icon, fontSize = 38.sp); Spacer(Modifier.height(8.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(sub, color = Muted, fontSize = 11.sp) } } }

@Composable fun BottomBar(tab: Int, onTab: (Int) -> Unit) { Row(Modifier.fillMaxWidth().height(68.dp).background(Color(0xF20D1019)), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { listOf("خانه" to Icons.Default.Home, "تقویم" to Icons.Default.CalendarMonth, "انجام‌شده" to Icons.Default.CheckCircle, "تنظیمات" to Icons.Default.Settings).forEachIndexed { i, p -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { onTab(i) }) { Icon(p.second, null, tint = if (tab == i) Accent else Muted, modifier = Modifier.size(21.dp)); Text(p.first, color = if (tab == i) Color.White else Muted, fontSize = 9.sp) } } } }

@Composable fun HomeworkEditor(context: Context, old: Homework?, onDismiss: () -> Unit, onSave: (Homework) -> Unit) {
    var subject by remember { mutableStateOf(old?.subject ?: "ریاضی") }; var title by remember { mutableStateOf(old?.title ?: "") }; var notes by remember { mutableStateOf(old?.notes ?: "") }; var due by remember { mutableLongStateOf(old?.due ?: System.currentTimeMillis() + 3600000) }; var showDelete by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Card, title = { Text(if (old == null) "✨ مشق جدید" else "✏️ ویرایش مشق", color = Color.White, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(subject, { subject = it }, label = { Text("درس") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(title, { title = it }, label = { Text("عنوان تکلیف") }, singleLine = true, modifier = Modifier.fillMaxWidth()); OutlinedTextField(notes, { notes = it }, label = { Text("توضیحات (اختیاری)") }, minLines = 2, modifier = Modifier.fillMaxWidth()); Button(onClick = { pickDateTime(context) { due = it } }, modifier = Modifier.fillMaxWidth()) { Text("📅 ${dateLabel(due)}   ⏰ ${SimpleDateFormat("HH:mm", Locale.US).format(Date(due))}") } } }, confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onSave(Homework(old?.id ?: System.currentTimeMillis(), subject.ifBlank { "عمومی" }.trim(), title.trim(), notes.trim(), due, old?.done ?: false, old?.created ?: System.currentTimeMillis())) }) { Text("ذخیره") } }, dismissButton = { Row { if (old != null) TextButton(onClick = { showDelete = true }) { Text("حذف", color = Red) }; TextButton(onClick = onDismiss) { Text("لغو") } } })
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text("حذف مشق؟") }, text = { Text("این تکلیف برای همیشه حذف می‌شود.") }, confirmButton = { TextButton(onClick = { deleteTask(context, old!!); showDelete = false; onDismiss() }) { Text("حذف", color = Red) } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text("لغو") } })
}

fun pickDateTime(context: Context, callback: (Long) -> Unit) { val now = Calendar.getInstance(); DatePickerDialog(context, { _, y, m, d -> TimePickerDialog(context, { _, h, min -> Calendar.getInstance().apply { set(y, m, d, h, min, 0); set(Calendar.MILLISECOND, 0); callback(timeInMillis) } }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show() }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show() }

fun toggleTask(context: Context, tasks: List<Homework>, id: Long, after: (Homework) -> Unit): List<Homework> { val updated = tasks.map { if (it.id == id) it.copy(done = !it.done) else it }; saveTasks(context, updated); updated.find { it.id == id }?.let { if (!it.done) after(it) }; return updated }
fun deleteTask(context: Context, task: Homework) { cancelReminder(context, task); val list = loadTasks(context).filter { it.id != task.id }; saveTasks(context, list) }
fun loadTasks(context: Context): List<Homework> { return try { val s = context.getSharedPreferences("data", 0).getString("tasks", null) ?: return emptyList(); val a = JSONArray(s); (0 until a.length()).map { val o = a.getJSONObject(it); Homework(o.getLong("id"), o.getString("subject"), o.getString("title"), o.optString("notes", ""), o.getLong("due"), o.getBoolean("done"), o.optLong("created", System.currentTimeMillis())) } } catch (_: Exception) { emptyList() } }
fun saveTasks(context: Context, list: List<Homework>) { val a = JSONArray(); list.forEach { a.put(JSONObject().apply { put("id", it.id); put("subject", it.subject); put("title", it.title); put("notes", it.notes); put("due", it.due); put("done", it.done); put("created", it.created) }) }; context.getSharedPreferences("data", 0).edit().putString("tasks", a.toString()).apply() }
fun scheduleReminder(context: Context, task: Homework) { if (task.done || task.due <= System.currentTimeMillis()) return; val intent = Intent(context, ReminderReceiver::class.java).putExtra("id", task.id).putExtra("subject", task.subject).putExtra("title", task.title); val pi = PendingIntent.getBroadcast(context, task.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val am = context.getSystemService(AlarmManager::class.java); try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.due, pi) } catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, task.due, pi) } }
fun cancelReminder(context: Context, task: Homework) { val pi = PendingIntent.getBroadcast(context, task.id.hashCode(), Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return; context.getSystemService(AlarmManager::class.java).cancel(pi); pi.cancel() }
fun calculateStreak(tasks: List<Homework>): Int { val days = tasks.filter { it.done }.map { dayKey(it.due) }.toSet(); var streak = 0; val c = Calendar.getInstance(); while (days.contains(dayKey(c.timeInMillis))) { streak++; c.add(Calendar.DAY_OF_YEAR, -1) }; return streak }
fun dayKey(t: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(t))
fun sameDay(t: Long, y: Int, m: Int, d: Int): Boolean { val c = Calendar.getInstance().apply { timeInMillis = t }; return c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == d }
fun gregorianDay(c: Calendar) = c.get(Calendar.DAY_OF_MONTH).toString()
fun dateLabel(t: Long): String { val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = t }; return when { sameDay(t, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)) -> "امروز"; else -> SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(t)) } }
fun subjectIcon(s: String) = when (s.lowercase()) { "ریاضی" -> "📐"; "علوم" -> "🔬"; "فارسی" -> "📖"; "انگلیسی" -> "🌐"; "تاریخ" -> "🏛️"; "فیزیک" -> "⚛️"; "شیمی" -> "🧪"; else -> "📚" }
