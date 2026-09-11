package com.example.homeworkreminder

import android.app.*
import android.content.*
import android.os.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private val BgDark = Color(0xFF070910)
private val CardDark = Color(0xFF111522)
private val Muted = Color(0xFF929BB0)
private val Green = Color(0xFF50D890)
private val Red = Color(0xFFFF667D)
private val Cyan = Color(0xFF4DDCFF)
private val Accents = listOf(Color(0xFF806CFF), Color(0xFF00B8D9), Color(0xFFFF5C8A), Color(0xFFFFA726), Color(0xFF43C66C))
private const val PREFS = "homework_data"
private const val TASKS = "tasks"
private const val REMINDER = "reminder_minutes"
private const val ACCENT = "accent"
private const val DARK = "dark"
private const val WEEK = "weekly_schedule"

data class Homework(val id: Long, val subject: String, val title: String, val notes: String = "", val due: Long, val done: Boolean = false, val created: Long = System.currentTimeMillis(), val reminderMinutes: Int = 30)
data class DayPlan(val day: Int, val subjects: String)

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch("android.permission.POST_NOTIFICATIONS")
        setContent { HomeworkApp(this) }
    }
}

@Composable
fun HomeworkApp(context: Context) {
    var tasks by remember { mutableStateOf(loadTasks(context)) }
    var tab by remember { mutableIntStateOf(0) }
    var editor by remember { mutableStateOf<Homework?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showQuickAdd by remember { mutableStateOf(false) }
    var focusTask by remember { mutableStateOf<Homework?>(null) }
    var dark by remember { mutableStateOf(context.prefs().getBoolean(DARK, true)) }
    var accentIndex by remember { mutableIntStateOf(context.prefs().getInt(ACCENT, 0).coerceIn(0, Accents.lastIndex)) }
    val accent = Accents[accentIndex]
    fun refresh() { tasks = loadTasks(context) }
    val scheme = if (dark) darkColorScheme(primary = accent, secondary = Cyan, background = BgDark, surface = CardDark, error = Red) else lightColorScheme(primary = accent, secondary = Color(0xFF007C91), error = Red)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                        Spacer(Modifier.height(14.dp))
                        when (tab) {
                            0 -> HomeScreen(tasks, accent, { showQuickAdd = true }, { editor = it; showEditor = true }, { deleteTask(context, it.id); refresh() }, { toggleTask(context, it.id); refresh() }, { focusTask = it })
                            1 -> CalendarScreen(tasks, accent, { editor = it; showEditor = true }, { toggleTask(context, it.id); refresh() })
                            2 -> StatsScreen(tasks, accent)
                            else -> SettingsScreen(context, dark, { dark = it; context.prefs().edit().putBoolean(DARK, it).apply() }, accentIndex, { accentIndex = it; context.prefs().edit().putInt(ACCENT, it).apply() })
                        }
                    }
                    FloatingActionButton(onClick = { showQuickAdd = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 78.dp), containerColor = accent, contentColor = Color.White) { Icon(Icons.Default.Add, "افزودن") }
                    BottomBar(tab, accent) { tab = it }
                }
            }
        }
        if (showQuickAdd) QuickAddDialog(accent, { showQuickAdd = false }) { title, subject ->
            val due = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            saveTask(context, Homework(System.currentTimeMillis(), subject, title, due = due, reminderMinutes = context.prefs().getInt(REMINDER, 30))); refresh(); showQuickAdd = false
        }
        if (showEditor) HomeworkEditor(context, editor, accent, { showEditor = false }) { task -> saveTask(context, task); refresh(); showEditor = false }
        focusTask?.let { FocusDialog(it, accent) { focusTask = null } }
    }
}

