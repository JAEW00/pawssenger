package com.pawssenger.app.ui.rest

import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pawssenger.app.data.NearbyPlace
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val TAG = "PlaceMapView"

/** OpenFreeMap: OpenStreetMap 기반 무료 벡터 지도. API 키가 필요 없다. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SRC_PLACES = "paw-places"
private const val SRC_REF = "paw-reference"
private const val LAYER_PLACES = "paw-places-layer"
private const val LOAD_TIMEOUT_MS = 6_000L

private const val BRAND = "#6B3FD4"

/**
 * 실제 지도 (MapLibre + OpenFreeMap). 기준점과 장소 핀을 표시하고, 핀을 누르면 [onSelect] 를 부른다.
 * 지도를 불러오지 못하면(인터넷 없음 등) [onUnavailable] 을 부른다 → 호출한 쪽이 약식 지도로 바꾼다.
 *
 * @param refLat 기준점(내 위치 또는 목적지) 위도
 */
@Composable
fun PlaceMapView(
    refLat: Double,
    refLng: Double,
    places: List<NearbyPlace>,
    selectedId: String?,
    modifier: Modifier,
    onSelect: (String) -> Unit,
    onUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    var styleReady by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            addOnDidFailLoadingMapListener { error ->
                Log.w(TAG, "map load failed: $error")
                currentOnUnavailable()
            }
            getMapAsync { map ->
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.isTiltGesturesEnabled = false
                map.uiSettings.isCompassEnabled = false
                map.setStyle(STYLE_URL) { style ->
                    useKoreanLabels(style)
                    addPawLayers(style)
                    map.addOnMapClickListener { point ->
                        val p = map.projection.toScreenLocation(point)
                        val hit = map.queryRenderedFeatures(RectF(p.x - 28, p.y - 28, p.x + 28, p.y + 28), LAYER_PLACES)
                        hit.firstOrNull()?.getStringProperty("id")?.let { currentOnSelect(it) }
                        hit.isNotEmpty()
                    }
                    styleReady = map to style
                }
            }
        }
    }

    // 일정 시간 안에 지도가 준비되지 않으면 사용할 수 없는 것으로 본다.
    LaunchedEffect(Unit) {
        delay(LOAD_TIMEOUT_MS)
        if (styleReady == null) { Log.w(TAG, "map load timed out"); currentOnUnavailable() }
    }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // 핀·기준점 갱신
    val ready = styleReady
    LaunchedEffect(ready, places, selectedId, refLat, refLng) {
        val (_, style) = ready ?: return@LaunchedEffect
        style.getSourceAs<GeoJsonSource>(SRC_PLACES)?.setGeoJson(
            FeatureCollection.fromFeatures(places.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.place.lng, p.place.lat)).apply {
                    addStringProperty("id", p.place.id)
                    addStringProperty("name", p.place.name)
                    addBooleanProperty("selected", p.place.id == selectedId)
                }
            })
        )
        style.getSourceAs<GeoJsonSource>(SRC_REF)?.setGeoJson(Feature.fromGeometry(Point.fromLngLat(refLng, refLat)))
    }

    // 기준점이나 장소 목록이 바뀌면 모두 보이도록 화면을 맞춘다 (선택만 바뀔 때는 움직이지 않는다).
    LaunchedEffect(ready, places.map { it.place.id }, refLat, refLng) {
        val (map, _) = ready ?: return@LaunchedEffect
        val shown = places.take(FIT_COUNT)
        if (shown.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(refLat, refLng), 13.0))
        } else {
            val bounds = LatLngBounds.Builder().include(LatLng(refLat, refLng))
                .apply { shown.forEach { include(LatLng(it.place.lat, it.place.lng)) } }.build()
            runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 110, 150, 110, 110), 600) }
                .onFailure { map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(refLat, refLng), 13.0)) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** 화면 맞춤에 쓰는 장소 수 (가까운 순). 먼 장소까지 넣으면 지도가 너무 넓어진다. */
private const val FIT_COUNT = 6

/**
 * 지명을 한글로만 표시한다. 기본 스타일은 영문과 한글을 함께 쓴다(예: "Seongnam 성남시").
 * 한글 이름(name:ko)이 없으면 현지 표기(name:nonlatin), 그것도 없으면 기본 이름을 쓴다.
 */
private fun useKoreanLabels(style: Style) {
    val korean = Expression.coalesce(Expression.get("name:ko"), Expression.get("name:nonlatin"), Expression.get("name"))
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        val field = layer.textField
        val text = (field.expression?.toString() ?: field.value?.toString()).orEmpty()
        // 도로 번호(ref) 같은 것은 건드리지 않고, 이름을 쓰는 레이어만 바꾼다.
        if (text.contains("name")) runCatching { layer.setProperties(PropertyFactory.textField(korean)) }
    }
}

private fun addPawLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_PLACES))
    style.addSource(GeoJsonSource(SRC_REF))
    val selected = Expression.get("selected")

    // 기준점: 흰 원 안에 보라색 점
    style.addLayer(CircleLayer("paw-ref-outer", SRC_REF).withProperties(
        PropertyFactory.circleRadius(15f), PropertyFactory.circleColor("#FFFFFF"),
        PropertyFactory.circleStrokeWidth(1f), PropertyFactory.circleStrokeColor("#D9D2EC"),
    ))
    style.addLayer(CircleLayer("paw-ref-inner", SRC_REF).withProperties(
        PropertyFactory.circleRadius(8f), PropertyFactory.circleColor(BRAND),
    ))
    // 장소 핀: 기본은 흰 바탕에 보라 테두리, 선택하면 보라로 채우고 크게
    style.addLayer(CircleLayer(LAYER_PLACES, SRC_PLACES).withProperties(
        PropertyFactory.circleRadius(Expression.switchCase(selected, Expression.literal(17f), Expression.literal(11f))),
        PropertyFactory.circleColor(Expression.switchCase(selected, Expression.literal(BRAND), Expression.literal("#FFFFFF"))),
        PropertyFactory.circleStrokeWidth(Expression.switchCase(selected, Expression.literal(4f), Expression.literal(3f))),
        PropertyFactory.circleStrokeColor(Expression.switchCase(selected, Expression.literal("#FFFFFF"), Expression.literal(BRAND))),
        PropertyFactory.circleSortKey(Expression.switchCase(selected, Expression.literal(1f), Expression.literal(0f))),
    ))
    // 선택한 장소의 이름
    style.addLayer(SymbolLayer("paw-places-label", SRC_PLACES).withProperties(
        PropertyFactory.textField(Expression.get("name")),
        PropertyFactory.textSize(15f),
        PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
        PropertyFactory.textColor("#1B1730"),
        PropertyFactory.textHaloColor("#FFFFFF"), PropertyFactory.textHaloWidth(2.5f),
        PropertyFactory.textOffset(arrayOf(0f, -2.1f)),
        PropertyFactory.textAllowOverlap(true), PropertyFactory.textIgnorePlacement(true),
    ).withFilter(Expression.eq(selected, Expression.literal(true))))
}
