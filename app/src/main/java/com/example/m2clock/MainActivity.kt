package com.example.m2clock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import android.media.RingtoneManager
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.m2clock.ui.theme.M2clockTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import com.google.genai.Client
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.graphics.Paint

// クライアントの初期化（※実際のアプリではキーを直接書かず、local.propertiesなどで管理してください）
val client = Client.builder()
    .apiKey("AIzaSyArILv04ellTmY4tjz-HNcsoZRMzkNJrIM")
    .build()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 通知権限のリクエスト (Android 13以降)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        lifecycleScope.launch {
            try {
                // JavaのSDKのため、名前付き引数は使用せず、(モデル, プロンプト, 設定) の順で渡します
                val response = client.models.generateContent(
                    "gemini-2.0-flash",
                    "Androidアプリ開発について、初心者が最初に学ぶべきことを1行で教えて",
                    null
                )
                println(response.text()) // AIからの返答 (メソッド呼び出し)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            M2clockTheme(darkTheme = true, dynamicColor = false) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Black
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        ClockDashboard()
                    }
                }
            }
        }
    }
}

@Composable
fun ClockDashboard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var dateTime by remember { mutableStateOf(LocalDateTime.now()) }
    var holidays by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var lastChimeHour by remember { mutableStateOf(-1) }
    
    // オプション設定用の状態
    var isChimeEnabled by remember { mutableStateOf(true) }
    var chimeStartHour by remember { mutableStateOf(7) }
    var chimeEndHour by remember { mutableStateOf(18) }

    // 画面遷移用の状態
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 時刻更新ループ
        while (true) {
            dateTime = LocalDateTime.now()
            delay(1000)
        }
    }

    LaunchedEffect(dateTime) {
        val hour = dateTime.hour
        val minute = dateTime.minute

        if (isChimeEnabled && minute == 0 && hour in chimeStartHour..chimeEndHour && hour != lastChimeHour) {
            lastChimeHour = hour
            try {
                var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                if (uri == null) {
                    uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                
                val r = RingtoneManager.getRingtone(context, uri)
                r?.apply {
                    audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        // 祝祭日データの取得 (holidays-jp.github.io API)
        withContext(Dispatchers.IO) {
            try {
                val jsonText = URL("https://holidays-jp.github.io/api/v1/date.json").readText()
                val json = JSONObject(jsonText)
                val holidayDates = mutableSetOf<LocalDate>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    holidayDates.add(LocalDate.parse(keys.next()))
                }
                holidays = holidayDates
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreen(
                isEnabled = isChimeEnabled,
                onEnabledChange = { isChimeEnabled = it },
                startHour = chimeStartHour,
                onStartHourChange = { chimeStartHour = it },
                endHour = chimeEndHour,
                onEndHourChange = { chimeEndHour = it },
                onBack = { showSettings = false }
            )
        } else {
            // メイン時計画面
            ClockMainLayout(
                dateTime = dateTime,
                holidays = holidays
            )
            
            // 右上の設定ボタン
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun ClockMainLayout(
    dateTime: LocalDateTime,
    holidays: Set<LocalDate>
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    if (isPortrait) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 上部: アナログ時計
            Box(
                modifier = Modifier.weight(1.5f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ClockFace(dateTime = dateTime, modifier = Modifier.size(300.dp))
            }

            // 中部: デジタル時刻
            Box(
                modifier = Modifier.weight(0.7f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // 下部: 日付
            Box(
                modifier = Modifier.weight(0.8f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.White
                    )
                    val isHoliday = holidays.contains(dateTime.toLocalDate())
                    val dayOfWeekColor = when {
                        isHoliday || dateTime.dayOfWeek == java.time.DayOfWeek.SUNDAY -> Color.Red
                        dateTime.dayOfWeek == java.time.DayOfWeek.SATURDAY -> Color.Blue
                        else -> Color.White
                    }
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("EEEE", Locale.JAPANESE)),
                        style = MaterialTheme.typography.headlineMedium,
                        color = dayOfWeekColor
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左半分: 24時間計アナログ時計
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                ClockFace(dateTime = dateTime, modifier = Modifier.size(320.dp))
            }

            // 右半分: デジタル表示と日付
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                // 右上: デジタル時刻
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 90.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                // 右下: 日付
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dateTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")),
                            style = MaterialTheme.typography.headlineLarge,
                            fontSize = 60.sp,
                            color = Color.White
                        )
                        val isHoliday = holidays.contains(dateTime.toLocalDate())
                        val dayOfWeekColor = when {
                            isHoliday || dateTime.dayOfWeek == java.time.DayOfWeek.SUNDAY -> Color.Red
                            dateTime.dayOfWeek == java.time.DayOfWeek.SATURDAY -> Color.Blue
                            else -> Color.White
                        }
                        Text(
                            text = dateTime.format(DateTimeFormatter.ofPattern("EEEE", Locale.JAPANESE)),
                            style = MaterialTheme.typography.headlineMedium,
                            fontSize = 40.sp,
                            color = dayOfWeekColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    startHour: Int,
    onStartHourChange: (Int) -> Unit,
    endHour: Int,
    onEndHourChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text("設定", color = Color.White, style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("時報の設定", color = Color.White, style = MaterialTheme.typography.titleLarge)
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("時報を鳴らす", color = Color.White)
            Switch(checked = isEnabled, onCheckedChange = onEnabledChange)
        }

        if (isEnabled) {
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("開始時間: ${startHour}時", color = Color.White)
            Slider(
                value = startHour.toFloat(),
                onValueChange = { onStartHourChange(it.toInt()) },
                valueRange = 0f..23f,
                steps = 22
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("終了時間: ${endHour}時", color = Color.White)
            Slider(
                value = endHour.toFloat(),
                onValueChange = { onEndHourChange(it.toInt()) },
                valueRange = 0f..23f,
                steps = 22
            )
        }
    }
}

@Composable
fun ClockFace(dateTime: LocalDateTime, modifier: Modifier = Modifier) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = onSurfaceColor.toArgb()
    
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * 0.4f
        val labelRadius = size.minDimension * 0.47f
        
        // 外枠
        drawCircle(
            color = onSurfaceColor,
            radius = radius,
            center = center,
            style = Stroke(width = 8.dp.toPx())
        )
        
        // 秒針 (円周の外側を回るゴールドの大きな三角形 - 文字の下に表示するため先に描画)
        val secondAngle = (dateTime.second * 6 - 90) * (PI / 180).toFloat()
        val triangleHeight = 30.dp.toPx()
        val triangleWidth = 24.dp.toPx()
        val baseRadius = radius + 1.dp.toPx()
        val tipRadius = baseRadius + triangleHeight
        
        val tip = Offset(
            center.x + tipRadius * cos(secondAngle),
            center.y + tipRadius * sin(secondAngle)
        )
        val baseAngleOffset = triangleWidth / (2 * baseRadius)
        val baseLeft = Offset(
            center.x + baseRadius * cos(secondAngle - baseAngleOffset),
            center.y + baseRadius * sin(secondAngle - baseAngleOffset)
        )
        val baseRight = Offset(
            center.x + baseRadius * cos(secondAngle + baseAngleOffset),
            center.y + baseRadius * sin(secondAngle + baseAngleOffset)
        )
        
        val trianglePath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(baseLeft.x, baseLeft.y)
            lineTo(baseRight.x, baseRight.y)
            close()
        }
        drawPath(trianglePath, Color(0xFFFFD700)) // ゴールド

        // 24時間周期の目盛りと文字 (12が上)
        for (i in 0 until 24) {
            // 12が上に来るように角度を計算。12時の位置が -90度。
            // 1時間あたり 360/24 = 15度
            val angleDegree = (i - 12) * 15f - 90f
            val angleRad = angleDegree * (PI / 180).toFloat()
            
            // 目盛り
            val tickLength = if (i % 6 == 0) 15.dp.toPx() else 8.dp.toPx()
            val start = Offset(
                center.x + (radius - tickLength) * cos(angleRad),
                center.y + (radius - tickLength) * sin(angleRad)
            )
            val end = Offset(
                center.x + radius * cos(angleRad),
                center.y + radius * sin(angleRad)
            )
            drawLine(onSurfaceColor, start, end, strokeWidth = (if (i % 6 == 0) 3.dp else 1.dp).toPx())
            
            // 文字盤の数字 (0-23)
            drawContext.canvas.nativeCanvas.apply {
                val label = i.toString()
                val paint = Paint().apply {
                    color = textColor
                    textAlign = Paint.Align.CENTER
                    textSize = 14.sp.toPx()
                    isFakeBoldText = (i % 6 == 0)
                }
                val x = center.x + labelRadius * cos(angleRad)
                val y = center.y + labelRadius * sin(angleRad) + (paint.textSize / 3)
                drawText(label, x, y, paint)
            }
        }
        
        // 分針 (60分で1周)
        val minuteAngle = (dateTime.minute * 6 + dateTime.second * 0.1f - 90) * (PI / 180).toFloat()
        drawLine(
            onSurfaceColor,
            center,
            Offset(
                center.x + (radius * 0.8f) * cos(minuteAngle),
                center.y + (radius * 0.8f) * sin(minuteAngle)
            ),
            strokeWidth = 6.dp.toPx()
        )
        
        // 時針 (24時間で1周)
        // 12時が上(-90度)なので、(時 - 12) * 15度
        val hourAngle = ((dateTime.hour + dateTime.minute / 60f) - 12) * 15f - 90f
        val hourAngleRad = hourAngle * (PI / 180).toFloat()
        val hourHandColor = if (dateTime.hour < 12) Color(0xFFF3AA28) else primaryColor
        drawLine(
            hourHandColor,
            center,
            Offset(
                center.x + (radius * 0.6f) * cos(hourAngleRad),
                center.y + (radius * 0.6f) * sin(hourAngleRad)
            ),
            strokeWidth = 10.dp.toPx()
        )

        // XEyes (中心の目 - 秒針の先端 tip を追いかける)
        val eyeWidth = 24.dp.toPx()
        val eyeHeight = 30.dp.toPx()
        val eyeGap = 4.dp.toPx()
        val pupilSize = 6.dp.toPx()
        val maxPupilOffset = 7.dp.toPx()

        val leftEyeCenter = Offset(center.x - eyeWidth / 2 - eyeGap / 2, center.y)
        val rightEyeCenter = Offset(center.x + eyeWidth / 2 + eyeGap / 2, center.y)

        // 白目 (楕円)
        drawOval(
            color = Color.White,
            topLeft = Offset(leftEyeCenter.x - eyeWidth / 2, leftEyeCenter.y - eyeHeight / 2),
            size = Size(eyeWidth, eyeHeight)
        )
        drawOval(
            color = Color.White,
            topLeft = Offset(rightEyeCenter.x - eyeWidth / 2, rightEyeCenter.y - eyeHeight / 2),
            size = Size(eyeWidth, eyeHeight)
        )

        // 黒目の位置計算
        fun getPupilOffset(eyeCenter: Offset, target: Offset): Offset {
            val dx = target.x - eyeCenter.x
            val dy = target.y - eyeCenter.y
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            return if (dist > 0) {
                Offset(
                    eyeCenter.x + (dx / dist) * maxPupilOffset,
                    eyeCenter.y + (dy / dist) * maxPupilOffset
                )
            } else eyeCenter
        }

        drawCircle(Color.Black, radius = pupilSize / 2, center = getPupilOffset(leftEyeCenter, tip))
        drawCircle(Color.Black, radius = pupilSize / 2, center = getPupilOffset(rightEyeCenter, tip))
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
fun ClockDashboardPreview() {
    M2clockTheme {
        ClockDashboard()
    }
}
