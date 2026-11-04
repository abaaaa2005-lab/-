package com.vse.launcher.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vse.launcher.data.LauncherViewModel
import com.vse.launcher.ui.ScreenScaffold
import com.vse.launcher.ui.ToolId
import com.vse.launcher.util.Calc
import kotlin.random.Random

@Composable
fun ToolScreen(id: ToolId, vm: LauncherViewModel, onBack: () -> Unit) {
    when (id) {
        ToolId.CALCULATOR -> CalculatorTool(onBack)
        ToolId.NOTES -> NotesTool(vm, onBack)
        ToolId.TODO -> TodoTool(vm, onBack)
        ToolId.WEATHER -> WeatherTool(vm, onBack)
        ToolId.FLASHLIGHT -> FlashlightTool(vm, onBack)
        ToolId.STOPWATCH -> StopwatchTool(onBack)
        ToolId.TIMER -> TimerTool(onBack)
        ToolId.COMPASS -> CompassTool(onBack)
        ToolId.LEVEL -> LevelTool(onBack)
        ToolId.CONVERTER -> ConverterTool(onBack)
        ToolId.COUNTER -> CounterTool(onBack)
        ToolId.RANDOM -> RandomTool(onBack)
        ToolId.DEVICE -> DeviceInfoTool(onBack)
    }
}

// ---------------- Калькулятор ----------------

