package org.opencrown.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.opencrown.app.payload.Compatibility
import org.opencrown.app.payload.PayloadManifest
import org.opencrown.app.root.PageCacheRelayChain

private val Paper = Color(0xFFFBFBFD)
private val PaperShade = Color(0xFFF2F2F7)
private val Ink = Color(0xFF1D1D1F)
private val Muted = Color(0xFF86868B)
private val AppleGreen = Color(0xFF34C759)
private val AppleRed = Color(0xFFFF3B30)
private val AppleBlue = Color(0xFF007AFF)

private enum class Stage { INITIALIZING, RUNNING, VERIFYING, WAITING, ROOTED, FAILED, UNSUPPORTED, COMPATIBLE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        setContent {
            AppleTheme {
                Surface(color = Paper) {
                    ChainScreen()
                }
            }
        }
    }
}

@Composable
private fun AppleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Paper,
            onSurface = Ink,
        ),
        typography = Typography(
            titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif),
        ),
        content = content,
    )
}

@Composable
private fun ChainScreen() {
    val context = LocalContext.current
    val chain = remember { PageCacheRelayChain(context.applicationContext) }
    val manifest = remember { runCatching { PayloadManifest.load(context) }.getOrNull() }
    val prefs = remember { context.getSharedPreferences("pagecache_relay", android.content.Context.MODE_PRIVATE) }
    var stage by remember { mutableStateOf(Stage.INITIALIZING) }
    var detail by remember { mutableStateOf("Preparing.") }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt, manifest) {
        val payloadManifest = manifest ?: run {
            stage = Stage.FAILED
            detail = "Payload manifest is missing."
            return@LaunchedEffect
        }
        val match = payloadManifest.match(PayloadManifest.currentDevice())
        when (match.compatibility) {
            Compatibility.UNSUPPORTED -> {
                stage = Stage.UNSUPPORTED
                detail = match.reason
                return@LaunchedEffect
            }
            Compatibility.COMPATIBLE -> {
                stage = Stage.COMPATIBLE
                detail = match.reason
                return@LaunchedEffect
            }
            Compatibility.VERIFIED -> Unit
        }
        val payload = withContext(Dispatchers.IO) {
            runCatching { payloadManifest.payloadBytes(context) }.getOrElse {
                stage = Stage.FAILED
                detail = "Payload entry could not be read."
                return@withContext null
            }
        } ?: return@LaunchedEffect
        if (payloadManifest.entrySize > 0 && payload.size != payloadManifest.entrySize) {
            stage = Stage.FAILED
            detail = "Payload size ${payload.size} does not match ${payloadManifest.entrySize}."
            return@LaunchedEffect
        }
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        val already = prefs.getInt("boot", -1) == boot && prefs.getBoolean("executed", false)
        if (already) {
            stage = Stage.VERIFYING
            detail = "Checking this boot."
            if (chain.isRootActive()) {
                stage = Stage.ROOTED
                return@LaunchedEffect
            }
            withContext(Dispatchers.IO) { chain.triggerInitLog() }
            stage = Stage.WAITING
            detail = "Waiting for the system."
            while (isActive) {
                if (chain.isRootActive()) {
                    stage = Stage.ROOTED
                    break
                }
                delay(400)
            }
            return@LaunchedEffect
        }

        stage = Stage.RUNNING
        detail = "Preparing."
        val ok = withContext(Dispatchers.IO) {
            chain.run(payload, object : PageCacheRelayChain.Listener {
                override fun onLog(line: String) {
                    detail = when {
                        line.contains("STEP A") -> "Reading the system."
                        line.contains("STEP D") || line.contains("Phase") -> "Writing."
                        line.contains("CHECK") || line.contains("Verified") -> "Verifying."
                        line.contains("PAGE CACHE") -> "Activating."
                        else -> detail
                    }
                }
            })
        }
        if (!ok) {
            stage = Stage.FAILED
            detail = "The chain did not complete."
            return@LaunchedEffect
        }

        prefs.edit().putInt("boot", boot).putBoolean("executed", true).apply()
        stage = Stage.VERIFYING
        detail = "Verifying."
        withContext(Dispatchers.IO) { chain.triggerInitLog() }
        stage = Stage.WAITING
        detail = "Waiting for the system."
        while (isActive) {
            if (chain.isRootActive()) {
                stage = Stage.ROOTED
                break
            }
            delay(400)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Paper, PaperShade)))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            DigitalCrownMark()
            Spacer(Modifier.height(34.dp))
            Text(
                text = "Open Crown",
                color = Ink,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.4).sp,
                fontFamily = FontFamily.SansSerif,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = (manifest?.name ?: "Page-cache Relay").uppercase(),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.2.sp,
                fontFamily = FontFamily.SansSerif,
            )
            Spacer(Modifier.height(58.dp))
            StatusBlock(stage = stage, detail = detail, onRetry = {
                prefs.edit().putBoolean("executed", false).apply()
                attempt++
            })
            Spacer(Modifier.height(64.dp))
            Text(
                text = if (stage == Stage.ROOTED) {
                    "Root is temporary. It ends at reboot."
                } else {
                    "One boot. No unlock. No wipe."
                },
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
            )
        }
    }
}

