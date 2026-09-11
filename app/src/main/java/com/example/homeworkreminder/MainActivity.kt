package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val Bg = Color(0xFF090B12)
private val Card = Color(0xFF131722)
private val Muted = Color(0xFF9299AC)
private val Accent = Color(0xFF7C6CFF)
private val Cyan = Color(0xFF4FD7FF)

data class Homework(val id: Long, val subject: String, val title: String, val due: Long, val done: Boolean)

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
    var showAdd by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    val remaining = tasks.count { !it.done }
    val completed = tasks.count { it.done }
    val progress = if (tasks.isEmpty()) 0f else completed.toFloat() / tasks.size

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
        CompositionLocalProvider(LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = Bg) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                        Spacer(Modifier.height(18.dp))
                        Text("سلام 👋", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("امروز چه کارهایی داری؟", color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(18.dp))
                        WeekStrip()
                        Spacer(Modifier.height(16.dp))
                        ProgressCard(remaining, completed, tasks.size, progress)
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (tab == 2) "انجام‌شده‌ها" else "تکالیف پیش رو", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("$remaining باقی مانده", color = Accent, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
                            val visible = when(tab) { 2 -> tasks.filter { it.done }; else -> tasks.filter { !it.done } }
                            items(visible, key = { it.id }) { task ->
                                TaskCard(task) {
                                    val updated = tasks.map { if (it.id == task.id) it.copy(done = !it.done) else it }
                                    tasks = updated; saveTasks(context, updated)
                                }
                            }
                        }
                    }
                    FloatingActionButton(onClick = { showAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 78.dp), containerColor = Accent, contentColor = Color.White) {
                        Icon(Icons.Default.Add, contentDescription = "افزودن")
                    }
                    BottomBar(tab) { tab = it }
                }
            }
        }
        if (showAdd) AddHomeworkDialog(context, onDismiss = { showAdd = false }) { newTask ->
            val updated = tasks + newTask; tasks = updated; saveTasks(context, updated); scheduleReminder(context, newTask); showAdd = false
        }
    }
}

@Composable fun WeekStrip() {
    val days = listOf("ش","ی","د","س","چ","پ","ج")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEachIndexed { i, d ->
            val selected = i == 6
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                Text(d, color = if (selected) Color.White else Muted, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp))
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Accent else Card), contentAlignment = Alignment.Center) {
                    Text("${i + 14}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable fun ProgressCard(remaining: Int, completed: Int, total: Int, progress: Float) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF201D48), Color(0xFF101D2D)))).padding(20.dp)) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("$remaining تکلیف باقی مانده", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("$completed از $total تکلیف انجام شده", color = Muted, fontSize = 12.sp) }
                Text("${(progress * 100).toInt()}%", color = Cyan, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(10.dp)), color = Cyan, trackColor = Color(0x332F3445))
        }
    }
}

@Composable fun TaskCard(task: Homework, onDone: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x221B8FFF)), contentAlignment = Alignment.Center) { Text(subjectIcon(task.subject), fontSize = 23.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(task.subject, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(3.dp)); Text(task.title, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(5.dp)); Text("${dateLabel(task.due)} • ${fmt.format(Date(task.due))}", color = Accent, fontSize = 11.sp) }
        IconButton(onClick = onDone) { Icon(if (task.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (task.done) Cyan else Muted) }
    }
}

fun subjectIcon(s: String) = when(s.lowercase()) { "ریاضی" -> "📐"; "علوم" -> "🔬"; "فارسی" -> "📖"; "انگلیسی" -> "🌐"; "تاریخ" -> "🏛️"; else -> "📚" }
fun dateLabel(t: Long): String { val now = Calendar.getInstance(); val c = Calendar.getInstance().apply { timeInMillis = t }; return when { now.get(Calendar.DAY_OF_YEAR)==c.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR)==c.get(Calendar.YEAR) -> "امروز"; else -> SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(t)) } }

@Composable fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(68.dp).background(Color(0xF20D1019)).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf("خانه" to Icons.Default.Home, "تقویم" to Icons.Default.CalendarMonth, "انجام‌شده" to Icons.Default.CheckCircle, "تنظیمات" to Icons.Default.Settings).forEachIndexed { i, pair ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable { onTab(i) }) { Icon(pair.second, null, tint = if (tab==i) Accent else Muted, modifier = Modifier.size(21.dp)); Spacer(Modifier.height(3.dp)); Text(pair.first, color = if (tab==i) Color.White else Muted, fontSize = 10.sp) }
        }
    }
}

@Composable fun AddHomeworkDialog(context: Context, onDismiss: () -> Unit, onSave: (Homework) -> Unit) {
    var subject by remember { mutableStateOf("ریاضی") }; var title by remember { mutableStateOf("") }
    var due by remember { mutableLongStateOf(System.currentTimeMillis() + 3600000) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Card, title = { Text("مشق جدید", color = Color.White, fontWeight = FontWeight.Bold) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("درس") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("تکلیف") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = { pickDateTime(context) { due = it } }, modifier = Modifier.fillMaxWidth()) { Text("تاریخ و ساعت: ${dateLabel(due)} ${SimpleDateFormat("HH:mm", Locale.US).format(Date(due))}") }
        }
    }, confirmButton = { Button(enabled = title.isNotBlank(), onClick = { onSave(Homework(System.currentTimeMillis(), subject.ifBlank { "عمومی" }, title.trim(), due, false)) }) { Text("ذخیره مشق") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } })
}

fun pickDateTime(context: Context, callback: (Long)->Unit) {
    val now = Calendar.getInstance(); DatePickerDialog(context, { _, y,m,d -> TimePickerDialog(context, { _,h,min -> Calendar.getInstance().apply { set(y,m,d,h,min,0); set(Calendar.MILLISECOND,0); callback(timeInMillis) } }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show() }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
}

fun loadTasks(context: Context): List<Homework> { val s = context.getSharedPreferences("data",0).getString("tasks",null) ?: return emptyList(); val a=JSONArray(s); return (0 until a.length()).map { val o=a.getJSONObject(it); Homework(o.getLong("id"),o.getString("subject"),o.getString("title"),o.getLong("due"),o.getBoolean("done")) } }
fun saveTasks(context: Context, list: List<Homework>) { val a=JSONArray(); list.forEach { a.put(JSONObject().apply { put("id",it.id);put("subject",it.subject);put("title",it.title);put("due",it.due);put("done",it.done) }) }; context.getSharedPreferences("data",0).edit().putString("tasks",a.toString()).apply() }
fun scheduleReminder(context: Context, task: Homework) { if (task.due <= System.currentTimeMillis()) return; val intent=Intent(context,ReminderReceiver::class.java).putExtra("subject",task.subject).putExtra("title",task.title); val pi=PendingIntent.getBroadcast(context,task.id.toInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE); val am=context.getSystemService(AlarmManager::class.java); try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,task.due,pi) } catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP,task.due,pi) } }