@Composable
private fun CalculatorTool(onBack: () -> Unit) {
    var expr by rememberSaveable { mutableStateOf("") }
    val history = remember { mutableStateListOf<String>() }
    val preview = Calc.eval(expr)?.let { Calc.format(it) }

    val rows = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("%", "0", ",", "="),
    )
    val sci = listOf("√", "^", "π", "sin", "cos", "tan", "ln", "log")

    ScreenScaffold("Калькулятор", onBack) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState(), reverseScrolling = true), horizontalAlignment = Alignment.End) {
                history.forEach {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                expr.ifEmpty { "0" },
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 40.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (preview != null && preview != expr) "= $preview" else " ",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sci.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { expr += if (k.length > 1 && k != "π") "$k(" else k },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, fontSize = 14.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        val isOp = k in listOf("÷", "×", "−", "+", "=")
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1.35f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    when {
                                        k == "=" -> MaterialTheme.colorScheme.primary
                                        isOp || k == "C" -> MaterialTheme.colorScheme.secondaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .clickable {
                                    when (k) {
                                        "C" -> expr = ""
                                        "=" -> preview?.let { history += "$expr = $it"; expr = it }
                                        else -> expr += k
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                k, fontSize = 26.sp,
                                color = if (k == "=") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton({ if (expr.isNotEmpty()) expr = expr.dropLast(1) }) {
                    Icon(Icons.Default.Backspace, null); Spacer(Modifier.width(6.dp)); Text("Стереть")
                }
            }
        }
    }
}

// ---------------- Конвертер ----------------

private data class MeasureUnit(val name: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double)

private fun lin(name: String, factor: Double) = MeasureUnit(name, { it * factor }, { it / factor })

private val CATEGORIES: List<Pair<String, List<MeasureUnit>>> = listOf(
    "Длина" to listOf(
        lin("мм", 0.001), lin("см", 0.01), lin("м", 1.0), lin("км", 1000.0),
        lin("дюйм", 0.0254), lin("фут", 0.3048), lin("ярд", 0.9144), lin("миля", 1609.344),
    ),
    "Масса" to listOf(
        lin("мг", 1e-6), lin("г", 0.001), lin("кг", 1.0), lin("т", 1000.0),
        lin("унция", 0.0283495), lin("фунт", 0.453592),
    ),
    "Температура" to listOf(
        MeasureUnit("°C", { it }, { it }),
        MeasureUnit("°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
        MeasureUnit("K", { it - 273.15 }, { it + 273.15 }),
    ),
    "Скорость" to listOf(
        lin("м/с", 1.0), lin("км/ч", 1 / 3.6), lin("миль/ч", 0.44704), lin("узел", 0.514444),
    ),
    "Объём" to listOf(
        lin("мл", 0.001), lin("л", 1.0), lin("м³", 1000.0), lin("галлон (США)", 3.78541), lin("стакан", 0.25),
    ),
    "Площадь" to listOf(
        lin("м²", 1.0), lin("км²", 1e6), lin("сотка", 100.0), lin("га", 10000.0), lin("акр", 4046.86),
    ),
    "Данные" to listOf(
        lin("бит", 0.125), lin("байт", 1.0), lin("КБ", 1024.0), lin("МБ", 1024.0 * 1024), lin("ГБ", 1024.0 * 1024 * 1024),
        lin("ТБ", 1024.0 * 1024 * 1024 * 1024),
    ),
    "Время" to listOf(
        lin("сек", 1.0), lin("мин", 60.0), lin("час", 3600.0), lin("день", 86400.0), lin("неделя", 604800.0), lin("год", 31557600.0),
    ),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConverterTool(onBack: () -> Unit) {
    var cat by remember { mutableIntStateOf(0) }
    var from by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("1") }
    val units = CATEGORIES[cat].second
    val value = Calc.eval(input)

    ScreenScaffold("Конвертер", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CATEGORIES.forEachIndexed { i, c ->
                    FilterChip(selected = i == cat, onClick = { cat = i; from = 0 }, label = { Text(c.first) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                input, { input = it },
                label = { Text("Значение") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("Из:", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEachIndexed { i, u ->
                    FilterChip(selected = i == from, onClick = { from = i }, label = { Text(u.name) })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (value == null) {
                Text("Введите число", color = MaterialTheme.colorScheme.error)
            } else {
                val base = units[from].toBase(value)
                units.forEachIndexed { i, u ->
                    if (i != from) {
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Calc.format(u.fromBase(base)), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(u.name, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Счётчик ----------------

@Composable
private fun CounterTool(onBack: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold("Счётчик", onBack, actions = {
        IconButton({ count = 0 }) { Icon(Icons.Default.RestartAlt, "Сброс") }
    }) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { count++ },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$count", fontSize = 96.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Нажмите, чтобы прибавить", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FilledTonalButton({ count-- }) { Text("−1") }
                FilledTonalButton({ count += 10 }) { Text("+10") }
            }
        }
    }
}

// ---------------- Случайность ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomTool(onBack: () -> Unit) {
    var result by remember { mutableStateOf("🎲") }
    var subtitle by remember { mutableStateOf("Выберите действие") }
    var min by remember { mutableStateOf("1") }
    var max by remember { mutableStateOf("100") }
    var options by remember { mutableStateOf("Пицца, Суши, Бургер") }

    ScreenScaffold("Случайность", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(200.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(result, fontSize = if (result.length > 6) 28.sp else 64.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(subtitle, modifier = Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ result = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅").let { val n = Random.nextInt(6); subtitle = "Кубик: ${n + 1}"; it[n] } }) { Text("Кубик") }
                Button({ val heads = Random.nextBoolean(); result = if (heads) "Орёл" else "Решка"; subtitle = "Монетка" }) { Text("Монетка") }
                Button({
                    val r = Random.nextInt(2) ; result = if (r == 0) "Да" else "Нет"; subtitle = "Да или нет?"
                }) { Text("Да / Нет") }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(min, { min = it }, label = { Text("От") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(max, { max = it }, label = { Text("До") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Button({
                val a = min.toLongOrNull(); val b = max.toLongOrNull()
                if (a != null && b != null && a <= b) { result = (a..b).random().toString(); subtitle = "Число от $a до $b" }
            }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Случайное число") }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(options, { options = it }, label = { Text("Варианты через запятую") }, modifier = Modifier.fillMaxWidth())
            Button({
                val list = options.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) { result = list.random(); subtitle = "Выбор из ${list.size}" }
            }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Выбрать за меня") }
        }
    }
}
