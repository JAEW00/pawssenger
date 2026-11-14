package com.pawssenger.app.ui.rest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attractions
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawssenger.app.data.NearbyPlace
import com.pawssenger.app.data.PlaceType
import com.pawssenger.app.ui.components.T
import com.pawssenger.app.ui.theme.Paw
import kotlin.math.sqrt

fun PlaceType.icon(): ImageVector = when (this) {
    PlaceType.ATTRACTION -> Icons.Outlined.Park
    PlaceType.FOOD -> Icons.Outlined.LocalCafe
    PlaceType.STAY -> Icons.Outlined.Hotel
    PlaceType.LEISURE -> Icons.Outlined.Attractions
}

/**
 * 약식 지도 (실제 지도를 불러오지 못했을 때의 대체 화면).
 * 배경(도로·녹지·물)은 장식이고, 그 위에 기준점과 장소 핀을 올린다.
 * 핀은 방향은 실제대로, 거리는 √ 로 줄여서(먼 곳도 화면에 들어오도록) 배치하므로 축척은 맞지 않는다.
 */
@Composable
fun SketchMap(places: List<NearbyPlace>, selectedId: String?, modifier: Modifier, onSelect: (String) -> Unit) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(28.dp)).background(Color(0xFFEEF1F6))) {
        val w = maxWidth; val h = maxHeight
        val origin = Pair(w * 0.1875f, h * 0.88f)
        val unit = w * 0.2875f
        fun pos(p: NearbyPlace): Pair<Dp, Dp> {
            val d = p.distanceKm.coerceAtLeast(0.01)
            val r = sqrt(d).toFloat()
            return Pair(origin.first + unit * (p.eastKm / d * r).toFloat(), origin.second - unit * (p.northKm / d * r).toFloat())
        }
        val selected = places.firstOrNull { it.place.id == selectedId }

        Canvas(Modifier.fillMaxSize()) {
            val sw = size.width; val sh = size.height
            drawPath(Path().apply {
                moveTo(0f, sh * 0.58f); cubicTo(sw * 0.3f, sh * 0.66f, sw * 0.6f, sh * 0.55f, sw, sh * 0.66f)
                lineTo(sw, sh); lineTo(0f, sh); close()
            }, Color(0xFFD4E4F3))
            drawOval(Color(0xFFD5E8CF), Offset(sw * 0.2f, sh * 0.03f), Size(sw * 0.27f, sh * 0.25f))
            drawOval(Color(0xFFD5E8CF), Offset(sw * 0.75f, sh * 0.37f), Size(sw * 0.22f, sh * 0.26f))
            drawRoundRect(Color(0xFFD5E8CF), Offset(sw * 0.035f, sh * 0.34f), Size(sw * 0.14f, sh * 0.145f), CornerRadius(sw * 0.02f))
            val road = Stroke(sw * 0.016f, cap = StrokeCap.Round)
            fun roadPath(block: Path.() -> Unit) = drawPath(Path().apply(block), Color.White, style = road)
            roadPath { moveTo(0f, sh * 0.085f); cubicTo(sw * 0.3f, sh * 0.06f, sw * 0.6f, sh * 0.2f, sw, sh * 0.16f) }
            roadPath { moveTo(0f, sh * 0.28f); cubicTo(sw * 0.3f, sh * 0.25f, sw * 0.6f, sh * 0.36f, sw, sh * 0.31f) }
            roadPath { moveTo(sw * 0.275f, 0f); cubicTo(sw * 0.3f, sh * 0.4f, sw * 0.2f, sh * 0.7f, sw * 0.285f, sh) }
            roadPath { moveTo(sw * 0.51f, 0f); cubicTo(sw * 0.52f, sh * 0.4f, sw * 0.5f, sh * 0.7f, sw * 0.585f, sh) }
            roadPath { moveTo(sw * 0.755f, 0f); cubicTo(sw * 0.73f, sh * 0.4f, sw * 0.8f, sh * 0.7f, sw * 0.765f, sh) }

            val o = Offset(origin.first.toPx(), origin.second.toPx())
            if (selected != null) {
                val (px, py) = pos(selected)
                val t = Offset(px.toPx(), py.toPx())
                drawPath(Path().apply {
                    moveTo(o.x, o.y)
                    cubicTo(o.x + (t.x - o.x) * 0.25f, o.y + (t.y - o.y) * 0.75f, o.x + (t.x - o.x) * 0.2f, t.y, t.x, t.y)
                }, Paw.BrandStrong, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(Color.White, 20.dp.toPx(), o)
            drawCircle(Paw.BrandStrong, 11.dp.toPx(), o)
        }

        places.forEach { p ->
            val (x, y) = pos(p)
            if (x < 0.dp || x > w || y < 0.dp || y > h) return@forEach
            if (p.place.id == selectedId) {
                Box(Modifier.offset(x - 27.dp, y - 62.dp).size(54.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Paw.BrandStrong), contentAlignment = Alignment.Center) {
                    Icon(p.place.type.icon(), p.place.name, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Box(Modifier.offset(x - 90.dp, y - 104.dp).width(180.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.height(32.dp).clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        T(p.place.name, 14, FontWeight.Bold, maxLines = 1)
                    }
                }
            } else {
                Box(
                    Modifier.offset(x - 20.dp, y - 20.dp).size(40.dp).clip(CircleShape).background(Color.White)
                        .border(2.dp, Paw.BrandStrong, CircleShape).clickable { onSelect(p.place.id) },
                    contentAlignment = Alignment.Center,
                ) { Icon(p.place.type.icon(), p.place.name, tint = Paw.BrandStrong, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}