@Composable
fun ColumnScope.HomeScreen(tasks: List<Homework>, accent: Color, onAdd: () -> Unit, onEdit: (Homework) -> Unit, onDelete: (Homework) -> Unit, onDone: (Homework) -> Unit, onFocus: (Homework) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("همه") }
    val subjects = listOf("همه") + tasks.map { it.subject }.filter { it.isNotBlank() }.distinct().take(7)
    val now = System.currentTimeMillis()
    val visible = tasks.filter { !it.done }.filter { query.isBlank() || it.title.contains(query, true) || it.subject.contains(query, true) || it.notes.contains(query, true) }.filter { filter == "همه" || it.subject == filter }.sortedBy { it.due }
    val overdue = visible.count { it.due < now }
    Text("تکالیف من 📚", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Text("همه‌چیز مرتب و آماده برای انجام", color = Muted, fontSize = 14.sp)
    Spacer(Modifier.height(13.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        InfoCard("📚", "${tasks.count { !it.done }}", "باقی‌مانده", accent, Modifier.weight(1f))
        InfoCard("✓", "${tasks.count { it.done }}", "انجام‌شده", Green, Modifier.weight(1f))
        InfoCard("⚠", "$overdue", "عقب‌افتاده", Red, Modifier.weight(1f))
    }
    Spacer(Modifier.height(11.dp))
    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("جستجوی عنوان، درس یا یادداشت...", color = Muted) }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp))
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { subjects.forEach { Chip(it, filter == it, accent) { filter = it } } }
    Spacer(Modifier.height(10.dp))
    if (overdue > 0) {
        Surface(Modifier.fillMaxWidth(), color = Red.copy(alpha = .12f), shape = RoundedCornerShape(15.dp)) { Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Warning, null, tint = Red); Spacer(Modifier.width(7.dp)); Text("$overdue تکلیف عقب‌افتاده داری.", color = Red, fontSize = 12.sp) } }
        Spacer(Modifier.height(9.dp))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("لیست تکالیف", color = MaterialTheme.colorScheme.onBackground, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("روی کارت = ویرایش", color = Muted, fontSize = 10.sp) }
    Spacer(Modifier.height(6.dp))
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 120.dp)) {
        items(visible, key = { it.id }) { TaskCard(it, accent, onEdit, onDelete, onDone, onFocus) }
        if (visible.isEmpty()) item { EmptyState("🎉", "چیزی برای نمایش نیست", "از + برای افزودن سریع استفاده کن") }
    }
}

@Composable fun InfoCard(icon: String, value: String, label: String, color: Color, modifier: Modifier) { Column(modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(icon, fontSize = 18.sp); Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(label, color = color, fontSize = 9.sp) } }

@Composable fun TaskCard(task: Homework, accent: Color, onEdit: (Homework) -> Unit, onDelete: (Homework) -> Unit, onDone: (Homework) -> Unit, onFocus: (Homework) -> Unit) {
    val overdue = !task.done && task.due < System.currentTimeMillis(); val fmt = remember { SimpleDateFormat("HH:mm", Locale.US) }; var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surface).clickable { onEdit(task) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background((if (overdue) Red else accent).copy(alpha = .14f)), contentAlignment = Alignment.Center) { Text(subjectIcon(task.subject), fontSize = 21.sp) }
        Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(task.subject.ifBlank { "درس" }, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(task.title, color = Muted, fontSize = 12.sp, maxLines = 1); Text("${jalaliDate(task.due)} • ${fmt.format(Date(task.due))}${if (overdue) " • عقب‌افتاده" else ""}", color = if (overdue) Red else accent, fontSize = 10.sp) }
        Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("ویرایش") }, { menu=false; onEdit(task) }, leadingIcon={Icon(Icons.Default.Edit,null)}); DropdownMenuItem({ Text("حالت تمرکز") }, { menu=false; onFocus(task) }, leadingIcon={Icon(Icons.Default.Timer,null)}); DropdownMenuItem({ Text("حذف") }, { menu=false; onDelete(task) }, leadingIcon={Icon(Icons.Default.Delete,null,tint=Red)}) } }
        IconButton(onClick = { onDone(task) }) { Icon(Icons.Default.CheckCircleOutline, null, tint = Green) }
    }
}

