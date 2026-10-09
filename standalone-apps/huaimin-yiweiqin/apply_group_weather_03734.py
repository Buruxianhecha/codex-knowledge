#!/usr/bin/env python3
"""v0.37.34: genuine weather in group chat, with optional authorized GPS weather."""
from pathlib import Path
import shutil
import sys

root = Path(sys.argv[1]).resolve()
here = Path(__file__).resolve().parent
base = root / "app/src/main/java/com/cleo/cleos"
tools = base / "ai/Tools.kt"
weather = base / "ai/Weather.kt"
chat = base / "ai/ChatRepository.kt"

def once(path: Path, before: str, after: str):
    content = path.read_text(encoding="utf-8")
    count = content.count(before)
    if count != 1:
        raise SystemExit(f"0.37.34 weather patch: {path.name} anchor count {count}: {before[:120]!r}")
    path.write_text(content.replace(before, after, 1), encoding="utf-8")

# The original weather capability exists in single chats. Make the same weather
# backend usable from GPS coordinates, rather than geocoding "浦东新区" as a
# possibly unrelated township or pretending Shanghai city-wide data is street-local.
once(tools,
'''interface WeatherSource {
    /** Throws [ToolFailure] when the place is unknown or the service can't be reached. */
    suspend fun report(city: String, days: Int): WeatherReport
}''',
'''interface WeatherSource {
    /** Throws [ToolFailure] when the place is unknown or the service can't be reached. */
    suspend fun report(city: String, days: Int): WeatherReport
}

/** Explicit opt-in phone GPS weather. Other WeatherSource fakes need no changes. */
interface GeoWeatherSource : WeatherSource {
    suspend fun reportAt(latitude: Double, longitude: Double, placeName: String, days: Int): WeatherReport
}''')

once(weather,
'''class OpenMeteo(http: OkHttpClient) : WeatherSource {''',
'''class OpenMeteo(http: OkHttpClient) : GeoWeatherSource {''')

once(weather,
'''    private fun geocodeUrl(name: String): HttpUrl =''',
'''    /** Phone GPS weather without ambiguous place-name geocoding. */
    override suspend fun reportAt(latitude: Double, longitude: Double, placeName: String, days: Int): WeatherReport {
        if (!latitude.isFinite() || !longitude.isFinite() || latitude !in -90.0..90.0 || longitude !in -180.0..180.0) {
            throw ToolFailure("手机提供的位置坐标异常，无法查询天气。", "定位坐标有误")
        }
        val spot = Weather.Place(placeName.trim().take(90).ifEmpty { "手机定位附近" },
            null, null, latitude, longitude, 0)
        try {
            return WeatherReport(spot.name, Weather.describe(spot, get(forecastUrl(spot, days.coerceIn(1, 7)))))
        } catch (e: IOException) {
            throw ToolFailure("天气服务暂时连不上（¤{e.message ?: "网络错误"}），稍后重试。", "天气网络出错")
        } catch (e: IllegalArgumentException) {
            throw ToolFailure("天气服务返回的数据无法解析，稍后重试。", "天气服务数据错误")
        }
    }

    private fun geocodeUrl(name: String): HttpUrl ='''.replace("¤","$"))

once(tools,
'''            "days" to prop("integer", "预报几天，默认 3，最多 7"),
        ),
    )

    val getLocation = ToolSpec(''',
'''            "days" to prop("integer", "预报几天，默认 3，最多 7"),
            "use_phone_location" to prop("boolean",
                "只有用户明确要求按手机定位查天气时才设 true。需要查位置权限；否则按 city 或设置中的天气城市查询。"),
        ),
    )

    val getLocation = ToolSpec(''')

