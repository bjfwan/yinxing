package com.yinxing.launcher.data.weather

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos

/**
 * 内置全国省/市/区县三级地名索引（assets/china_regions.json，约 3200 条），
 * 在本地完成模糊匹配后再拿坐标去查天气，不再依赖 Open-Meteo 的精确地名查询
 * （它错一个字、少个"区"字、或"北京朝阳"这类拼接输入都查不到）。
 *
 * 支持：
 *  - 去后缀匹配（"朝阳区"/"朝阳" 等价）
 *  - 行政区上下文消歧（"北京朝阳"→北京朝阳区，"长春朝阳区"→长春朝阳区）
 *  - 自治州/自治县别名（"湘西"→湘西土家族苗族自治州）
 *  - 常见简称（"哈市"→哈尔滨市）
 *  - 编辑距离 1 的错别字（"杭洲"→杭州）
 *  - 离线逆地理兜底：nearest() 找最近行政区名
 */
internal class ChinaRegionIndex private constructor(
    private val entries: List<Entry>
) {

    internal data class Entry(
        val adcode: Int,
        val name: String,
        val level: Int, // 1=省 2=市 3=区县
        val latitude: Double,
        val longitude: Double,
        val province: String,
        val city: String,
    )

    internal data class Match(val entry: Entry, val score: Double)

    private val byName = HashMap<String, MutableList<Entry>>()
    private val byAlias = HashMap<String, MutableList<Entry>>()

    init {
        for (entry in entries) {
            val normalized = normalize(entry.name)
            if (normalized.length >= 2) {
                byName.getOrPut(normalized) { mutableListOf() } += entry
            }
            for (alias in aliasSetOf(entry.name)) {
                if (alias.length < 2 || alias == normalized) continue
                val bucket = byAlias.getOrPut(alias) { mutableListOf() }
                if (entry !in bucket) bucket += entry
            }
        }
    }

    /**
     * 把用户输入解析成一个行政区条目；置信度不足时返回 null（调用方走网络兜底）。
     */
    internal fun resolve(query: String): Match? {
        val raw = query.trim().replace(Regex("\\s+"), "")
        if (raw.isEmpty()) return null
        val nq = normalize(raw)
        if (nq.isEmpty()) return null

        val pool = LinkedHashMap<Int, Pair<Entry, Double>>() // adcode -> entry,base
        fun offer(entry: Entry, base: Double) {
            val existing = pool[entry.adcode]
            if (existing == null || existing.second < base) pool[entry.adcode] = entry to base
        }
        byName[raw]?.forEach { offer(it, 100.0) }
        byName[nq]?.forEach { offer(it, 100.0) }
        byAlias[raw]?.forEach { offer(it, 85.0) }
        byAlias[nq]?.forEach { offer(it, 85.0) }
        if (nq.length >= 2) {
            for ((key, list) in byName) {
                if (key == nq || key.length < 2) continue
                if (nq.endsWith(key) || nq.startsWith(key) || key.endsWith(nq) || key.startsWith(nq)) {
                    list.forEach { offer(it, 60.0) }
                }
            }
            for ((key, list) in byAlias) {
                if (key == nq || key.length < 2) continue
                if (nq.endsWith(key) || nq.startsWith(key) || key.endsWith(nq) || key.startsWith(nq)) {
                    list.forEach { offer(it, 55.0) }
                }
            }
        }
        if (pool.isEmpty() && nq.length >= 2) {
            for ((key, list) in byName) {
                if (abs(key.length - nq.length) > 1) continue
                if (withinEditDistance(nq, key, 1)) list.forEach { offer(it, 50.0) }
            }
            for ((key, list) in byAlias) {
                if (abs(key.length - nq.length) > 1) continue
                if (withinEditDistance(nq, key, 1)) list.forEach { offer(it, 45.0) }
            }
        }
        if (pool.isEmpty()) return null

        var best: Match? = null
        for ((entry, base) in pool.values) {
            var score = base
            // 上下文判断要基于剥掉匹配名后的残余串，否则"朝阳区"里的"朝阳"会误喂给"朝阳市"
            val residual = raw.replace(entry.name, "").replace(normalize(entry.name), "")
            if (residual.isNotEmpty()) {
                val province = normalize(entry.province)
                val city = normalize(entry.city)
                if (province.isNotEmpty() && residual.contains(province)) score += 12.0
                if (city.isNotEmpty() && city != province && residual.contains(city)) score += 12.0
            }
            if (entry.name.last() in ADMIN_SUFFIX_CHARS && raw.endsWith(entry.name.last().toString())) {
                score += 6.0
            }
            score += when (entry.level) {
                2 -> 4.0
                3 -> 2.0
                else -> 0.0
            }
            if (best == null || score > best.score ||
                (score == best.score && entry.adcode < best.entry.adcode)
            ) {
                best = Match(entry, score)
            }
        }
        return best?.takeIf { it.score >= CONFIDENT_SCORE }
    }

    /** 离线逆地理兜底：返回距坐标最近的地级市（超过阈值视为不可靠返回 null）。 */
    internal fun nearestCity(latitude: Double, longitude: Double): Entry? {
        var best: Entry? = null
        var bestDist = Double.MAX_VALUE
        val cosLat = cos(Math.toRadians(latitude))
        for (entry in entries) {
            if (entry.level > 2) continue
            val dLat = entry.latitude - latitude
            val dLng = (entry.longitude - longitude) * cosLat
            val approx = dLat * dLat + dLng * dLng
            if (approx < bestDist) {
                bestDist = approx
                best = entry
            }
        }
        return best?.takeIf { Math.sqrt(bestDist) * 111.0 <= NEAREST_CITY_MAX_KM }
    }

    private fun withinEditDistance(a: String, b: String, max: Int): Boolean {
        val m = a.length
        val n = b.length
        if (abs(m - n) > max) return false
        var prev = IntArray(n + 1) { it }
        for (i in 1..m) {
            val cur = IntArray(n + 1) { if (it == 0) i else 0 }
            var rowMin = cur[0]
            for (j in 1..n) {
                cur[j] = minOf(
                    prev[j] + 1,
                    cur[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return false
            prev = cur
        }
        return prev[n] <= max
    }

    internal companion object {
        private const val CONFIDENT_SCORE = 50.0
        private const val NEAREST_CITY_MAX_KM = 120.0
        private val ADMIN_SUFFIX_CHARS = charArrayOf('省', '市', '区', '县', '州', '盟', '旗')
        private val ADMIN_SUFFIXES = Regex(
            "(?:特别行政区|维吾尔自治区|壮族自治区|回族自治区|自治区|自治州|自治县|自治旗|地区|盟|省|市|区|县|旗|林区)+$"
        )
        private val ETHNIC_NAMES = listOf(
            "柯尔克孜", "乌孜别克", "塔吉克", "鄂伦春", "俄罗斯", "塔塔尔", "达斡尔",
            "鄂温克", "哈萨克", "维吾尔", "傈僳", "撒拉", "仫佬", "仡佬", "锡伯",
            "阿昌", "普米", "德昂", "保安", "裕固", "独龙", "赫哲", "门巴", "珞巴",
            "基诺", "土家", "拉祜", "景颇", "布朗", "毛南", "东乡", "布依", "蒙古",
            "朝鲜", "哈尼", "纳西", "藏", "回", "苗", "彝", "壮", "满", "侗", "瑶",
            "白", "傣", "黎", "佤", "畲", "水", "羌", "怒", "京"
        )
        private val MANUAL_ALIASES = mapOf(
            "哈市" to "哈尔滨市", "呼市" to "呼和浩特市", "乌市" to "乌鲁木齐市",
            "蓉城" to "成都市", "羊城" to "广州市", "鹏城" to "深圳市",
            "魔都" to "上海市", "帝都" to "北京市", "春城" to "昆明市",
            "冰城" to "哈尔滨市", "泉城" to "济南市", "鹭岛" to "厦门市",
            "椰城" to "海口市", "绿城" to "南宁市", "锡城" to "无锡市",
            "甬城" to "宁波市", "姑苏" to "苏州市", "金陵" to "南京市",
            "庐州" to "合肥市", "汴京" to "开封市", "内蒙" to "内蒙古自治区"
        )

        @Volatile
        private var instance: ChinaRegionIndex? = null

        fun get(context: Context): ChinaRegionIndex {
            instance?.let { return it }
            synchronized(this) {
                instance ?: run {
                    val parsed = context.assets.open("china_regions.json").use { parse(it.readBytes().decodeToString()) }
                    instance = parsed
                }
                return instance!!
            }
        }

        internal fun parse(json: String): ChinaRegionIndex {
            val root = JSONObject(json)
            val array = root.getJSONArray("e")
            val flat = mutableListOf<Entry>()
            val byAdcode = HashMap<Int, Entry>()
            for (i in 0 until array.length()) {
                val e = array.getJSONArray(i)
                val entry = Entry(
                    adcode = e.getInt(0),
                    name = e.getString(1),
                    level = e.getInt(2),
                    latitude = e.getDouble(3),
                    longitude = e.getDouble(4),
                    province = "",
                    city = ""
                )
                flat += entry
                byAdcode[entry.adcode] = entry
            }
            val entries = flat.map { entry ->
                val provCode = (entry.adcode / 10000) * 10000
                val province = when {
                    entry.level == 1 -> entry.name
                    else -> byAdcode[provCode]?.name ?: ""
                }
                val city = when (entry.level) {
                    1, 2 -> entry.name
                    else -> byAdcode[(entry.adcode / 100) * 100]?.name
                        ?: byAdcode[provCode]?.name ?: ""
                }
                entry.copy(province = province, city = city)
            }
            return ChinaRegionIndex(entries)
        }

        internal fun normalize(name: String): String {
            val trimmed = name.trim()
            val stripped = trimmed.replace(ADMIN_SUFFIXES, "")
            return stripped.ifEmpty { trimmed }
        }

        private fun aliasSetOf(name: String): Set<String> {
            val aliases = LinkedHashSet<String>()
            if ("自治" in name) {
                var short = normalize(name)
                for (ethnic in ETHNIC_NAMES) short = short.replace(ethnic, "")
                short = short.replace("族", "")
                if (short.length >= 2) aliases += short
            }
            MANUAL_ALIASES.filterValues { it == name }.keys.forEach { aliases += it }
            return aliases
        }
    }
}