@Composable fun ColumnScope.CalendarScreen(tasks: List<Homework>, accent: Color, onEdit: (Homework) -> Unit, onDone: (Homework) -> Unit) {
    var monthOffset by remember { mutableIntStateOf(0) }; val base = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
    Text("تقویم 📅", color = MaterialTheme.colorScheme.onBackground, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("تقویم شمسی تکالیفت", color = Muted, fontSize = 14.sp); Spacer(Modifier.height(10.dp)); JalaliCalendar(tasks, base, { monthOffset-- }, { monthOffset++ }); Spacer(Modifier.height(10.dp)); Text("تکالیف این ماه", color = MaterialTheme.colorScheme.onBackground, fontSize = 18.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp))
    val target = tasks.filter { val j=taskJalali(it.due); val b=gregorianToJalali(base.get(Calendar.YEAR),base.get(Calendar.MONTH)+1,1); j[0]==b.first && j[1]==b.second }.sortedBy { it.due }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 110.dp)) { items(target,key={it.id}){ TaskCard(it,accent,onEdit,{},onDone,{}) }; if(target.isEmpty()) item{EmptyState("📅","این ماه خالی است","هنوز تکلیفی برای این ماه ثبت نشده")} }
}

@Composable fun JalaliCalendar(tasks: List<Homework>, base: Calendar, prev: () -> Unit, next: () -> Unit) {
    val names=listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند"); val j=gregorianToJalali(base.get(Calendar.YEAR),base.get(Calendar.MONTH)+1,1); val jy=j.first; val jm=j.second; val days=jalaliMonthDays(jy,jm); val g=jalaliToGregorian(jy,jm,1); val first=Calendar.getInstance().apply{set(g[0],g[1]-1,g[2])}; val offset=(first.get(Calendar.DAY_OF_WEEK)+1)%7
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(13.dp)) { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){IconButton(onClick=prev){Icon(Icons.Default.ChevronRight,null)};Text("${names[jm-1]} $jy",color=MaterialTheme.colorScheme.onSurface,fontSize=17.sp,fontWeight=FontWeight.Bold);IconButton(onClick=next){Icon(Icons.Default.ChevronLeft,null)}}; Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){listOf("ش","ی","د","س","چ","پ","ج").forEach{Text(it,color=Muted,fontSize=10.sp,modifier=Modifier.width(35.dp),textAlign=TextAlign.Center)}}; val cells=List(offset){0}+(1..days).toList(); val padded=cells+List((7-cells.size%7)%7){0}; padded.chunked(7).forEach{week->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){week.forEach{d->val has=if(d==0)false else tasks.any{t->val x=taskJalali(t.due);x[0]==jy&&x[1]==jm&&x[2]==d};Box(Modifier.size(35.dp),contentAlignment=Alignment.Center){if(d>0)Column(horizontalAlignment=Alignment.CenterHorizontally){Text(d.toString(),color=MaterialTheme.colorScheme.onSurface,fontSize=11.sp);if(has)Text("•",color=MaterialTheme.colorScheme.primary,fontSize=12.sp)}}}}} } }
}

@Composable fun ColumnScope.StatsScreen(tasks: List<Homework>, accent: Color) {
    val total=tasks.size; val done=tasks.count{it.done}; val overdue=tasks.count{!it.done&&it.due<System.currentTimeMillis()}; val rate=if(total==0)0 else done*100/total
    Text("آمار 📊",color=MaterialTheme.colorScheme.onBackground,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("عملکرد و وضعیت تکالیف",color=Muted,fontSize=14.sp);Spacer(Modifier.height(13.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){InfoCard("✓","$rate%","نرخ تکمیل",Green,Modifier.weight(1f));InfoCard("📚","$done","انجام‌شده",accent,Modifier.weight(1f));InfoCard("⚠","$overdue","عقب‌افتاده",Red,Modifier.weight(1f))};Spacer(Modifier.height(11.dp))
    val bySubject=tasks.groupBy{it.subject.ifBlank{"بدون درس"}}.mapValues{(_,v)->v.count{it.done} to v.size}.entries.sortedByDescending{it.value.second}
    Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(21.dp)){Column(Modifier.padding(16.dp)){Text("عملکرد بر اساس درس",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(8.dp));bySubject.forEach{(s,p)->val f=if(p.second==0)0f else p.first.toFloat()/p.second;Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text(s,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.width(82.dp),fontSize=11.sp);LinearProgressIndicator(progress={f},modifier=Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(5.dp)),color=accent);Spacer(Modifier.width(7.dp));Text("${p.first}/${p.second}",color=Muted,fontSize=10.sp)}};if(bySubject.isEmpty())Text("هنوز داده‌ای وجود ندارد.",color=Muted,fontSize=11.sp)}}
    Spacer(Modifier.height(10.dp));Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(21.dp)){Column(Modifier.padding(16.dp)){Text("تاریخچه انجام‌شده‌ها",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=17.sp);Spacer(Modifier.height(7.dp));val completed=tasks.filter{it.done}.sortedByDescending{it.created};completed.take(8).forEach{t->Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically){Text("✓",color=Green,fontSize=16.sp);Spacer(Modifier.width(8.dp));Column(Modifier.weight(1f)){Text(t.title,color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp);Text(t.subject+" • "+jalaliDate(t.due),color=Muted,fontSize=9.sp)}}};if(completed.isEmpty())Text("هنوز تکلیفی کامل نشده است.",color=Muted,fontSize=11.sp)}}
    Spacer(Modifier.height(7.dp))
}

