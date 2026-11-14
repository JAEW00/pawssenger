package com.pawssenger.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pawssenger.app.ui.theme.Paw

/** 목록 한 페이지에 보여 주는 건수 */
const val PAGE_SIZE = 20

/** 스크롤바 막대를 목록 오른쪽 바깥(카드 여백)에 그린다. 내용과 겹치지 않는다. */
private fun ContentDrawScope.drawThumb(fraction: Float, thumbRatio: Float) {
    val w = 4.dp.toPx()
    val thumb = (size.height * thumbRatio).coerceIn(28.dp.toPx(), size.height)
    val y = fraction.coerceIn(0f, 1f) * (size.height - thumb)
    val x = size.width + 8.dp.toPx()
    drawRoundRect(Paw.Lilac, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
    drawRoundRect(Paw.BrandLine, Offset(x, y), Size(w, thumb), CornerRadius(w / 2))
}

/**
 * 넘길 내용이 있을 때만 오른쪽에 얇은 스크롤바를 보여 준다 (LazyColumn 용).
 * 줄 높이가 같다고 보고 위치를 어림한다.
 */
fun Modifier.listScrollbar(state: LazyListState): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || !(state.canScrollForward || state.canScrollBackward)) return@drawWithContent
    val rowHeight = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = rowHeight * info.totalItemsCount
    if (contentHeight <= size.height) return@drawWithContent
    val scrolled = state.firstVisibleItemIndex * rowHeight + state.firstVisibleItemScrollOffset
    drawThumb(scrolled / (contentHeight - size.height), size.height / contentHeight)
}

/** 넘길 내용이 있을 때만 오른쪽에 얇은 스크롤바를 보여 준다 (verticalScroll 용). verticalScroll 앞에 붙인다. */
fun Modifier.listScrollbar(state: ScrollState): Modifier = drawWithContent {
    drawContent()
    val max = state.maxValue
    if (max <= 0 || max == Int.MAX_VALUE) return@drawWithContent
    drawThumb(state.value.toFloat() / max, size.height / (size.height + max))
}

/** 페이지 이동: ‹ 1 / 3 › . 한 페이지뿐이면 아무것도 그리지 않는다. */
@Composable
fun Pager(page: Int, pageCount: Int, onPage: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (pageCount <= 1) return
    Row(modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        PagerArrow(PawIcons.ChevronLeft, enabled = page > 0) { onPage(page - 1) }
        Spacer(Modifier.width(16.dp))
        T("${page + 1} / $pageCount", 15, FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        PagerArrow(PawIcons.ChevronRight, enabled = page < pageCount - 1) { onPage(page + 1) }
    }
}

@Composable
private fun PagerArrow(icon: List<String>, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(if (enabled) Paw.Tile else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { StrokeIcon(icon, 20.dp, if (enabled) Paw.BrandStrong else Paw.Lilac, 2.2f) }
}

/** 전체 건수에 대한 페이지 수 */
fun pageCountOf(total: Int): Int = (total + PAGE_SIZE - 1) / PAGE_SIZE
