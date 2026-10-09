package com.cleo.cleos.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One explicit fresh user group weather request -> one genuine network fetch. */
internal object GroupWeatherRules {
    data class Request(val city: String?, val usePhoneLocation: Boolean, val days: Int)

    private val topic = Regex("天气|气温|温度|降雨|下雨|带伞|穿什么|预报|湿度|冷不冷|热不热|几度|会下雨", RegexOption.IGNORE_CASE)
    private val asking = Regex("查|看|告诉|想知道|多少|怎么样|如何|请|帮|会不会|要不要|需要|现在|今天|明天|后天|近期|未来|\\?|？|什么", RegexOption.IGNORE_CASE)
    private val refusal = Regex("(?:别|不要|不用|不许|禁止|停止|取消|无需|不想).{0,12}(?:查|看|查询|搜索).{0,8}(?:天气|气温|温度|降雨|预报)|(?:不要|不用).{0,8}(?:天气|预报)", RegexOption.IGNORE_CASE)
    private val metadiscussion = Regex("(?:天气|气温|预报).{0,10}(?:工具|功能|接口|设计)|(?:工具|功能|接口|设计).{0,14}(?:天气|预报)|天气.{0,8}(?:查不了|不可用|不支持)", RegexOption.IGNORE_CASE)
    private val gps = Regex("按.{0,8}(?:定位|手机位置|位置)|定位|当前位置|我的位置|我这里|我这边|我附近|我周围|我所在|按刚才", RegexOption.IGNORE_CASE)

    private val knownCities = listOf(
        "上海", "北京", "天津", "重庆", "广州", "深圳", "杭州", "南京", "苏州", "无锡",
        "合肥", "武汉", "成都", "西安", "长沙", "南昌", "福州", "厦门", "泉州",
        "宁波", "温州", "嘉兴", "绍兴", "金华", "台州", "舟山", "湖州",
        "青岛", "济南", "烟台", "威海", "潍坊", "临沂", "郑州", "洛阳",
        "石家庄", "唐山", "保定", "太原", "大同", "呼和浩特", "包头",
        "沈阳", "大连", "长春", "哈尔滨", "齐齐哈尔", "兰州", "银川", "西宁",
        "乌鲁木齐", "拉萨", "昆明", "贵阳", "南宁", "海口", "三亚",
        "珠海", "佛山", "东莞", "惠州", "中山", "汕头", "江门", "湛江",
        "扬州", "镇江", "常州", "南通", "徐州", "盐城", "连云港", "淮安",
        "泰州", "宿迁", "铜陵", "芜湖", "马鞍山", "安庆", "滁州", "蚌埠",
        "六安", "宣城", "黄山", "池州", "亳州", "淮南", "淮北", "阜阳",
        "香港", "澳门", "台北", "高雄", "台中", "桃园",
    ).sortedByDescending { it.length }

    private fun explicitCity(text: String): String? =
        knownCities.firstOrNull { it in text }
            ?: Regex("([\\p{IsHan}]{2,5})市(?:的|今日|今天|明天|现在|天气|气温|温度|降雨|预报)")
                .find(text)?.groupValues?.getOrNull(1)

    fun parse(userText: String): Request? {
        val text = userText.trim().replace(Regex("\\s+"), "")
        if (text.isEmpty() || refusal.containsMatchIn(text) || metadiscussion.containsMatchIn(text)) return null
        if (!topic.containsMatchIn(text) || !asking.containsMatchIn(text)) return null
        val days = when {
            "一周" in text || "七天" in text || "7天" in text || "未来7天" in text -> 7
            "五天" in text || "5天" in text -> 5
            "后天" in text -> 3
            "明天" in text -> 2
            "现在" in text || "实时" in text || "当前" in text || "此刻" in text -> 1
            else -> 3
        }
        return Request(city = explicitCity(text), usePhoneLocation = gps.containsMatchIn(text), days = days)
    }

    fun arguments(request: Request): String = buildJsonObject {
        request.city?.let { put("city", it) }
        put("days", request.days)
        if (request.usePhoneLocation) put("use_phone_location", true)
    }.toString()

    fun context(outcome: ToolOutcome, success: Boolean, request: Request): String =
        if (success) """
【本轮用户要求查询真实天气，手机已实际调用 get_weather】
${outcome.result.take(3400)}
位置依据：${if (request.usePhoneLocation) "本轮经授权的手机 GPS 坐标（精度取决于手机）" else "用户明确指定的城市或设置中的天气城市"}。
天气结果是一次实时服务查询，不是 AI 自行猜测。请依据结果自然回应温度、天气、降水概率及出门建议；不要把市级预报冒充街道级精确实况，也不要说没有查询工具。群里所有角色参考这一份结果，不重复联网查询。
""".trim() else """
【本轮天气查询未成功】
真实失败原因：${outcome.result.take(700)}
未得到天气服务的实时数据。请如实说明失败原因，不得编造气温、降水、位置；提示用户在「设置 → 能做的事」打开「查天气」，如按定位查询还需打开「查位置」及系统定位权限，或明确给出一个城市后重试。
""".trim()
}