@Composable fun ColumnScope.SettingsScreen(context: Context,dark:Boolean,setDark:(Boolean)->Unit,accentIndex:Int,setAccent:(Int)->Unit){
    Text("تنظیمات ⚙️",color=MaterialTheme.colorScheme.onBackground,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("ظاهر، یادآورها و برنامه مدرسه",color=Muted,fontSize=14.sp);Spacer(Modifier.height(13.dp));Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(15.dp)){SettingSwitch("حالت تاریک","ظاهر تیره و حرفه‌ای",dark,setDark);Spacer(Modifier.height(9.dp));Text("رنگ برنامه",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=14.sp);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Accents.forEachIndexed{i,c->Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable{setAccent(i)},contentAlignment=Alignment.Center){if(i==accentIndex)Icon(Icons.Default.Check,null,tint=Color.White)}}}}}
    Spacer(Modifier.height(11.dp));var reminder by remember{mutableIntStateOf(context.prefs().getInt(REMINDER,30))};Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(15.dp)){Text("یادآوری هوشمند ⏰",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=17.sp);Text("اعلان قبل از موعد هر تکلیف ارسال می‌شود.",color=Muted,fontSize=11.sp);Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){listOf(0 to "موعد",15 to "۱۵دقیقه",30 to "۳۰دقیقه",60 to "۱ساعت",1440 to "۱روز").forEach{(m,l)->Chip(l,reminder==m,MaterialTheme.colorScheme.primary){reminder=m;context.prefs().edit().putInt(REMINDER,m).apply()}}}}}
    Spacer(Modifier.height(11.dp));WeeklyScheduleEditor(context);Spacer(Modifier.height(11.dp));Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(15.dp)){Text("آلارم دقیق",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=15.sp);Text("برای اعلان دقیق در زمان تعیین‌شده، مجوز آلارم را فعال کن.",color=Muted,fontSize=11.sp);Spacer(Modifier.height(7.dp));Button(onClick={if(Build.VERSION.SDK_INT>=31)context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))}){Text("تنظیم آلارم دقیق")}}}
    Spacer(Modifier.height(12.dp));Text("نسخه 2.1",color=Muted,fontSize=10.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
}

@Composable fun WeeklyScheduleEditor(context:Context){val names=listOf("شنبه","یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه");var plans by remember{mutableStateOf(loadWeek(context))};var selected by remember{mutableIntStateOf(0)};Surface(Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(20.dp)){Column(Modifier.padding(15.dp)){Text("برنامه هفتگی مدرسه 🏫",color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=17.sp);Text("درس‌های هر روز را ثبت کن.",color=Muted,fontSize=11.sp);Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){names.forEachIndexed{i,n->FilterChip(selected==i,{selected=i},label={Text(n.take(1),fontSize=10.sp)})}};Spacer(Modifier.height(7.dp));OutlinedTextField(plans[selected].subjects,{v->plans=plans.toMutableList().also{it[selected]=DayPlan(selected,v)};saveWeek(context,plans)},Modifier.fillMaxWidth(),label={Text(names[selected])},placeholder={Text("مثلاً: ریاضی، علوم، فارسی")})}}}

