package com.example.couchto5k.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Applies a stretchy and squishy UI overscroll effect to a scrollable container.
 * When pulling past the top or bottom bounds, the container stretches vertically (scaleY),
 * squishes horizontally (scaleX), and spring-bounces back when released.
 *
 * @param maxOverscrollPx Maximum pixels of displacement allowed.
 * @param stretchIntensity Factor controlling how much scale deformation occurs.
 */
fun Modifier.squishyOverscroll(
    maxOverscrollPx: Float = 100f,
    stretchIntensity: Float = 0.00033f,
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val overscrollOffset = remember { Animatable(0f) }

    // Stiffness 1800f provides 3x faster spring oscillation speed than StiffnessLow (200f)
    val snapBackSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = 1800f,
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val current = overscrollOffset.value
                if ((source == NestedScrollSource.Drag) && (current != 0f)) {
                    val delta = available.y
                    if ((current > 0f && delta < 0f) || (current < 0f && delta > 0f)) {
                        val newOffset = if (current > 0f) {
                            (current + delta * 0.15f).coerceAtLeast(0f)
                        } else {
                            (current + delta * 0.15f).coerceAtMost(0f)
                        }
                        val consumedY = (newOffset - current) / 0.15f
                        coroutineScope.launch {
                            overscrollOffset.snapTo(newOffset)
                        }
                        return Offset(0f, consumedY)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.Drag) {
                    val delta = available.y
                    if (delta != 0f) {
                        val current = overscrollOffset.value
                        val newOffset = (current + delta * 0.133f).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                        coroutineScope.launch {
                            overscrollOffset.snapTo(newOffset)
                        }
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (overscrollOffset.value != 0f) {
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = snapBackSpec,
                        )
                    }
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y != 0f) {
                    val flingImpact = (available.y * 0.02f).coerceIn(-maxOverscrollPx * 0.5f, maxOverscrollPx * 0.5f)
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = flingImpact,
                            animationSpec = snapBackSpec,
                        )
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = snapBackSpec,
                        )
                    }
                } else if (overscrollOffset.value != 0f) {
                    coroutineScope.launch {
                        overscrollOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = snapBackSpec,
                        )
                    }
                }
                return Velocity.Zero
            }
        }
    }

    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                if (overscrollOffset.value != 0f) {
                    coroutineScope.launch {
                        overscrollOffset.snapTo(0f)
                    }
                }
            }
        }
        .nestedScroll(nestedScrollConnection)
        .graphicsLayer {
            val offset = overscrollOffset.value
            val absOffset = abs(offset)
            if (absOffset > 0f) {
                val scaleYChange = absOffset * stretchIntensity
                val scaleXChange = scaleYChange * 0.35f

                scaleY = 1f + scaleYChange
                scaleX = (1f - scaleXChange).coerceAtLeast(0.95f)
                translationY = offset * 0.15f

                transformOrigin = if (offset > 0f) {
                    TransformOrigin(0.5f, 0f)
                } else {
                    TransformOrigin(0.5f, 1f)
                }
            }
        }
}
