package com.example.assignment3.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.assignment3.sensor.MotionState
import kotlin.math.sqrt

/**
 * A tiny hand-drawn pixel "bean" character, no external art assets. One
 * shape + color grid, reused for every [MotionState]; the personality comes
 * from how it moves — a Compose [rememberInfiniteTransition] drives a
 * different squash/wobble/bounce animation per state.
 */
private const val GRID_SIZE = 12

private val OutlineColor = Color(0xFF7A5C4F)
private val BodyColor = Color(0xFFFFEFC2)
private val CheekColor = Color(0xFFFFAFC0)
private val EyeColor = Color(0xFF4A3B32)

private enum class Cell { EMPTY, OUTLINE, BODY, EYE, CHEEK }

private val beanGrid: Array<Array<Cell>> by lazy { buildBeanGrid() }

private fun buildBeanGrid(): Array<Array<Cell>> {
    val center = (GRID_SIZE - 1) / 2f
    val radius = GRID_SIZE / 2f - 0.5f
    val grid = Array(GRID_SIZE) { y ->
        Array(GRID_SIZE) { x ->
            val dx = x - center
            val dy = y - center
            val dist = sqrt(dx * dx + dy * dy)
            when {
                dist > radius -> Cell.EMPTY
                dist > radius - 1.3f -> Cell.OUTLINE
                else -> Cell.BODY
            }
        }
    }
    grid[4][4] = Cell.EYE
    grid[4][7] = Cell.EYE
    grid[7][3] = Cell.CHEEK
    grid[7][8] = Cell.CHEEK
    return grid
}

@Composable
fun MotionPixelArt(motionState: MotionState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "motionPixelArt")

    var scaleX = 1f
    var scaleY = 1f
    var rotation = 0f
    var translateX = 0f
    var translateY = 0f

    when (motionState) {
        MotionState.TAP -> {
            val squash by transition.animateFloat(
                initialValue = 0.82f,
                targetValue = 1.18f,
                animationSpec = infiniteRepeatable(tween(220, easing = LinearEasing), RepeatMode.Reverse),
                label = "tapSquash"
            )
            scaleX = squash
            scaleY = 2f - squash
        }

        MotionState.SHAKE -> {
            val wobble by transition.animateFloat(
                initialValue = -18f,
                targetValue = 18f,
                animationSpec = infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse),
                label = "shakeWobble"
            )
            rotation = wobble
            translateX = wobble / 3f
        }

        MotionState.WALK -> {
            val sway by transition.animateFloat(
                initialValue = -14f,
                targetValue = 14f,
                animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
                label = "walkSway"
            )
            val bob by transition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(tween(210, easing = LinearEasing), RepeatMode.Reverse),
                label = "walkBob"
            )
            rotation = sway / 2.2f
            translateX = sway
            translateY = bob
        }

        else -> {
            val breathe by transition.animateFloat(
                initialValue = 1f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
                label = "staticBreathe"
            )
            scaleX = breathe
            scaleY = breathe
        }
    }

    Canvas(
        modifier = modifier
            .size(96.dp)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
                this.rotationZ = rotation
                this.translationX = translateX
                this.translationY = translateY
            }
    ) {
        val pixelSize = size.minDimension / GRID_SIZE
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val color = when (beanGrid[y][x]) {
                    Cell.EMPTY -> null
                    Cell.OUTLINE -> OutlineColor
                    Cell.BODY -> BodyColor
                    Cell.EYE -> EyeColor
                    Cell.CHEEK -> CheekColor
                }
                if (color != null) {
                    drawRect(
                        color = color,
                        topLeft = Offset(x * pixelSize, y * pixelSize),
                        size = Size(pixelSize, pixelSize)
                    )
                }
            }
        }
    }
}