private fun manifestDeviceLine(): String {
    val facts = PayloadManifest.currentDevice()
    return "${facts.model} · ${facts.kernel}"
}

@Composable
private fun StatusBlock(stage: Stage, detail: String, onRetry: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (stage) {
            Stage.UNSUPPORTED, Stage.COMPATIBLE -> {
                InfoBadge()
                Spacer(Modifier.height(18.dp))
                Text(
                    if (stage == Stage.UNSUPPORTED) "Unsupported device" else "Compatible, untested",
                    color = Ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(detail, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(manifestDeviceLine(), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
            Stage.ROOTED -> {
                CheckBadge()
                Spacer(Modifier.height(18.dp))
                Text("Root reached", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text("Temporary until reboot", color = Muted, fontSize = 13.sp)
            }
            Stage.FAILED -> {
                CrossBadge()
                Spacer(Modifier.height(18.dp))
                Text("Initialization failed", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(detail, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry) {
                    Text("Try Again", color = AppleBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            else -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Ink,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(18.dp))
                Text("Initializing", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(detail, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun DigitalCrownMark() {
    Canvas(modifier = Modifier.size(66.dp)) {
        val cx = size.width * 0.45f
        val cy = size.height * 0.50f
        val outer = size.width * 0.29f
        val stemTop = cy - size.height * 0.055f

        // The crown's case-side stem.
        drawRoundRect(
            color = Ink,
            topLeft = Offset(cx + outer - size.width * 0.025f, stemTop),
            size = Size(size.width * 0.20f, size.height * 0.11f),
            cornerRadius = CornerRadius(size.height * 0.055f),
        )

        // Knurled rim.
        drawCircle(
            color = Ink,
            radius = outer,
            center = Offset(cx, cy),
            style = Stroke(width = size.width * 0.052f),
        )
        drawCircle(
            color = Ink,
            radius = outer * 0.57f,
            center = Offset(cx, cy),
            style = Stroke(width = size.width * 0.042f),
        )

        // Ridges around the crown.
        val tickInner = outer * 0.66f
        val tickOuter = outer * 0.94f
        for (i in 0 until 12) {
            val angle = i * Math.PI / 6.0
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = Ink,
                start = Offset(cx + cos * tickInner, cy + sin * tickInner),
                end = Offset(cx + cos * tickOuter, cy + sin * tickOuter),
                strokeWidth = size.width * 0.028f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun InfoBadge() {
    Canvas(modifier = Modifier.size(48.dp)) {
        drawCircle(color = Muted.copy(alpha = 0.16f))
        drawLine(
            color = Muted,
            start = Offset(size.width * 0.5f, size.height * 0.26f),
            end = Offset(size.width * 0.5f, size.height * 0.56f),
            strokeWidth = size.width * 0.06f,
            cap = StrokeCap.Round,
        )
        drawCircle(color = Muted, radius = size.width * 0.035f, center = Offset(size.width * 0.5f, size.height * 0.70f))
    }
}

@Composable
private fun CheckBadge() {
    Canvas(modifier = Modifier.size(48.dp)) {
        drawCircle(color = AppleGreen)
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.74f, size.height * 0.34f)
        }
        drawPath(
            path = path,
            color = Color.White,
            style = Stroke(width = size.width * 0.07f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun CrossBadge() {
    Canvas(modifier = Modifier.size(48.dp)) {
        drawCircle(color = AppleRed)
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.32f, size.height * 0.32f),
            end = Offset(size.width * 0.68f, size.height * 0.68f),
            strokeWidth = size.width * 0.07f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.68f, size.height * 0.32f),
            end = Offset(size.width * 0.32f, size.height * 0.68f),
            strokeWidth = size.width * 0.07f,
            cap = StrokeCap.Round,
        )
    }
}