once(tools,
'''    private suspend fun getWeather(a: JsonObject, settings: AppSettings): ToolOutcome {
        val city = ToolArgs.text(a, "city")?.trim().orEmpty().ifEmpty { settings.weatherCity.trim() }
        if (city.isEmpty()) {
            throw ToolFailure("不知道对方在哪个城市。先问一下，再带上 city 查。", "不知道在哪个城市")
        }
        val days = (ToolArgs.int(a["days"]) ?: 3).coerceIn(1, 7)
        val report = weather.report(city, days)
        return ToolOutcome(report.text, "查了¤{report.place}的天气")
    }'''.replace("¤","$"),
'''    private suspend fun getWeather(a: JsonObject, settings: AppSettings): ToolOutcome {
        val days = (ToolArgs.int(a["days"]) ?: 3).coerceIn(1, 7)
        val usePhoneLocation = (a["use_phone_location"] as? JsonPrimitive)?.content == "true"
        if (usePhoneLocation) {
            if (ToolGroup.Location !in settings.tools)
                throw ToolFailure("没有开启「查位置」功能。请在设置 → 能做的事中授权后重试，或直接指定城市。", "位置功能未启用")
            val place = (location ?: throw ToolFailure("这台手机没有可用的定位功能。", "定位不可用")).here()
            val geo = weather as? GeoWeatherSource
                ?: throw ToolFailure("当前天气提供方暂不支持手机坐标查询，可直接提供城市名。", "坐标天气未启用")
            val report = geo.reportAt(place.lat, place.lon, place.area ?: place.address ?: "手机定位附近", days)
            val age = if (place.ageMs >= 2 * 60_000L) "；使用的是¤{place.ageMs / 60_000} 分钟前的手机定位" else ""
            val accuracy = place.accuracy?.let { "；定位误差约 ¤{it.toInt()} 米" }.orEmpty()
            return ToolOutcome("按用户请求使用手机实际定位坐标查询附近的天气¤accuracy¤age：\\n¤{report.text}",
                "查了手机定位处的天气")
        }
        val city = ToolArgs.text(a, "city")?.trim().orEmpty().ifEmpty { settings.weatherCity.trim() }
        if (city.isEmpty())
            throw ToolFailure("不知道对方在哪个城市。先问一下，再带上 city 查。", "不知道在哪个城市")
        val report = weather.report(city, days)
        return ToolOutcome(report.text, "查了¤{report.place}的天气")
    }'''.replace("¤","$"))

# Consume weather ONLY on an explicit fresh user turn, never proactively when
# an AI quotes a weather question or the user simply continues the group.
once(chat,
'''        val groupLocation = if (!continued && trigger?.role == "user" && GroupLocationRules.explicitlyRequested(latestText)) {''',
'''        val weatherRequest = if (!continued && trigger?.role == "user") GroupWeatherRules.parse(latestText) else null
        val groupLocation = if (!continued && trigger?.role == "user" &&
            GroupLocationRules.explicitlyRequested(latestText) && weatherRequest?.usePhoneLocation != true) {''')

once(chat,
'''        } else null
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }''',
'''        } else null
        val groupWeather = weatherRequest?.let { request ->
            val outcome = try {
                // Uses ToolBox's existing permission switch for weather and (optionally) location.
                tools.run(
                    ToolCall("group-weather-" + conversationId + "-" + trigger?.id,
                        ToolSpecs.getWeather.name, GroupWeatherRules.arguments(request)),
                    s, conversationId, members.first().id,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolOutcome("天气服务暂时无法使用，请稍后再试。", "查天气没成：工具出错")
            }
            note(conversationId, outcome.note.ifBlank { "天气查询没有返回可用结果" })
            GroupWeatherRules.context(outcome, outcome.note.startsWith("查了"), request)
        }
        val names = members.associate { it.id to it.name.trim().ifEmpty { "TA" } }''')

once(chat,
'''            val context = listOfNotNull(worldContext(conversationId, latestText, s), groupLocation)
                .joinToString("\\n\\n").ifBlank { null }''',
'''            val context = listOfNotNull(worldContext(conversationId, latestText, s), groupLocation, groupWeather)
                .joinToString("\\n\\n").ifBlank { null }''')

once(chat,
'''                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 || groupLocation != null || (isQuestion && said == 0),
                    historyRequested = !continued && ChatHistorySearch.wanted(latestText))) said++''',
'''                    targeted = patTarget != null || mentions.isNotEmpty() || conversation.groupMode == 1 ||
                        groupLocation != null || groupWeather != null || (isQuestion && said == 0),
                    historyRequested = !continued && ChatHistorySearch.wanted(latestText))) said++''')

once(chat,
'''            (mentions.isNotEmpty() || isQuestion || groupLocation != null)) {''',
'''            (mentions.isNotEmpty() || isQuestion || groupLocation != null || groupWeather != null)) {''')

for source, target in [
    ("GroupWeatherRules.kt", base/"ai/GroupWeatherRules.kt"),
    ("GroupWeatherRulesTest.kt",root/"app/src/test/java/com/cleo/cleos/ai/GroupWeatherRulesTest.kt"),
]:
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(here/source,target)

gradle = root/"app/build.gradle.kts"
once(gradle,'versionName = "0.37.33"','versionName = "0.37.34"')
once(gradle,'versionCode = 62055','versionCode = 62056')
print("Huaimin 0.37.34 / 62056: group weather, coordinate lookup with user consent, no Room schema change")
