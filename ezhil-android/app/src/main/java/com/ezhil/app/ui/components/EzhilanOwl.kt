package com.ezhil.app.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Ezhilan the owl, drawn parametrically on a Compose Canvas — no image
 * assets. Poses are derived from [EzhilanState]; everything animates with
 * springs. All coordinates live in a 100×100 design space scaled to [size].
 *
 * Design rules: PROCESSING looks thoughtful (never worried), CELEBRATING is
 * bouncy (never frantic), and the owl blinks so it always feels alive.
 */

private val OwlTeal = Color(0xFF0F2E33)
private val OwlTealLight = Color(0xFF17444B)
private val OwlCream = Color(0xFFFBF3E0)
private val OwlCyan = Color(0xFF62F9EE)
private val OwlAmber = Color(0xFFFFB955)

@Composable
fun EzhilanOwl(
    state: EzhilanState = EzhilanState.IDLE,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    // ── Pose targets per state ────────────────────────────────────────────
    val wingTarget = when (state) {
        EzhilanState.IDLE        -> 0f
        EzhilanState.PROCESSING  -> 38f     // wing to chin
        EzhilanState.CELEBRATING -> 125f    // both wings up
    }
    val headTiltTarget = when (state) {
        EzhilanState.IDLE        -> 0f
        EzhilanState.PROCESSING  -> 7f
        EzhilanState.CELEBRATING -> -4f
    }

    val wing by animateFloatAsState(
        targetValue = wingTarget,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "wing"
    )
    val headTilt by animateFloatAsState(
        targetValue = headTiltTarget,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "head"
    )

    // ── Continuous life: breathing, celebratory hops, thinking sway ──────
    val infinite = rememberInfiniteTransition(label = "owl_life")
    val breath by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breath"
    )
    val hop by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (state == EzhilanState.CELEBRATING) 1f else 0f,
        animationSpec = infiniteRepeatable(tween(420, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "hop"
    )
    val sway by infinite.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "sway"
    )

    // ── Blinks (random 3–6 s) ─────────────────────────────────────────────
    var lidTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        while (true) {
            delay(3000L + Random.nextLong(3000L))
            lidTarget = 1f
            delay(130)
            lidTarget = 0f
        }
    }
    val lids by animateFloatAsState(
        targetValue = lidTarget,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "lids"
    )

    // Pupils wander on their own (looking around) — subtle life on a device
    // with no pointer to track.
    var pupilSeed by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2200L + Random.nextLong(2600L))
            pupilSeed = Offset(Random.nextFloat() * 2f - 1f, Random.nextFloat() * 1.2f - 0.4f)
        }
    }
    val pupilX by animateFloatAsState(
        pupilSeed.x * 3.4f, spring(stiffness = Spring.StiffnessLow), label = "px"
    )
    val pupilY by animateFloatAsState(
        pupilSeed.y * 2.6f, spring(stiffness = Spring.StiffnessLow), label = "py"
    )

    val celebrating = state == EzhilanState.CELEBRATING
    val processing = state == EzhilanState.PROCESSING

    Canvas(modifier = modifier.size(size)) {
        val u = this.size.minDimension / 100f   // design unit

        translate(top = -hop * 7f * u) {
            // Whole-body breathing (anchored at the feet)
            scale(scaleX = 1f, scaleY = 1f + breath * 0.02f, pivot = Offset(50f * u, 96f * u)) {

                // Wings (rotate at the shoulders)
                rotate(degrees = -wing, pivot = Offset(27f * u, 56f * u)) {
                    drawOval(OwlTealLight, Offset(11f * u, 47f * u), Size(20f * u, 32f * u))
                }
                rotate(degrees = wing, pivot = Offset(73f * u, 56f * u)) {
                    drawOval(OwlTealLight, Offset(69f * u, 47f * u), Size(20f * u, 32f * u))
                }

                // Torso + belly — v2: torso overlaps the head heavily so the
                // two shapes read as one egg silhouette (design-brief §3)
                drawOval(OwlTeal, Offset(21f * u, 35f * u), Size(58f * u, 58f * u))
                drawOval(OwlCream, Offset(33f * u, 54f * u), Size(34f * u, 34f * u))
                // Feather marks
                featherArc(u, 42f, 67f)
                featherArc(u, 46f, 75f)

                // Feet
                foot(u, 40f)
                foot(u, 60f)

                // Head (tilts; sways gently while processing)
                val tilt = headTilt + if (processing) sway * 2.5f else 0f
                rotate(degrees = tilt, pivot = Offset(50f * u, 48f * u)) {
                    drawCircle(OwlTeal, 27f * u, Offset(50f * u, 36f * u))

                    // Ear tufts
                    earTuft(u, left = true)
                    earTuft(u, left = false)

                    // Brows (raise when excited/thinking) — thick, they carry
                    // the expression now that the eyes dominate the face
                    val browLift = when {
                        celebrating -> 3.5f
                        processing  -> 2.5f
                        else        -> 0f
                    }
                    brow(u, 27f, 21f - browLift, flip = false)
                    brow(u, 56f, 19.5f - browLift, flip = true)

                    // Eyes — v2: big and nearly touching (≈45% of head height)
                    eye(u, 36.5f, lids, pupilX, pupilY, happy = celebrating)
                    eye(u, 63.5f, lids, pupilX, pupilY, happy = celebrating)

                    // Beak — small rounded triangle (big beaks read grumpy)
                    val beak = Path().apply {
                        moveTo(50f * u, 48f * u)
                        lineTo(45.8f * u, 51f * u)
                        quadraticBezierTo(50f * u, 56.5f * u, 54.2f * u, 51f * u)
                        close()
                    }
                    drawPath(beak, OwlAmber)
                }
            }
        }

        // Celebration stars burst outward, driven by the hop phase
        if (celebrating) {
            val burst = hop
            for (i in 0 until 6) {
                val angle = Math.toRadians((i * 60f + 20f).toDouble())
                val dist = (28f + burst * 22f) * u
                val cx = 50f * u + (Math.cos(angle) * dist).toFloat()
                val cy = 40f * u + (Math.sin(angle) * dist * 0.8f).toFloat()
                star(u, cx, cy, (2.6f + (i % 2) * 1.4f) * u * (0.6f + burst * 0.4f), 1f - burst * 0.8f)
            }
        }
    }
}