@Composable fun SettingSwitch(title:String,subtitle:String,checked:Boolean,onChange:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,color=MaterialTheme.colorScheme.onSurface,fontWeight=FontWeight.Bold,fontSize=14.sp);Text(subtitle,color=Muted,fontSize=10.sp)};Switch(checked,onChange)}}

@Composable fun QuickAddDialog(accent:Color,onCancel:()->Unit,onSave:(String,String)->Unit){var title by remember{mutableStateOf("")};var subject by remember{mutableStateOf("")};AlertDialog(onDismissRequest=onCancel,title={Text("افزودن سریع ⚡")},text={Column{Text("امروز ساعت ۲۰:۰۰ ثبت می‌شود.",color=Muted,fontSize=11.sp);Spacer(Modifier.height(8.dp));OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان مشق")},singleLine=true);Spacer(Modifier.height(7.dp));OutlinedTextField(subject,{subject=it},Modifier.fillMaxWidth(),label={Text("درس")},singleLine=true)}},confirmButton={Button(onClick={if(title.isNotBlank())onSave(title.trim(),subject.trim())},colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("ثبت")}},dismissButton={TextButton(onClick=onCancel){Text("لغو")}})}

@Composable fun HomeworkEditor(context:Context,original:Homework?,accent:Color,onCancel:()->Unit,onSave:(Homework)->Unit){var subject by remember{mutableStateOf(original?.subject?:"")};var title by remember{mutableStateOf(original?.title?:"")};var notes by remember{mutableStateOf(original?.notes?:"")};var date by remember{mutableStateOf(original?.due?:Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,20);set(Calendar.MINUTE,0);set(Calendar.SECOND,0);set(Calendar.MILLISECOND,0)}.timeInMillis)};var reminder by remember{mutableIntStateOf(original?.reminderMinutes?:context.prefs().getInt(REMINDER,30))};var showDate by remember{mutableStateOf(false)};if(showDate)AndroidDateDialog(date,{date=it;showDate=false});AlertDialog(onDismissRequest=onCancel,title={Text(if(original==null)"مشق جدید" else "ویرایش مشق")},text={Column{OutlinedTextField(subject,{subject=it},Modifier.fillMaxWidth(),label={Text("درس")},singleLine=true);Spacer(Modifier.height(7.dp));OutlinedTextField(title,{title=it},Modifier.fillMaxWidth(),label={Text("عنوان")},singleLine=true);Spacer(Modifier.height(7.dp));OutlinedTextField(notes,{notes=it},Modifier.fillMaxWidth(),label={Text("یادداشت")},minLines=2);Spacer(Modifier.height(7.dp));OutlinedButton(onClick={showDate=true},Modifier.fillMaxWidth()){Text("موعد: ${jalaliDate(date)} • ${SimpleDateFormat("HH:mm",Locale.US).format(Date(date))}")};Spacer(Modifier.height(6.dp));Text("یادآوری",color=MaterialTheme.colorScheme.onSurface,fontSize=12.sp);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(0 to "موعد",15 to "۱۵دقیقه",30 to "۳۰دقیقه",60 to "۱ساعت",1440 to "۱روز").forEach{(m,l)->Chip(l,reminder==m,accent){reminder=m}}}}},confirmButton={Button(onClick={if(title.isNotBlank())onSave(Homework(original?.id?:System.currentTimeMillis(),subject.trim(),title.trim(),notes.trim(),date,original?.done?:false,original?.created?:System.currentTimeMillis(),reminder))}){Text("ذخیره")}},dismissButton={TextButton(onClick=onCancel){Text("لغو")}})}

@Composable fun AndroidDateDialog(current:Long,onPick:(Long)->Unit){val context=androidx.compose.ui.platform.LocalContext.current;val c=Calendar.getInstance().apply{timeInMillis=current};DisposableEffect(current){val d=DatePickerDialog(context,{_,y,m,day->val n=Calendar.getInstance().apply{timeInMillis=current;set(Calendar.YEAR,y);set(Calendar.MONTH,m);set(Calendar.DAY_OF_MONTH,day)};onPick(n.timeInMillis)},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));d.show();onDispose{d.dismiss()}}}

@Composable fun FocusDialog(task:Homework,accent:Color,onClose:()->Unit){var seconds by remember{mutableIntStateOf(25*60)};var running by remember{mutableStateOf(false)};LaunchedEffect(running,seconds){if(running&&seconds>0){delay(1000);seconds--}else if(seconds==0)running=false};AlertDialog(onDismissRequest=onClose,title={Text("حالت تمرکز 🎯")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.fillMaxWidth()){Text(task.title,color=Muted,maxLines=2);Spacer(Modifier.height(13.dp));Text(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60),color=accent,fontSize=46.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));Text("۲۵ دقیقه تمرکز",color=Muted,fontSize=11.sp)}},confirmButton={Button(onClick={running=!running}){Text(if(running)"توقف" else "شروع")}},dismissButton={TextButton(onClick=onClose){Text("بستن")}})}

@Composable fun BoxScope.BottomBar(selected:Int,accent:Color,onSelect:(Int)->Unit){NavigationBar(containerColor=MaterialTheme.colorScheme.surface,modifier=Modifier.align(Alignment.BottomCenter)){listOf(Icons.Default.List to "تکالیف",Icons.Default.DateRange to "تقویم",Icons.Default.BarChart to "آمار",Icons.Default.Settings to "تنظیمات").forEachIndexed{i,p->NavigationBarItem(selected==i,{onSelect(i)},icon={Icon(p.first,null)},label={Text(p.second,fontSize=10.sp)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Color.White,indicatorColor=accent))}}}
@Composable fun Chip(text:String,selected:Boolean,accent:Color,onClick:()->Unit){Surface(onClick=onClick,color=if(selected)accent else MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(14.dp)){Text(text,color=if(selected)Color.White else Muted,modifier=Modifier.padding(horizontal=9.dp,vertical=6.dp),fontSize=10.sp)}}
@Composable fun EmptyState(icon:String,title:String,subtitle:String){Box(Modifier.fillMaxWidth().padding(25.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(icon,fontSize=36.sp);Text(title,color=MaterialTheme.colorScheme.onBackground,fontWeight=FontWeight.Bold);Text(subtitle,color=Muted,fontSize=11.sp)}}}