// ── Drawing helpers (design-space coordinates × u) ───────────────────────────

private fun DrawScope.eye(
    u: Float, cx: Float, lids: Float, px: Float, py: Float, happy: Boolean
) {
    drawCircle(Color.White, 13.5f * u, Offset(cx * u, 36f * u))
    drawCircle(OwlTealLight, 13.5f * u, Offset(cx * u, 36f * u), style = Stroke(1.4f * u))

    if (happy) {
        // Happy arc instead of open eye
        val arc = Path().apply {
            moveTo((cx - 8.5f) * u, 37.5f * u)
            quadraticBezierTo(cx * u, 27.5f * u, (cx + 8.5f) * u, 37.5f * u)
        }
        drawPath(arc, OwlTeal, style = Stroke(4f * u, cap = StrokeCap.Round))
        return
    }

    drawCircle(OwlTeal, 6.6f * u, Offset((cx + px) * u, (36f + py) * u))
    drawCircle(Color.White, 2.2f * u, Offset((cx + px + 2.3f) * u, (33.6f + py) * u))
    drawCircle(Color.White.copy(alpha = 0.7f), 1f * u, Offset((cx + px - 2f) * u, (38.6f + py) * u))

    // Eyelid sweeps down over the eye
    if (lids > 0.02f) {
        drawOval(
            OwlTeal,
            Offset((cx - 14f) * u, (22.5f - 28f + lids * 28f) * u),
            Size(28f * u, 28f * u)
        )
    }
}

private fun DrawScope.brow(u: Float, x: Float, y: Float, flip: Boolean) {
    val p = Path().apply {
        moveTo(x * u, y * u)
        if (flip) quadraticBezierTo((x + 8f) * u, (y - 3f) * u, (x + 15f) * u, (y + 2f) * u)
        else quadraticBezierTo((x + 8f) * u, (y - 5f) * u, (x + 15f) * u, (y - 1f) * u)
    }
    drawPath(p, OwlCyan, style = Stroke(3f * u, cap = StrokeCap.Round))
}

private fun DrawScope.earTuft(u: Float, left: Boolean) {
    val p = Path().apply {
        if (left) {
            moveTo(30f * u, 16f * u)
            quadraticBezierTo(28f * u, 7f * u, 36f * u, 5f * u)
            quadraticBezierTo(37f * u, 12f * u, 35f * u, 15f * u)
        } else {
            moveTo(70f * u, 16f * u)
            quadraticBezierTo(72f * u, 7f * u, 64f * u, 5f * u)
            quadraticBezierTo(63f * u, 12f * u, 65f * u, 15f * u)
        }
        close()
    }
    drawPath(p, OwlTeal)
}

private fun DrawScope.featherArc(u: Float, x: Float, y: Float) {
    val p = Path().apply {
        moveTo(x * u, y * u)
        quadraticBezierTo((x + 4f) * u, (y + 3f) * u, (x + 8f) * u, y * u)
    }
    drawPath(p, OwlAmber.copy(alpha = 0.55f), style = Stroke(1.4f * u, cap = StrokeCap.Round))
}

private fun DrawScope.foot(u: Float, x: Float) {
    for (dx in listOf(-3f, 0f, 3f)) {
        drawLine(
            OwlAmber,
            Offset(x * u, 91f * u),
            Offset((x + dx) * u, 97f * u),
            strokeWidth = 2.4f * u,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.star(u: Float, cx: Float, cy: Float, r: Float, alpha: Float) {
    val p = Path().apply {
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else r * 0.45f
            val a = Math.toRadians((i * 36f - 90f).toDouble())
            val x = cx + (Math.cos(a) * rad).toFloat()
            val y = cy + (Math.sin(a) * rad).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(p, OwlAmber.copy(alpha = alpha.coerceIn(0f, 1f)))
}