private fun Context.prefs()=getSharedPreferences(PREFS,Context.MODE_PRIVATE)
fun loadTasks(context:Context):List<Homework>=runCatching{val a=JSONArray(context.prefs().getString(TASKS,"[]"));List(a.length()){i->val o=a.getJSONObject(i);Homework(o.getLong("id"),o.optString("subject"),o.optString("title"),o.optString("notes"),o.getLong("due"),o.optBoolean("done"),o.optLong("created",System.currentTimeMillis()),o.optInt("reminderMinutes",context.prefs().getInt(REMINDER,30)))}}.getOrDefault(emptyList())
fun saveTask(context:Context,task:Homework){val list=loadTasks(context).toMutableList();val i=list.indexOfFirst{it.id==task.id};if(i>=0)list[i]=task else list.add(task);saveTasks(context,list);if(!task.done)scheduleReminder(context,task)}
fun saveTasks(context:Context,list:List<Homework>){val a=JSONArray();list.forEach{a.put(JSONObject().apply{put("id",it.id);put("subject",it.subject);put("title",it.title);put("notes",it.notes);put("due",it.due);put("done",it.done);put("created",it.created);put("reminderMinutes",it.reminderMinutes)})};context.prefs().edit().putString(TASKS,a.toString()).apply()}
fun deleteTask(context:Context,id:Long){cancelReminder(context,id);saveTasks(context,loadTasks(context).filterNot{it.id==id})}
fun toggleTask(context:Context,id:Long){val list=loadTasks(context).map{if(it.id==id)it.copy(done=!it.done)else it};saveTasks(context,list);list.firstOrNull{it.id==id}?.let{if(it.done)cancelReminder(context,it.id)else scheduleReminder(context,it)}}
fun scheduleReminder(context:Context,task:Homework){if(task.done)return;val trigger=task.due-task.reminderMinutes*60000L;if(trigger<=System.currentTimeMillis())return;val am=context.getSystemService(AlarmManager::class.java);val intent=Intent(context,ReminderReceiver::class.java).apply{action="REMINDER";putExtra("id",task.id);putExtra("subject",task.subject);putExtra("title",task.title)};val pi=PendingIntent.getBroadcast(context,task.id.hashCode(),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);if(Build.VERSION.SDK_INT>=31&&am.canScheduleExactAlarms())am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)}
fun cancelReminder(context:Context,id:Long){val pi=PendingIntent.getBroadcast(context,id.hashCode(),Intent(context,ReminderReceiver::class.java).apply{action="REMINDER"},PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);context.getSystemService(AlarmManager::class.java).cancel(pi)}
private fun loadWeek(context:Context):MutableList<DayPlan>=runCatching{val a=JSONArray(context.prefs().getString(WEEK,"[]"));MutableList(7){i->if(i<a.length())DayPlan(i,a.getJSONObject(i).optString("subjects"))else DayPlan(i,"")}}.getOrDefault(MutableList(7){DayPlan(it,"")})
private fun saveWeek(context:Context,list:List<DayPlan>){val a=JSONArray();list.forEach{a.put(JSONObject().put("day",it.day).put("subjects",it.subjects))};context.prefs().edit().putString(WEEK,a.toString()).apply()}
fun subjectIcon(s:String)=when{ s.contains("ریاضی")->"➗";s.contains("علوم")->"🔬";s.contains("فارسی")->"📖";s.contains("انگلیسی")->"🔤";s.contains("تاریخ")->"🏛️";s.contains("ورزش")->"⚽";else->"📚" }
fun jalaliDate(ms:Long):String{val j=taskJalali(ms);return "%04d/%02d/%02d".format(Locale.US,j[0],j[1],j[2])}
fun taskJalali(ms:Long):IntArray{val c=Calendar.getInstance().apply{timeInMillis=ms};val j=gregorianToJalali(c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));return intArrayOf(j.first,j.second,j.third)}
fun jalaliMonthDays(y:Int,m:Int)=if(m<=6)31 else if(m<=11)30 else if(isJalaliLeap(y))30 else 29
fun isJalaliLeap(y:Int):Boolean{val g=jalaliToGregorian(y,12,30);return g[2]==30}
fun gregorianToJalali(gy:Int,gm:Int,gd:Int):Triple<Int,Int,Int>{val md=intArrayOf(0,31,28,31,30,31,30,31,31,30,31,30,31);var gy2=gy-1600;val gm2=gm-1;val gd2=gd-1;var g=365*gy2+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400;for(i in 0 until gm2)g+=md[i+1];if(gm2>1&&((gy%4==0&&gy%100!=0)||gy%400==0))g++;g+=gd2;var j=g-79;var jy=979+33*(j/12053);j%=12053;jy+=4*(j/1461);j%=1461;if(j>=366){jy+=(j-1)/365;j=(j-1)%365};return if(j<186)Triple(jy,1+j/31,1+j%31)else Triple(jy,7+(j-186)/30,1+(j-186)%30)}
fun jalaliToGregorian(jy:Int,jm:Int,jd:Int):IntArray{val jy2=jy-979;val jm2=jm-1;val jd2=jd-1;var j=365*jy2+(jy2/33)*8+((jy2%33)+3)/4;for(i in 0 until jm2)j+=if(i<6)31 else 30;j+=jd2;var g=j+79;var gy=1600+400*(g/146097);g%=146097;var leap=true;if(g>=36525){g--;gy+=100*(g/36524);g%=36524;if(g>=365)g++;else leap=false};gy+=4*(g/1461);g%=1461;if(g>=366){leap=false;g--;gy+=g/365;g%=365};val md=intArrayOf(31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31);var gm=0;while(g>=md[gm]){g-=md[gm];gm++};return intArrayOf(gy,gm+1,g+1)}
