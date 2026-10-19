package com.example.ui

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Address
import android.location.Geocoder
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoEngine
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MediaGpsInfo(
    val latitude: Double,
    val longitude: Double,
    val placeName: String = "",
    val admin1: String = "",
    val countryCode: String = "",
    val countryName: String = "",
    val geonameId: Int = 0,
    val street: String = "",
    val ward: String = "",
    val district: String = "",
    val detailedAddress: String = "",
    val isDetailed: Boolean = false
) {
    val formattedAddress: String
        get() {
            if (detailedAddress.isNotBlank()) return detailedAddress.trim()
            val rawParts = listOf(street, ward, district, placeName, admin1, countryName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val deduplicated = GeoNamesReverseGeocoder.deduplicateAddressParts(rawParts)
            return if (deduplicated.isNotEmpty()) {
                deduplicated.joinToString(", ")
            } else {
                "Đang tải địa chỉ từ bản đồ trực tuyến..."
            }
        }
}

/**
 * Hệ thống Reverse Geocoding trực tuyến toàn cầu (100% Dynamic Live Map Geocoding - KHÔNG hardcode bất kỳ địa chỉ nào):
 * Tự động truy vấn thời gian thực từ các dịch vụ bản đồ toàn cầu luôn được cập nhật khi có đường/phố mới:
 * 1. OpenStreetMap Nominatim Live API (zoom=18, addressdetails=1) - Dữ liệu cộng đồng OSM cập nhật từng phút toàn cầu.
 * 2. Android Platform Geocoder (Google Play Services / System Geocoder) - Dữ liệu trực tiếp từ Google Maps.
 * 3. ArcGIS World Geocoding Service (Esri / HERE / TomTom / OSM global live street data).
 * 4. Photon Komoot & BigDataCloud Global Reverse Geocoding API.
 */
object GeoNamesReverseGeocoder {

    private val detailedCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, MediaGpsInfo>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaGpsInfo>?): Boolean {
                return size > 512
            }
        }
    )

    private data class ParsedAddress(
        val street: String = "",
        val ward: String = "",
        val district: String = "",
        val city: String = "",
        val country: String = "",
        val countryCode: String = "",
        val fullLine: String = ""
    )

    /**
     * Loại bỏ các phần tử trùng lặp ngữ nghĩa (ví dụ "Thanh Xuân" và "Quận Thanh Xuân") nhưng giữ đầy đủ
     * thứ tự phân cấp: Số nhà / Đường / Ngõ -> Phường / Xã -> Quận / Huyện -> Tỉnh / Thành phố -> Quốc gia.
     */
    fun deduplicateAddressParts(parts: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (raw in parts) {
            val subSegments = raw.split(",")
                .map { cleanAddressComponent(it) }
                .filter { it.isNotEmpty() }

            for (part in subSegments) {
                val normPart = normalizePlaceToken(part)
                if (normPart.isEmpty()) continue

                val existingIdx = result.indexOfFirst { existing ->
                    val normExisting = normalizePlaceToken(existing)
                    normExisting.equals(normPart, ignoreCase = true)
                }

                if (existingIdx == -1) {
                    result.add(part)
                } else {
                    // Nếu phần mới có tiền tố hành chính rõ hơn (ví dụ "Quận Thanh Xuân" so với "Thanh Xuân"), ưu tiên giữ bản đầy đủ
                    if (part.length > result[existingIdx].length) {
                        result[existingIdx] = part
                    }
                }
            }
        }
        return result
    }

    private fun normalizePlaceToken(value: String): String {
        return value.trim()
            .replace(
                Regex(
                    "^(Đường|Đ\\.|Phố|P\\.|Ngõ|Ngách|Hẻm|Kiệt|Phường|Xã|Thị trấn|TT\\.|Quận|Q\\.|Huyện|H\\.|Thị xã|TX\\.|Thành phố|TP\\.|Tỉnh)\\s+",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
    }

    private fun cleanAddressComponent(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        // Bỏ Plus Code (ví dụ: "XRV6+abc"), mã bưu chính 5-6 chữ số (ví dụ: "100000"), hoặc chuỗi tọa độ số thuần túy
        if (trimmed.matches(Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,6}.*", RegexOption.IGNORE_CASE))) {
            return ""
        }
        if (trimmed.matches(Regex("^\\d{5,6}(-\\d{4})?$"))) {
            return ""
        }
        if (trimmed.matches(Regex("^[+-]?\\d+(\\.\\d+)?\\s*,\\s*[+-]?\\d+(\\.\\d+)?$"))) {
            return ""
        }
        return trimmed
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            val cleaned = cleanAddressComponent(v)
            if (cleaned.isNotEmpty()) return cleaned
        }
        return ""
    }

    /**
     * Phân giải địa chỉ trực tuyến thời gian thực trên toàn thế giới (không dùng bất kỳ dữ liệu hardcode nào).
     */
    fun reverseGeocodeDetailed(
        context: Context?,
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false
    ): MediaGpsInfo {
        val cacheKey = String.format(Locale.US, "%.5f,%.5f", lat, lon)
        if (!forceRefresh) {
            detailedCache[cacheKey]?.let { cached ->
                if (cached.isDetailed && cached.formattedAddress.isNotBlank()) return cached
            }
        }

        // 1. Truy vấn Android Platform Geocoder (Google Play Services / System Geocoder)
        val androidParsed = if (context != null) queryAndroidGeocoder(context, lat, lon) else null

        // 2. Truy vấn OpenStreetMap Nominatim Live API ở mức chi tiết cao nhất (zoom=18)
        val osmParsed = queryOsmNominatim(lat, lon)

        // 3. Truy vấn ArcGIS World Geocoding Service (nếu thiếu tên đường hoặc thiếu cấp quận/phường)
        val needMoreDetail = (firstNonBlank(osmParsed?.street, androidParsed?.street).isEmpty()) ||
                (firstNonBlank(osmParsed?.district, androidParsed?.district).isEmpty())
        val arcgisParsed = if (needMoreDetail) queryArcGisReverseGeocoder(lat, lon) else null

        // 4. Dự phòng Photon Komoot & BigDataCloud nếu các dịch vụ trên bị giới hạn mạng tạm thời
        val photonParsed = if (
            osmParsed == null && androidParsed == null && arcgisParsed == null
        ) {
            queryPhotonKomoot(lat, lon) ?: queryBigDataCloud(lat, lon)
        } else null

        val bestStreet = firstNonBlank(
            osmParsed?.street,
            androidParsed?.street,
            arcgisParsed?.street,
            photonParsed?.street
        )
        val bestWard = firstNonBlank(
            osmParsed?.ward,
            androidParsed?.ward,
            arcgisParsed?.ward,
            photonParsed?.ward
        )
        val bestDistrict = firstNonBlank(
            osmParsed?.district,
            androidParsed?.district,
            arcgisParsed?.district,
            photonParsed?.district
        )
        val bestCity = firstNonBlank(
            osmParsed?.city,
            androidParsed?.city,
            arcgisParsed?.city,
            photonParsed?.city
        )
        val bestCountry = firstNonBlank(
            osmParsed?.country,
            androidParsed?.country,
            arcgisParsed?.country,
            photonParsed?.country
        )
        val bestCountryCode = firstNonBlank(
            osmParsed?.countryCode,
            androidParsed?.countryCode,
            arcgisParsed?.countryCode,
            photonParsed?.countryCode
        )

        // Kết hợp các thành phần phân cấp + dòng địa chỉ đầy đủ để không bỏ sót bất kỳ cấp đường/ngõ/phường/quận nào
        val hierarchyParts = mutableListOf<String>()
        if (bestStreet.isNotEmpty()) hierarchyParts.add(bestStreet)
        if (bestWard.isNotEmpty()) hierarchyParts.add(bestWard)
        if (bestDistrict.isNotEmpty()) hierarchyParts.add(bestDistrict)

        // Nếu fullLine từ Android Geocoder hoặc OSM có thêm thông tin (ví dụ tên tòa nhà/ngõ hoặc quận khi trường rời bị khuyết),
        // trộn vào đúng vị trí trước cấp Tỉnh/Thành phố.
        val extraSegments = listOfNotNull(androidParsed?.fullLine, osmParsed?.fullLine, arcgisParsed?.fullLine)
            .flatMap { it.split(",") }
            .map { cleanAddressComponent(it) }
            .filter { it.isNotEmpty() }

        for (seg in extraSegments) {
            val normSeg = normalizePlaceToken(seg)
            if (normSeg.isNotEmpty() &&
                !normSeg.equals(normalizePlaceToken(bestCity), ignoreCase = true) &&
                !normSeg.equals(normalizePlaceToken(bestCountry), ignoreCase = true)
            ) {
                hierarchyParts.add(seg)
            }
        }

        if (bestCity.isNotEmpty()) hierarchyParts.add(bestCity)
        if (bestCountry.isNotEmpty()) hierarchyParts.add(bestCountry)

        val finalParts = deduplicateAddressParts(hierarchyParts)
        if (finalParts.isNotEmpty()) {
            val fullAddress = finalParts.joinToString(", ")
            val resolved = MediaGpsInfo(
                latitude = lat,
                longitude = lon,
                placeName = bestStreet.ifEmpty { bestWard.ifEmpty { bestDistrict } },
                admin1 = bestCity,
                countryCode = bestCountryCode,
                countryName = bestCountry,
                street = bestStreet,
                ward = bestWard,
                district = bestDistrict,
                detailedAddress = fullAddress,
                isDetailed = true
            )
            detailedCache[cacheKey] = resolved
            return resolved
        }

        // Trường hợp thiết bị hoàn toàn mất kết nối mạng tại thời điểm truy vấn:
        // chỉ lưu tọa độ với isDetailed = false để tự động truy vấn lại ngay khi có mạng.
        return MediaGpsInfo(
            latitude = lat,
            longitude = lon,
            isDetailed = false
        )
    }

    /**
     * Truy vấn OpenStreetMap Nominatim Live API (zoom=18) lấy đầy đủ số nhà, ngõ, đường/phố, phường/xã, quận/huyện, thành phố.
     */
    private fun queryOsmNominatim(lat: Double, lon: Double): ParsedAddress? {
        var conn: HttpURLConnection? = null
        return try {
            val lang = Locale.getDefault().language.ifEmpty { "vi" }
            val urlStr = String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=%s,vi,en",
                lat,
                lon,
                lang
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Accept-Language", "$lang,vi;q=0.9,en;q=0.8")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val addr = json.optJSONObject("address") ?: return null

            val houseNumber = cleanAddressComponent(addr.optString("house_number", ""))
            val road = firstNonBlank(
                addr.optString("road"),
                addr.optString("street"),
                addr.optString("pedestrian"),
                addr.optString("residential"),
                addr.optString("footway"),
                addr.optString("path"),
                addr.optString("cycleway"),
                addr.optString("highway")
            )
            val poiName = firstNonBlank(
                json.optString("name"),
                addr.optString("amenity"),
                addr.optString("building"),
                addr.optString("shop"),
                addr.optString("tourism"),
                addr.optString("office"),
                addr.optString("leisure"),
                addr.optString("historic")
            )

            val streetCombined = when {
                houseNumber.isNotEmpty() && road.isNotEmpty() -> "$houseNumber $road"
                road.isNotEmpty() && poiName.isNotEmpty() &&
                        !normalizePlaceToken(poiName).equals(normalizePlaceToken(road), ignoreCase = true) -> "$poiName, $road"
                road.isNotEmpty() -> road
                else -> poiName
            }

            // Thu thập tất cả các cấp phường/khu dân cư và quận/huyện theo thứ tự phân cấp của OSM
            // (tránh lỗi suburb đè mất quarter hoặc ngược lại)
            val subLocalLevels = listOf(
                addr.optString("neighbourhood"),
                addr.optString("allotments"),
                addr.optString("quarter"),
                addr.optString("hamlet"),
                addr.optString("village"),
                addr.optString("croft")
            ).map { cleanAddressComponent(it) }.filter { it.isNotEmpty() }

            val districtLevels = listOf(
                addr.optString("suburb"),
                addr.optString("city_district"),
                addr.optString("borough"),
                addr.optString("district"),
                addr.optString("town"),
                addr.optString("municipality"),
                addr.optString("county")
            ).map { cleanAddressComponent(it) }.filter { it.isNotEmpty() }

            val ward = deduplicateAddressParts(subLocalLevels).joinToString(", ")
            val district = deduplicateAddressParts(districtLevels).joinToString(", ")

            val city = firstNonBlank(
                addr.optString("city"),
                addr.optString("state"),
                addr.optString("province"),
                addr.optString("region")
            )
            val country = cleanAddressComponent(addr.optString("country", ""))
            val countryCode = addr.optString("country_code", "").uppercase(Locale.ROOT)
            val displayName = json.optString("display_name", "")

            if (streetCombined.isEmpty() && ward.isEmpty() && district.isEmpty() && city.isEmpty()) {
                null
            } else {
                ParsedAddress(
                    street = streetCombined,
                    ward = ward,
                    district = district,
                    city = city,
                    country = country,
                    countryCode = countryCode,
                    fullLine = displayName
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /**
     * Truy vấn Android Platform Geocoder (kết nối trực tiếp dịch vụ Google Maps trên thiết bị).
     * Hỗ trợ cả API bất đồng bộ mới trên Android 13+ (GeocodeListener) và getFromLocation truyền thống.
     */
    @Suppress("DEPRECATION")
    private fun queryAndroidGeocoder(context: Context, lat: Double, lon: Double): ParsedAddress? {
        return try {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            val addresses: List<Address> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                var resultList: List<Address> = emptyList()
                val latch = CountDownLatch(1)
                geocoder.getFromLocation(lat, lon, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addrs: MutableList<Address>) {
                        resultList = addrs
                        latch.countDown()
                    }

                    override fun onError(errorMessage: String?) {
                        latch.countDown()
                    }
                })
                latch.await(3500L, TimeUnit.MILLISECONDS)
                resultList.ifEmpty {
                    try { geocoder.getFromLocation(lat, lon, 5).orEmpty() } catch (_: Exception) { emptyList() }
                }
            } else {
                geocoder.getFromLocation(lat, lon, 5).orEmpty()
            }

            if (addresses.isEmpty()) return null

            var street = ""
            var ward = ""
            var district = ""
            var city = ""
            var country = ""
            var countryCode = ""
            var bestFullLine = ""

            for (addr in addresses) {
                val subThoroughfare = cleanAddressComponent(addr.subThoroughfare)
                val thoroughfare = cleanAddressComponent(addr.thoroughfare)
                val feature = cleanAddressComponent(addr.featureName)

                val candidateStreet = when {
                    subThoroughfare.isNotEmpty() && thoroughfare.isNotEmpty() -> "$subThoroughfare $thoroughfare"
                    thoroughfare.isNotEmpty() -> thoroughfare
                    feature.isNotEmpty() && !feature.all { it.isDigit() } -> feature
                    else -> ""
                }
                if (street.isEmpty() && candidateStreet.isNotEmpty()) {
                    street = candidateStreet
                }
                if (ward.isEmpty()) {
                    ward = cleanAddressComponent(addr.subLocality)
                }
                if (district.isEmpty()) {
                    district = firstNonBlank(
                        addr.subAdminArea,
                        if (addr.adminArea != null && addr.locality != null && !addr.locality.equals(addr.adminArea, ignoreCase = true)) {
                            addr.locality
                        } else null
                    )
                }
                if (city.isEmpty()) {
                    city = firstNonBlank(addr.adminArea, addr.locality)
                }
                if (country.isEmpty()) {
                    country = cleanAddressComponent(addr.countryName)
                }
                if (countryCode.isEmpty() && !addr.countryCode.isNullOrBlank()) {
                    countryCode = addr.countryCode.uppercase(Locale.ROOT)
                }

                if (addr.maxAddressLineIndex >= 0) {
                    val line = (0..addr.maxAddressLineIndex)
                        .mapNotNull { idx -> addr.getAddressLine(idx)?.takeIf { it.isNotBlank() } }
                        .joinToString(", ")
                    if (line.length > bestFullLine.length) {
                        bestFullLine = line
                    }
                }
            }

            if (street.isEmpty() && ward.isEmpty() && district.isEmpty() && bestFullLine.isEmpty()) {
                null
            } else {
                ParsedAddress(
                    street = street,
                    ward = ward,
                    district = district,
                    city = city,
                    country = country,
                    countryCode = countryCode,
                    fullLine = bestFullLine
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Truy vấn ArcGIS World Geocoding Service trực tuyến (dữ liệu đường phố toàn cầu của Esri/HERE/TomTom/OSM).
     */
    private fun queryArcGisReverseGeocoder(lat: Double, lon: Double): ParsedAddress? {
        var conn: HttpURLConnection? = null
        return try {
            val lang = Locale.getDefault().language.ifEmpty { "vi" }
            val urlStr = String.format(
                Locale.US,
                "https://geocode.arcgis.com/arcgis/rest/services/World/GeocodeServer/reverseGeocode?f=json&location=%.6f,%.6f&langCode=%s",
                lon,
                lat,
                lang
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val addr = root.optJSONObject("address") ?: return null

            val street = firstNonBlank(addr.optString("Address"), addr.optString("ShortLabel"))
            val ward = cleanAddressComponent(addr.optString("Neighborhood"))
            val district = firstNonBlank(addr.optString("District"), addr.optString("Subregion"))
            val city = firstNonBlank(addr.optString("City"), addr.optString("Region"), addr.optString("MetroArea"))
            val country = cleanAddressComponent(addr.optString("CntryName"))
            val countryCode = addr.optString("CountryCode", "").uppercase(Locale.ROOT)
            val longLabel = addr.optString("LongLabel", "")

            if (street.isEmpty() && ward.isEmpty() && district.isEmpty() && city.isEmpty()) {
                null
            } else {
                ParsedAddress(
                    street = street,
                    ward = ward,
                    district = district,
                    city = city,
                    country = country,
                    countryCode = countryCode,
                    fullLine = longLabel
                )
            }
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun queryPhotonKomoot(lat: Double, lon: Double): ParsedAddress? {
        var conn: HttpURLConnection? = null
        return try {
            val urlStr = String.format(
                Locale.US,
                "https://photon.komoot.io/reverse?lon=%.6f&lat=%.6f",
                lon,
                lat
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val props = features.optJSONObject(0)?.optJSONObject("properties") ?: return null

            val houseNumber = cleanAddressComponent(props.optString("housenumber"))
            val streetName = firstNonBlank(props.optString("street"), props.optString("name"))
            val street = if (houseNumber.isNotEmpty() && streetName.isNotEmpty()) "$houseNumber $streetName" else streetName
            val ward = cleanAddressComponent(props.optString("locality"))
            val district = firstNonBlank(props.optString("district"), props.optString("county"))
            val city = firstNonBlank(props.optString("city"), props.optString("state"))
            val country = cleanAddressComponent(props.optString("country"))
            val countryCode = props.optString("countrycode", "").uppercase(Locale.ROOT)

            if (street.isEmpty() && district.isEmpty() && city.isEmpty()) null
            else ParsedAddress(
                street = street,
                ward = ward,
                district = district,
                city = city,
                country = country,
                countryCode = countryCode
            )
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun queryBigDataCloud(lat: Double, lon: Double): ParsedAddress? {
        var conn: HttpURLConnection? = null
        return try {
            val lang = Locale.getDefault().language.ifEmpty { "vi" }
            val urlStr = String.format(
                Locale.US,
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.6f&longitude=%.6f&localityLanguage=%s",
                lat,
                lon,
                lang
            )
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)

            val locality = cleanAddressComponent(root.optString("locality"))
            val city = firstNonBlank(root.optString("city"), root.optString("principalSubdivision"))
            val country = cleanAddressComponent(root.optString("countryName"))
            val countryCode = root.optString("countryCode", "").uppercase(Locale.ROOT)

            val adminArr = root.optJSONObject("localityInfo")?.optJSONArray("administrative")
            val adminNames = mutableListOf<String>()
            if (adminArr != null) {
                for (i in (adminArr.length() - 1) downTo 0) {
                    val item = adminArr.optJSONObject(i) ?: continue
                    val name = cleanAddressComponent(item.optString("name"))
                    if (name.isNotEmpty()) adminNames.add(name)
                }
            }

            if (locality.isEmpty() && city.isEmpty() && adminNames.isEmpty()) null
            else ParsedAddress(
                ward = locality,
                district = adminNames.firstOrNull().orEmpty(),
                city = city,
                country = country,
                countryCode = countryCode,
                fullLine = adminNames.joinToString(", ")
            )
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}

data class VaultItem(
    val originalName: String,
    val encryptedName: String,
    val uri: Uri,
    val isVideo: Boolean,
    val isLivePhoto: Boolean = false,
    val size: Long,
    val dateTaken: Long
)

/**
 * Yêu cầu hiển thị UI cho phép người dùng tự chọn frame bất kỳ của video
 * để làm ảnh bìa (tạo cả Tier SCREEN 1080p và Tier THUMB 216p) khi import.
 */
data class VideoFramePickRequest(
    val uri: Uri,
    val fileName: String,
    val queueIndex: Int,
    val queueTotal: Int
)

private data class PendingImportItem(
    val index: Int,
    val uri: Uri,
    val originalName: String,
    val isVideo: Boolean,
    val companionVideoUri: Uri? = null,
    val companionVideoSize: Long = 0L
)

private class PrecomputedTiers(
    val thumbBytes: ByteArray,
    val screenBytes: ByteArray,
    val dimensions: Pair<Int, Int>
)

class VaultViewModel : ViewModel() {

    companion object {
        const val THUMB_SIZE_PX = 216
    }

    // Danh sách các thư mục Vault đã ghi nhớ ở trang chủ
    private val _savedVaults = MutableStateFlow<List<Uri>>(emptyList())
    val savedVaults: StateFlow<List<Uri>> = _savedVaults.asStateFlow()

    // Vault đang được người dùng click chọn để nhập password / mở khóa
    private val _vaultUri = MutableStateFlow<Uri?>(null)
    val vaultUri: StateFlow<Uri?> = _vaultUri.asStateFlow()

    private val _dek = MutableStateFlow<ByteArray?>(null)
    val dek: StateFlow<ByteArray?> = _dek.asStateFlow()

    private val _items = MutableStateFlow<List<VaultItem>>(emptyList())
    val items: StateFlow<List<VaultItem>> = _items.asStateFlow()

    // Bảng lưu Note của từng ảnh/video (được giải mã từ file "media notes.txt")
    private val _mediaNotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val mediaNotes: StateFlow<Map<String, String>> = _mediaNotes.asStateFlow()

    // Bảng lưu tọa độ GPS + địa danh GeoNames của từng ảnh/video (được giải mã từ file "media gps.txt")
    private val _mediaGps = MutableStateFlow<Map<String, MediaGpsInfo>>(emptyMap())
    val mediaGps: StateFlow<Map<String, MediaGpsInfo>> = _mediaGps.asStateFlow()

    private val scannedNoGpsKeys = Collections.synchronizedSet(mutableSetOf<String>())
    private val notesLock = Any()
    private val gpsLock = Any()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _isProcessCompleted = MutableStateFlow(false)
    val isProcessCompleted: StateFlow<Boolean> = _isProcessCompleted.asStateFlow()

    /**
     * Đảm bảo mọi vòng tiến trình đều chạy đầy 100%, sau đó hiển thị animation vẽ dấu tick
     * hoàn tất rồi mới ẩn tiến trình đi.
     */
    private suspend fun playCompletionTickAnimation() {
        _importProgress.value = 1f
        kotlinx.coroutines.delay(240L)
        _isProcessCompleted.value = true
        kotlinx.coroutines.delay(480L)
    }

    private val _importQueueTotal = MutableStateFlow(0)
    val importQueueTotal: StateFlow<Int> = _importQueueTotal.asStateFlow()

    private val _importQueueIndex = MutableStateFlow(0)
    val importQueueIndex: StateFlow<Int> = _importQueueIndex.asStateFlow()

    // Bitmap preview kích thước thật (216x216 px) và cờ báo loại file (video / Live Photo) đang được mã hoá
    private val _currentImportThumb = MutableStateFlow<Bitmap?>(null)
    val currentImportThumb: StateFlow<Bitmap?> = _currentImportThumb.asStateFlow()

    private val _currentImportIsVideo = MutableStateFlow(false)
    val currentImportIsVideo: StateFlow<Boolean> = _currentImportIsVideo.asStateFlow()

    private val _currentImportIsLivePhoto = MutableStateFlow(false)
    val currentImportIsLivePhoto: StateFlow<Boolean> = _currentImportIsLivePhoto.asStateFlow()

    // Trạng thái đang huỷ import & dọn dẹp các file đã lỡ tạo
    private val _isCancellingImport = MutableStateFlow(false)
    val isCancellingImport: StateFlow<Boolean> = _isCancellingImport.asStateFlow()

    // Trạng thái đang export file gốc ra thư mục Vault
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _lastExportedFileUri = MutableStateFlow<Uri?>(null)
    val lastExportedFileUri: StateFlow<Uri?> = _lastExportedFileUri.asStateFlow()

    private var importJob: Job? = null
    private var gpsBackfillJob: Job? = null
    private val isImportCancelled = AtomicBoolean(false)

    private val _videoFramePickRequest = MutableStateFlow<VideoFramePickRequest?>(null)
    val videoFramePickRequest: StateFlow<VideoFramePickRequest?> = _videoFramePickRequest.asStateFlow()
    private var videoFrameDeferred: CompletableDeferred<Bitmap?>? = null

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedMedia = MutableStateFlow<VaultItem?>(null)
    val selectedMedia: StateFlow<VaultItem?> = _selectedMedia.asStateFlow()

    private val _clickedItemBounds = MutableStateFlow<FloatArray?>(null)
    val clickedItemBounds: StateFlow<FloatArray?> = _clickedItemBounds.asStateFlow()

    private val _thumbnailBounds = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val thumbnailBounds: StateFlow<Map<String, FloatArray>> = _thumbnailBounds.asStateFlow()

    private val _animatingItem = MutableStateFlow<String?>(null)
    val animatingItem: StateFlow<String?> = _animatingItem.asStateFlow()

    fun updateThumbnailBounds(encryptedName: String, bounds: FloatArray) {
        val current = _thumbnailBounds.value[encryptedName]
        if (current != null &&
            current.size == bounds.size &&
            kotlin.math.abs(current[0] - bounds[0]) < 0.5f &&
            kotlin.math.abs(current[1] - bounds[1]) < 0.5f &&
            kotlin.math.abs(current[2] - bounds[2]) < 0.5f &&
            kotlin.math.abs(current[3] - bounds[3]) < 0.5f
        ) {
            return
        }
        _thumbnailBounds.value = _thumbnailBounds.value + (encryptedName to bounds)
    }

    fun setAnimatingItem(encryptedName: String?) {
        _animatingItem.value = encryptedName
    }

    /**
     * Được gọi từ VideoFramePickerDialog khi người dùng đã chọn xong frame ưng ý.
     */
    fun confirmVideoFrameSelection(chosenBitmap: Bitmap?) {
        videoFrameDeferred?.complete(chosenBitmap)
        videoFrameDeferred = null
    }

    /**
     * Huỷ toàn bộ quá trình import bất cứ lúc nào (cả khi đang chọn thumb video lẫn khi đang mã hoá)
     * và xoá sạch toàn bộ các file đã lỡ mã hoá trong đợt import hiện tại.
     */
    fun cancelImport() {
        if (!_isLoading.value && !_isExporting.value && _videoFramePickRequest.value == null) return
        isImportCancelled.set(true)
        _isCancellingImport.value = true
        _videoFramePickRequest.value = null
        videoFrameDeferred?.cancel(CancellationException("Import cancelled by user"))
        videoFrameDeferred = null
        importJob?.cancel(CancellationException("Import cancelled by user"))
    }

    /**
     * Tải danh sách các Vault đã ghi nhớ từ SharedPreferences khi mở app.
     * Chỉ migrate khóa cũ "saved_vault_uri" duy nhất 1 lần khi "saved_vault_uris_json" chưa tồn tại,
     * sau đó xoá hẳn "saved_vault_uri" để tránh lỗi Vault đã xoá lại tự động xuất hiện lại.
     */
    fun loadSavedVaultUri(context: Context) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val persistedSet = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

        val loadedUris = mutableListOf<Uri>()
        val hasNewJsonKey = prefs.contains("saved_vault_uris_json")
        val jsonStr = prefs.getString("saved_vault_uris_json", null)

        if (hasNewJsonKey) {
            if (!jsonStr.isNullOrEmpty()) {
                try {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val uStr = arr.optString(i)
                        if (uStr.isNotEmpty() && uStr in persistedSet) {
                            val parsed = Uri.parse(uStr)
                            if (loadedUris.none { it.toString() == parsed.toString() }) {
                                loadedUris.add(parsed)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            // Chỉ migrate từ key cũ "saved_vault_uri" nếu app chưa từng khởi tạo "saved_vault_uris_json"
            val legacyUriStr = prefs.getString("saved_vault_uri", null)
            if (!legacyUriStr.isNullOrEmpty() && legacyUriStr in persistedSet) {
                try {
                    val legacyUri = Uri.parse(legacyUriStr)
                    loadedUris.add(legacyUri)
                } catch (_: Exception) {}
            }
        }

        _savedVaults.value = loadedUris
        saveVaultListToPrefs(context, loadedUris)
    }

    private fun saveVaultListToPrefs(context: Context, list: List<Uri>) {
        val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        list.forEach { arr.put(it.toString()) }
        prefs.edit()
            .putString("saved_vault_uris_json", arr.toString())
            .remove("saved_vault_uri")
            .commit()
    }

    fun setVaultUri(context: Context, uri: Uri?) {
        gpsBackfillJob?.cancel()
        com.example.ui.screens.ThumbBitmapCache.clear()
        com.example.ui.screens.ScreenBitmapCache.clear()
        com.example.ui.screens.FullBitmapCache.clear()
        com.example.ui.screens.MediaPropertiesCache.clear()
        _dek.value?.fill(0)
        _dek.value = null
        _items.value = emptyList()
        _mediaNotes.value = emptyMap()
        _mediaGps.value = emptyMap()
        scannedNoGpsKeys.clear()
        _error.value = null

        if (uri == null) {
            _vaultUri.value = null
            return
        }

        val targetStr = uri.toString()
        if (_savedVaults.value.none { it.toString() == targetStr }) {
            val currentList = _savedVaults.value.toMutableList()
            currentList.add(uri)
            _savedVaults.value = currentList
            saveVaultListToPrefs(context, currentList)
        }

        _vaultUri.value = uri
    }

    fun moveSavedVault(context: Context, fromIndex: Int, toIndex: Int) {
        val currentList = _savedVaults.value.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices || fromIndex == toIndex) return
        val moved = currentList.removeAt(fromIndex)
        currentList.add(toIndex, moved)
        _savedVaults.value = currentList
        saveVaultListToPrefs(context, currentList)
    }

    fun removeSavedVault(context: Context, uri: Uri) {
        val targetStr = uri.toString()
        val updated = _savedVaults.value.filterNot { it == uri || it.toString() == targetStr }
        _savedVaults.value = updated
        saveVaultListToPrefs(context, updated)

        CryptoEngine.clearBiometricDek(context, uri)

        // Thu hồi luôn quyền Persisted URI Permission của thư mục vừa xoá khỏi danh sách ghi nhớ
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) {}

        if (_vaultUri.value == uri || _vaultUri.value?.toString() == targetStr) {
            _vaultUri.value = null
            _error.value = null
        }
    }

    fun setError(message: String?) {
        _error.value = message
    }

    fun clearError() { _error.value = null }

    fun selectMedia(item: VaultItem?, bounds: FloatArray? = null) {
        _clickedItemBounds.value = bounds; _selectedMedia.value = item
    }

    /**
     * Lưu hoặc xoá Note cho 1 ảnh/video và ghi mã hoá vào "media notes.txt".
     */
    fun saveMediaNote(
        context: Context,
        encryptedName: String,
        noteText: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return
        val cleaned = noteText.trim()

        val updatedMap = _mediaNotes.value.toMutableMap()
        if (cleaned.isEmpty()) {
            updatedMap.remove(encryptedName)
        } else {
            updatedMap[encryptedName] = cleaned
        }
        _mediaNotes.value = updatedMap

        viewModelScope.launch(Dispatchers.IO) {
            val ok = persistMediaNotesLocked(context, vaultUriVal, key, _mediaNotes.value)
            if (onComplete != null) {
                withContext(Dispatchers.Main) { onComplete(ok) }
            }
        }
    }

    private fun loadMediaNotesFromDisk(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray
    ): Map<String, String> {
        val rawText = CryptoEngine.readEncryptedTextFile(
            context = context,
            directoryUri = vaultUriVal,
            fileName = CryptoEngine.NOTES_FILE_NAME,
            dek = key
        ) ?: return emptyMap()

        val map = mutableMapOf<String, String>()
        try {
            val root = JSONObject(rawText)
            val notesObj = root.optJSONObject("notes") ?: root
            val keys = notesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = notesObj.opt(k)
                val noteStr = when (v) {
                    is JSONObject -> v.optString("note", "")
                    is String -> v
                    else -> ""
                }.trim()
                if (noteStr.isNotEmpty()) {
                    map[k] = noteStr
                }
            }
        } catch (_: Exception) {
            rawText.lineSequence().forEach { line ->
                val idx = line.indexOf('\t')
                if (idx > 0) {
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).replace("\\n", "\n").trim()
                    if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
                }
            }
        }
        return map
    }

    private fun persistMediaNotesLocked(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray,
        notesMap: Map<String, String>
    ): Boolean {
        synchronized(notesLock) {
            val notesJson = JSONObject()
            for ((encName, noteVal) in notesMap) {
                if (noteVal.isNotBlank()) {
                    notesJson.put(encName, noteVal)
                }
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("updatedAt", System.currentTimeMillis())
                put("notes", notesJson)
            }
            return CryptoEngine.writeEncryptedTextFile(
                context = context,
                directoryUri = vaultUriVal,
                fileName = CryptoEngine.NOTES_FILE_NAME,
                plainText = root.toString(),
                dek = key
            )
        }
    }

    private fun loadMediaGpsFromDisk(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray
    ): Pair<Map<String, MediaGpsInfo>, Set<String>> {
        val rawText = CryptoEngine.readEncryptedTextFile(
            context = context,
            directoryUri = vaultUriVal,
            fileName = CryptoEngine.GPS_FILE_NAME,
            dek = key
        ) ?: return emptyMap<String, MediaGpsInfo>() to emptySet()

        val gpsMap = mutableMapOf<String, MediaGpsInfo>()
        val noGpsSet = mutableSetOf<String>()
        try {
            val root = JSONObject(rawText)
            val schemaVersion = root.optInt("version", 1)
            val gpsObj = root.optJSONObject("gps") ?: root
            val keys = gpsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (k == "version" || k == "updatedAt" || k == "noGps") continue
                val entryObj = gpsObj.optJSONObject(k) ?: continue
                val lat = entryObj.optDouble("lat", Double.NaN)
                val lon = entryObj.optDouble("lon", Double.NaN)
                if (!lat.isNaN() && !lon.isNaN() && isValidLatLon(lat, lon)) {
                    val savedStreet = entryObj.optString("street", "")
                    val savedWard = entryObj.optString("ward", "")
                    val savedDistrict = entryObj.optString("district", "")
                    val savedDetailedAddr = entryObj.optString("detailedAddress", "")
                    val savedIsDetailed = entryObj.optBoolean("isDetailed", false)
                    val savedPlace = entryObj.optString("placeName", "")
                    val savedAdmin1 = entryObj.optString("admin1", "")
                    val savedCountryCode = entryObj.optString("countryCode", "")
                    val savedCountryName = entryObj.optString("countryName", "")

                    // Nếu dữ liệu thuộc schema cũ (< 5) hoặc chưa có địa chỉ trực tuyến chi tiết,
                    // đặt isDetailed = false để tự động truy vấn trực tuyến lại từ bản đồ thời gian thực.
                    val info = if (schemaVersion >= 5 && savedIsDetailed && savedDetailedAddr.isNotEmpty()) {
                        MediaGpsInfo(
                            latitude = lat,
                            longitude = lon,
                            placeName = savedPlace,
                            admin1 = savedAdmin1,
                            countryCode = savedCountryCode,
                            countryName = savedCountryName,
                            street = savedStreet,
                            ward = savedWard,
                            district = savedDistrict,
                            detailedAddress = savedDetailedAddr,
                            isDetailed = true
                        )
                    } else {
                        MediaGpsInfo(
                            latitude = lat,
                            longitude = lon,
                            isDetailed = false
                        )
                    }
                    gpsMap[k] = info
                }
            }
            val noGpsArr = if (schemaVersion >= 3) root.optJSONArray("noGps") else null
            if (noGpsArr != null) {
                for (i in 0 until noGpsArr.length()) {
                    val name = noGpsArr.optString(i)
                    if (name.isNotEmpty()) noGpsSet.add(name)
                }
            }
        } catch (_: Exception) {}
        return gpsMap to noGpsSet
    }

    private fun persistMediaGpsLocked(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray,
        gpsMap: Map<String, MediaGpsInfo>,
        noGpsSet: Set<String>
    ): Boolean {
        synchronized(gpsLock) {
            val gpsJson = JSONObject()
            for ((encName, info) in gpsMap) {
                val obj = JSONObject().apply {
                    put("lat", info.latitude)
                    put("lon", info.longitude)
                    put("placeName", info.placeName)
                    put("admin1", info.admin1)
                    put("countryCode", info.countryCode)
                    put("countryName", info.countryName)
                    put("street", info.street)
                    put("ward", info.ward)
                    put("district", info.district)
                    put("detailedAddress", info.detailedAddress)
                    put("isDetailed", info.isDetailed)
                }
                gpsJson.put(encName, obj)
            }
            val noGpsArr = JSONArray()
            noGpsSet.forEach { noGpsArr.put(it) }

            val root = JSONObject().apply {
                put("version", 5)
                put("updatedAt", System.currentTimeMillis())
                put("gps", gpsJson)
                put("noGps", noGpsArr)
            }
            return CryptoEngine.writeEncryptedTextFile(
                context = context,
                directoryUri = vaultUriVal,
                fileName = CryptoEngine.GPS_FILE_NAME,
                plainText = root.toString(),
                dek = key
            )
        }
    }

    /**
     * Đảm bảo file hiện tại đã được trích xuất tọa độ GPS và truy vấn địa chỉ trực tuyến thời gian thực.
     */
    fun ensureMediaGpsLoaded(context: Context, item: VaultItem) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return
        val existing = _mediaGps.value[item.encryptedName]

        viewModelScope.launch(Dispatchers.IO) {
            if (existing != null) {
                // Luôn kiểm tra trực tuyến từ bản đồ sống (forceRefresh = true) khi mở hộp thoại Details
                // để nếu bản đồ có đường mới xây hoặc thay đổi địa giới hành chính thì cập nhật kịp thời ngay lập tức.
                val refreshed = GeoNamesReverseGeocoder.reverseGeocodeDetailed(
                    context = context,
                    lat = existing.latitude,
                    lon = existing.longitude,
                    forceRefresh = true
                )
                if (refreshed.isDetailed && refreshed != existing) {
                    val updated = _mediaGps.value.toMutableMap()
                    updated[item.encryptedName] = refreshed
                    _mediaGps.value = updated
                    persistMediaGpsLocked(context, vaultUriVal, key, updated, scannedNoGpsKeys.toSet())
                }
                return@launch
            }

            val extracted = extractGpsFromEncryptedVaultItem(context, item, key)
            if (extracted != null) {
                scannedNoGpsKeys.remove(item.encryptedName)
                val updated = _mediaGps.value.toMutableMap()
                updated[item.encryptedName] = extracted
                _mediaGps.value = updated
                persistMediaGpsLocked(context, vaultUriVal, key, updated, scannedNoGpsKeys.toSet())
            } else if (item.encryptedName !in scannedNoGpsKeys) {
                scannedNoGpsKeys.add(item.encryptedName)
                persistMediaGpsLocked(context, vaultUriVal, key, _mediaGps.value, scannedNoGpsKeys.toSet())
            }
        }
    }

    private fun startBackgroundGpsBackfill(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray,
        itemsList: List<VaultItem>
    ) {
        gpsBackfillJob?.cancel()
        val itemsToProcess = itemsList.filter { item ->
            val currentInfo = _mediaGps.value[item.encryptedName]
            (currentInfo == null && item.encryptedName !in scannedNoGpsKeys) ||
                    (currentInfo != null && !currentInfo.isDetailed)
        }
        if (itemsToProcess.isEmpty()) return

        gpsBackfillJob = viewModelScope.launch(Dispatchers.IO) {
            var changed = false
            val currentMap = _mediaGps.value.toMutableMap()
            for (item in itemsToProcess) {
                if (_dek.value == null) return@launch
                val existing = currentMap[item.encryptedName]
                if (existing != null) {
                    if (!existing.isDetailed) {
                        val upgraded = GeoNamesReverseGeocoder.reverseGeocodeDetailed(
                            context = context,
                            lat = existing.latitude,
                            lon = existing.longitude
                        )
                        if (upgraded.isDetailed) {
                            currentMap[item.encryptedName] = upgraded
                            _mediaGps.value = currentMap.toMap()
                            changed = true
                        }
                    }
                    continue
                }
                if (item.encryptedName in scannedNoGpsKeys) continue
                val info = extractGpsFromEncryptedVaultItem(context, item, key)
                if (info != null) {
                    currentMap[item.encryptedName] = info
                    _mediaGps.value = currentMap.toMap()
                    changed = true
                } else {
                    scannedNoGpsKeys.add(item.encryptedName)
                    changed = true
                }
            }
            if (changed && _dek.value != null) {
                persistMediaGpsLocked(context, vaultUriVal, key, _mediaGps.value, scannedNoGpsKeys.toSet())
            }
        }
    }

    /**
     * Mở khoá Vault và quét sẵn toàn bộ danh sách file + giải mã "media notes.txt" & "media gps.txt"
     * TRƯỚC KHI gán _dek.value.
     */
    fun unlockVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                val unlockedData = withContext(Dispatchers.IO) {
                    val key = CryptoEngine.unlockVault(context, uri, password)
                    CryptoEngine.saveBiometricDek(context, uri, key)
                    val scanned = scanFiles(context, uri, key)
                    val notes = loadMediaNotesFromDisk(context, uri, key)
                    val (gps, noGps) = loadMediaGpsFromDisk(context, uri, key)
                    Triple(key, scanned, Triple(notes, gps, noGps))
                }
                val unlockedDek = unlockedData.first
                val initialItems = unlockedData.second
                val (notesMap, gpsMap, noGpsSet) = unlockedData.third

                scannedNoGpsKeys.clear()
                scannedNoGpsKeys.addAll(noGpsSet)
                _mediaNotes.value = notesMap
                _mediaGps.value = gpsMap
                _items.value = initialItems
                _dek.value = unlockedDek

                startBackgroundGpsBackfill(context, uri, unlockedDek, initialItems)
            } catch (e: Exception) {
                _error.value = "Incorrect password."
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    /**
     * Mở khoá Vault bằng vân tay của thiết bị (sau khi BiometricPrompt xác thực thành công).
     */
    fun unlockVaultWithBiometric(context: Context) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                val unlockedData = withContext(Dispatchers.IO) {
                    val key = CryptoEngine.unlockVaultWithBiometric(context, uri)
                    val scanned = scanFiles(context, uri, key)
                    val notes = loadMediaNotesFromDisk(context, uri, key)
                    val (gps, noGps) = loadMediaGpsFromDisk(context, uri, key)
                    Triple(key, scanned, Triple(notes, gps, noGps))
                }
                val unlockedDek = unlockedData.first
                val initialItems = unlockedData.second
                val (notesMap, gpsMap, noGpsSet) = unlockedData.third

                scannedNoGpsKeys.clear()
                scannedNoGpsKeys.addAll(noGpsSet)
                _mediaNotes.value = notesMap
                _mediaGps.value = gpsMap
                _items.value = initialItems
                _dek.value = unlockedDek

                startBackgroundGpsBackfill(context, uri, unlockedDek, initialItems)
            } catch (e: IllegalStateException) {
                _error.value = e.message ?: "Please unlock with your Master Password once to enable fingerprint unlock."
            } catch (e: Exception) {
                _error.value = "Fingerprint unlock failed. Please enter your Master Password."
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    fun createVault(context: Context, password: String) {
        val uri = _vaultUri.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                val (newDek, initialItems) = withContext(Dispatchers.IO) {
                    val key = CryptoEngine.initVault(context, uri, password)
                    CryptoEngine.saveBiometricDek(context, uri, key)
                    val scanned = scanFiles(context, uri, key)
                    key to scanned
                }

                playCompletionTickAnimation()

                scannedNoGpsKeys.clear()
                _mediaNotes.value = emptyMap()
                _mediaGps.value = emptyMap()
                _items.value = initialItems
                _dek.value = newDek
            } catch (e: Exception) {
                _error.value = "Failed to create Vault"
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    fun lockVault() {
        cancelImport()
        gpsBackfillJob?.cancel()
        com.example.ui.screens.ThumbBitmapCache.clear()
        com.example.ui.screens.ScreenBitmapCache.clear()
        com.example.ui.screens.FullBitmapCache.clear()
        com.example.ui.screens.MediaPropertiesCache.clear()
        _dek.value?.fill(0)
        _dek.value = null
        _vaultUri.value = null
        _items.value = emptyList()
        _mediaNotes.value = emptyMap()
        _mediaGps.value = emptyMap()
        scannedNoGpsKeys.clear()
        _selectedMedia.value = null
        _error.value = null
    }

    fun loadVaultItems(context: Context) {
        val uri = _vaultUri.value ?: return
        val key = _dek.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                val (scanned, notesMap, gpsPair) = withContext(Dispatchers.IO) {
                    val s = scanFiles(context, uri, key)
                    val n = loadMediaNotesFromDisk(context, uri, key)
                    val g = loadMediaGpsFromDisk(context, uri, key)
                    Triple(s, n, g)
                }
                scannedNoGpsKeys.clear()
                scannedNoGpsKeys.addAll(gpsPair.second)
                _mediaNotes.value = notesMap
                _mediaGps.value = gpsPair.first
                _items.value = scanned
                playCompletionTickAnimation()
                startBackgroundGpsBackfill(context, uri, key, scanned)
            } catch (e: Exception) {
                _error.value = "Failed to load: ${e.message}"
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    private fun scanFiles(context: Context, rootUri: Uri, key: ByteArray): List<VaultItem> {
        val result = mutableListOf<VaultItem>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri))
        val resolver = context.contentResolver
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE)

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val displayName = cursor.getString(1)
                val size = cursor.getLong(2)

                if (displayName.endsWith(".enc") &&
                    displayName != "vault.conf" &&
                    displayName != CryptoEngine.NOTES_FILE_NAME &&
                    displayName != CryptoEngine.GPS_FILE_NAME
                ) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(rootUri, docId)
                    val decryptedMeta = CryptoEngine.decryptFileName(displayName, key) ?: continue

                    val parts = decryptedMeta.split('|', limit = 3)
                    val dateTaken: Long
                    val isLiveTagged: Boolean
                    val decryptedName: String

                    if (parts.size == 3 && parts[1] == "LIVE") {
                        dateTaken = parts[0].toLongOrNull() ?: 0L
                        isLiveTagged = true
                        decryptedName = parts[2]
                    } else if (parts.size >= 2) {
                        dateTaken = parts[0].toLongOrNull() ?: 0L
                        isLiveTagged = false
                        decryptedName = decryptedMeta.substringAfter('|')
                    } else {
                        dateTaken = 0L
                        isLiveTagged = false
                        decryptedName = decryptedMeta
                    }

                    val isVideo = isVideoFile(decryptedName)
                    val isLivePhoto = isLiveTagged || (!isVideo && (CryptoEngine.readSgv2FileInfo(context, fileUri, key)?.isLivePhoto == true))

                    result.add(
                        VaultItem(
                            originalName = decryptedName,
                            encryptedName = displayName,
                            uri = fileUri,
                            isVideo = isVideo,
                            isLivePhoto = isLivePhoto,
                            size = size,
                            dateTaken = dateTaken
                        )
                    )
                }
            }
        }
        return result.sortedByDescending { it.dateTaken }
    }

    /** Import 1 file duy nhất - giữ lại để tương thích ngược, chỉ là wrapper gọi importMultipleMedia. */
    fun importMedia(context: Context, sourceUri: Uri) {
        importMultipleMedia(context, listOf(sourceUri))
    }

    /**
     * Bulk Import bằng cách chọn 1 Folder:
     * Quét đệ quy toàn bộ các file ảnh & video hợp lệ bên trong thư mục được chọn,
     * sau đó chuyển thẳng vào hàng đợi importMultipleMedia.
     */
    fun importFolderMedia(context: Context, folderTreeUri: Uri) {
        if (_vaultUri.value == null || _dek.value == null) return

        importJob?.cancel()
        isImportCancelled.set(false)
        _isCancellingImport.value = false

        importJob = viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _error.value = null

            val discoveredUris = try {
                withContext(Dispatchers.IO) {
                    collectMediaUrisFromTree(context, folderTreeUri)
                }
            } catch (e: CancellationException) {
                _isLoading.value = false
                _isCancellingImport.value = false
                return@launch
            } catch (e: Exception) {
                _isLoading.value = false
                _isCancellingImport.value = false
                _error.value = "Failed to scan folder: ${e.message}"
                return@launch
            }

            if (isImportCancelled.get()) {
                _isLoading.value = false
                _isCancellingImport.value = false
                return@launch
            }

            if (discoveredUris.isEmpty()) {
                _isLoading.value = false
                _isCancellingImport.value = false
                _error.value = "No photos or videos found in the selected folder."
                return@launch
            }

            runImportPipeline(context, discoveredUris)
        }
    }

    private fun collectMediaUrisFromTree(context: Context, treeUri: Uri): List<Uri> {
        val result = mutableListOf<Uri>()
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

        fun traverseDirectory(parentDocId: String) {
            if (isImportCancelled.get()) throw CancellationException("Import cancelled")
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")
                    val docId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: ""
                    val mimeType = cursor.getString(2) ?: ""

                    if (displayName.startsWith(".") ||
                        displayName == "vault.conf" ||
                        displayName == CryptoEngine.NOTES_FILE_NAME ||
                        displayName == CryptoEngine.GPS_FILE_NAME ||
                        displayName.endsWith(".enc")
                    ) {
                        continue
                    }

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        traverseDirectory(docId)
                    } else if (isSupportedMediaFile(displayName, mimeType)) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        result.add(fileUri)
                    }
                }
            }
        }

        traverseDirectory(rootDocId)
        return result
    }

    private fun isSupportedMediaFile(fileName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif", "gif", "bmp", "dng")
        return ext in imageExts || isVideoFile(fileName)
    }

    /**
     * Import nhiều file cùng lúc.
     */
    fun importMultipleMedia(context: Context, sourceUris: List<Uri>) {
        if (_vaultUri.value == null || _dek.value == null || sourceUris.isEmpty()) return

        importJob?.cancel()
        isImportCancelled.set(false)
        _isCancellingImport.value = false

        importJob = viewModelScope.launch {
            runImportPipeline(context, sourceUris)
        }
    }

    /**
     * Ghép cặp tự động các Live Photo dạng 2 file rời (ví dụ iOS/macOS: IMG_1234.HEIC/JPG + IMG_1234.MOV/MP4)
     * nếu được chọn cùng nhau và file video có thời lượng ngắn (<= 6 giây).
     */
    private fun buildPendingImportItems(context: Context, sourceUris: List<Uri>): List<PendingImportItem> {
        data class RawEntry(val uri: Uri, val name: String, val isVideo: Boolean, val baseKey: String)

        val rawList = sourceUris.mapIndexed { idx, uri ->
            if (isImportCancelled.get()) throw CancellationException("Import cancelled")
            val name = getFileNameFromUri(context, uri) ?: "import_${System.currentTimeMillis()}_$idx"
            val isVid = isVideoFile(name)
            val baseKey = name.substringBeforeLast('.').lowercase(Locale.ROOT)
            RawEntry(uri, name, isVid, baseKey)
        }

        val imageBaseKeys = rawList.filter { !it.isVideo }.map { it.baseKey }.toSet()
        val pairedVideoByBase = mutableMapOf<String, Pair<Uri, Long>>()

        rawList.filter { it.isVideo && it.baseKey in imageBaseKeys }.forEach { vidEntry ->
            val ext = vidEntry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (ext == "mov" || ext == "mp4") {
                val vidSize = getFileSizeFromUri(context, vidEntry.uri)
                val durationMs = getVideoDurationMs(context, vidEntry.uri)
                if (vidSize > 0L && durationMs in 100L..6500L) {
                    pairedVideoByBase[vidEntry.baseKey] = vidEntry.uri to vidSize
                }
            }
        }

        val consumedVideoUris = pairedVideoByBase.values.map { it.first }.toSet()
        val finalItems = mutableListOf<PendingImportItem>()

        rawList.forEach { entry ->
            if (entry.uri in consumedVideoUris) return@forEach
            val companion = if (!entry.isVideo) pairedVideoByBase[entry.baseKey] else null
            finalItems.add(
                PendingImportItem(
                    index = finalItems.size,
                    uri = entry.uri,
                    originalName = entry.name,
                    isVideo = entry.isVideo,
                    companionVideoUri = companion?.first,
                    companionVideoSize = companion?.second ?: 0L
                )
            )
        }

        return finalItems
    }

    private fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Pipeline import 3 giai đoạn có hỗ trợ Live Photo, trích xuất GPS vào "media gps.txt", Cancel & Rollback.
     */
    private suspend fun runImportPipeline(context: Context, sourceUris: List<Uri>) {
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return
        if (sourceUris.isEmpty()) return

        _isLoading.value = true
        _importProgress.value = 0f
        _isProcessCompleted.value = false
        _importQueueTotal.value = sourceUris.size
        _importQueueIndex.value = 0
        _currentImportThumb.value = null
        _currentImportIsVideo.value = false
        _currentImportIsLivePhoto.value = false
        _error.value = null

        val failedNames = mutableListOf<String>()
        val createdVaultUris = Collections.synchronizedList(mutableListOf<Uri>())
        val importedGpsBatch = Collections.synchronizedMap(mutableMapOf<String, MediaGpsInfo>())
        val importedNoGpsBatch = Collections.synchronizedSet(mutableSetOf<String>())

        try {
            val pendingItems = withContext(Dispatchers.IO) {
                buildPendingImportItems(context, sourceUris)
            }
            val totalFiles = pendingItems.size.coerceAtLeast(1)
            _importQueueTotal.value = totalFiles

            // GIAI ĐOẠN 1: Chọn thumbnail cho TOÀN BỘ video ngay bước đầu
            val videoItems = pendingItems.filter { it.isVideo }
            val precomputedVideoTiers = mutableMapOf<Int, PrecomputedTiers>()

            if (videoItems.isNotEmpty()) {
                videoItems.forEachIndexed { videoIdx, videoItem ->
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")

                    val deferred = CompletableDeferred<Bitmap?>()
                    videoFrameDeferred = deferred
                    _videoFramePickRequest.value = VideoFramePickRequest(
                        uri = videoItem.uri,
                        fileName = videoItem.originalName,
                        queueIndex = videoIdx + 1,
                        queueTotal = videoItems.size
                    )

                    val chosenBitmap = deferred.await()
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")

                    val tiers = withContext(Dispatchers.IO) {
                        val (thumbBytes, screenBytes, dimensions) = generateTierBitmaps(
                            context = context,
                            uri = videoItem.uri,
                            isVideo = true,
                            chosenVideoFrame = chosenBitmap,
                            recycleInputBitmap = false
                        )
                        PrecomputedTiers(thumbBytes, screenBytes, dimensions)
                    }
                    precomputedVideoTiers[videoItem.index] = tiers
                }
                _videoFramePickRequest.value = null
            }

            // GIAI ĐOẠN 2: Mã hoá liên tục toàn bộ danh sách file & trích xuất GPS
            withContext(Dispatchers.IO) {
                pendingItems.forEachIndexed { index, item ->
                    if (isImportCancelled.get()) throw CancellationException("Import cancelled")
                    _importQueueIndex.value = index + 1
                    try {
                        importSingleFile(
                            context = context,
                            vaultUriVal = vaultUriVal,
                            key = key,
                            item = item,
                            precomputedTiers = precomputedVideoTiers[item.index],
                            createdVaultUris = createdVaultUris,
                            importedGpsBatch = importedGpsBatch,
                            importedNoGpsBatch = importedNoGpsBatch
                        ) { fileProgress ->
                            if (isImportCancelled.get()) {
                                throw CancellationException("Import cancelled")
                            }
                            _importProgress.value = (index + fileProgress) / totalFiles
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedNames.add(item.originalName)
                        _importProgress.value = (index + 1f) / totalFiles
                    }
                }
            }

            if (isImportCancelled.get()) throw CancellationException("Import cancelled")

            // Lưu toàn bộ tọa độ GPS vừa trích xuất vào file mã hoá "media gps.txt"
            if (importedGpsBatch.isNotEmpty() || importedNoGpsBatch.isNotEmpty()) {
                val mergedGps = _mediaGps.value.toMutableMap().apply { putAll(importedGpsBatch) }
                scannedNoGpsKeys.addAll(importedNoGpsBatch)
                _mediaGps.value = mergedGps
                withContext(Dispatchers.IO) {
                    persistMediaGpsLocked(context, vaultUriVal, key, mergedGps, scannedNoGpsKeys.toSet())
                }
            }

            _items.value = withContext(Dispatchers.IO) { scanFiles(context, vaultUriVal, key) }

            if (failedNames.isNotEmpty()) {
                _error.value = if (failedNames.size == 1) {
                    "Failed to import: ${failedNames.first()}"
                } else {
                    "Failed to import ${failedNames.size} of $totalFiles files"
                }
            }

            // GIAI ĐOẠN 3: Rung 3 cái dài báo hiệu hoàn tất + Chờ tiến trình đạt 100% và vẽ dấu tick
            vibrateThreeTimes(context)
            playCompletionTickAnimation()
        } catch (e: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                val snapshot = synchronized(createdVaultUris) { createdVaultUris.toList() }
                snapshot.forEach { uriToDelete ->
                    CryptoEngine.deleteFile(context, uriToDelete)
                }
                _items.value = scanFiles(context, vaultUriVal, key)
            }
        } catch (e: Exception) {
            if (isImportCancelled.get()) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val snapshot = synchronized(createdVaultUris) { createdVaultUris.toList() }
                    snapshot.forEach { uriToDelete ->
                        CryptoEngine.deleteFile(context, uriToDelete)
                    }
                    _items.value = scanFiles(context, vaultUriVal, key)
                }
            } else {
                _error.value = "Import failed: ${e.message}"
            }
        } finally {
            withContext(NonCancellable) {
                _videoFramePickRequest.value = null
                videoFrameDeferred = null
                _currentImportThumb.value = null
                _currentImportIsVideo.value = false
                _currentImportIsLivePhoto.value = false
                _isCancellingImport.value = false
                isImportCancelled.set(false)
                _isProcessCompleted.value = false
                _isLoading.value = false
                _importProgress.value = 0f
                _importQueueTotal.value = 0
                _importQueueIndex.value = 0
            }
        }
    }

    private fun importSingleFile(
        context: Context,
        vaultUriVal: Uri,
        key: ByteArray,
        item: PendingImportItem,
        precomputedTiers: PrecomputedTiers? = null,
        createdVaultUris: MutableList<Uri>,
        importedGpsBatch: MutableMap<String, MediaGpsInfo>,
        importedNoGpsBatch: MutableSet<String>,
        onProgress: (Float) -> Unit
    ) {
        if (isImportCancelled.get()) throw CancellationException("Import cancelled")

        // Dùng Uri gốc (không bị redact GPS) cho TOÀN BỘ pipeline: trích GPS, đọc size, và quan trọng nhất là
        // mã hoá vào Vault -> bản mã hoá cũng giữ nguyên EXIF/GPS gốc thay vì bản đã bị hệ điều hành xoá.
        val sourceUri = getUnredactedUri(context, item.uri)
        val companionVideoUri = item.companionVideoUri?.let { getUnredactedUri(context, it) }
        val originalName = item.originalName
        val isVideo = item.isVideo
        val sourceSize = getFileSizeFromUri(context, sourceUri)

        val embeddedMotion = if (!isVideo && companionVideoUri == null && sourceSize > 0L) {
            detectEmbeddedMotionVideo(context, sourceUri, sourceSize)
        } else null

        val isLivePhoto = !isVideo && (companionVideoUri != null || embeddedMotion != null)
        val liveVideoOffset = embeddedMotion?.first ?: 0L
        val liveVideoSize = embeddedMotion?.second ?: 0L

        val (thumbBytes, screenBytes, dimensions) = if (precomputedTiers != null) {
            Triple(precomputedTiers.thumbBytes, precomputedTiers.screenBytes, precomputedTiers.dimensions)
        } else {
            generateTierBitmaps(
                context = context,
                uri = sourceUri,
                isVideo = isVideo,
                chosenVideoFrame = null
            )
        }

        val rawThumbBmp = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
        if (rawThumbBmp != null) {
            _currentImportIsVideo.value = isVideo
            _currentImportIsLivePhoto.value = isLivePhoto
            val cropped = cropCenterSquareBitmap(rawThumbBmp, THUMB_SIZE_PX)
            _currentImportThumb.value = cropped
        }

        if (isImportCancelled.get()) throw CancellationException("Import cancelled")

        val dateTaken = getActualDateTaken(context, sourceUri, isVideo, originalName)
        val combinedMetadata = if (isLivePhoto) {
            "$dateTaken|LIVE|$originalName"
        } else {
            "$dateTaken|$originalName"
        }
        val encryptedName = CryptoEngine.encryptFileName(combinedMetadata, key)

        // Trích xuất GPS từ file nguồn (và companion video nếu là Live Photo)
        val extractedGps = extractGpsFromSourceUri(
            context = context,
            uri = sourceUri,
            isVideo = isVideo,
            companionVideoUri = companionVideoUri
        )

        val encryptedFileUri = CryptoEngine.createChildFile(context, vaultUriVal, encryptedName)
            ?: throw java.io.IOException("Could not create SGV2 file in Vault")

        createdVaultUris.add(encryptedFileUri)

        try {
            CryptoEngine.encryptSgv2File(
                context = context,
                sourceUri = sourceUri,
                destUri = encryptedFileUri,
                thumbBytes = thumbBytes,
                screenBytes = screenBytes,
                dek = key,
                sourceSize = if (isLivePhoto && liveVideoOffset > 0L) liveVideoOffset else sourceSize,
                mediaWidth = dimensions.first,
                mediaHeight = dimensions.second,
                liveVideoOffset = liveVideoOffset,
                liveVideoSize = liveVideoSize,
                companionVideoUri = companionVideoUri,
                companionVideoSize = item.companionVideoSize,
                onProgress = { progress -> onProgress(progress) }
            )
            if (extractedGps != null) {
                importedGpsBatch[encryptedName] = extractedGps
            } else {
                importedNoGpsBatch.add(encryptedName)
            }
        } catch (e: Exception) {
            if (!isImportCancelled.get()) {
                CryptoEngine.deleteFile(context, encryptedFileUri)
                createdVaultUris.remove(encryptedFileUri)
            }
            throw e
        }
    }

    private fun isValidLatLon(lat: Double, lon: Double): Boolean {
        if (lat.isNaN() || lon.isNaN()) return false
        if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return false
        if (kotlin.math.abs(lat) < 0.0001 && kotlin.math.abs(lon) < 0.0001) return false
        return true
    }

    /**
     * Lấy Uri gốc không bị Android 10+ xoá (redact) tọa độ GPS trong EXIF / ISO-6709.
     *
     * Import dùng OpenMultipleDocuments nên Uri nhận được thường là Uri của DocumentsProvider
     * (content://com.android.providers.media.documents/document/image:1234), KHÔNG phải Uri MediaStore.
     * MediaStore.setRequireOriginal() chỉ chấp nhận Uri MediaStore (content://media/...) và ném
     * IllegalArgumentException với Uri khác -> trước đây exception bị nuốt, code đọc nhầm bản đã bị xoá GPS.
     *
     * Hàm này: đổi Uri documents -> Uri MediaStore, bật requireOriginal, rồi MỞ THỬ để chắc chắn đọc được.
     * Nếu bất kỳ bước nào thất bại (thiếu READ_MEDIA_* / ACCESS_MEDIA_LOCATION, Android 14 "Selected photos"...)
     * thì trả về đúng Uri ban đầu để luồng cũ vẫn chạy như trước.
     */
    private fun getUnredactedUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        if (uri.getQueryParameter("requireOriginal") != null) return uri
        return try {
            val mediaUri: Uri = when (uri.authority) {
                MediaStore.AUTHORITY -> uri
                "com.android.providers.media.documents" -> {
                    // documentId có dạng "image:1234" hoặc "video:1234"
                    val docId = DocumentsContract.getDocumentId(uri)
                    val kind = docId.substringBefore(':')
                    val id = docId.substringAfter(':', "").toLongOrNull() ?: return uri
                    val collection = when (kind) {
                        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        else -> return uri
                    }
                    ContentUris.withAppendedId(collection, id)
                }
                else -> return uri
            }
            val original = MediaStore.setRequireOriginal(mediaUri)
            // Kiểm tra thực sự mở được (cần READ_MEDIA_IMAGES/VIDEO), nếu không thì bỏ cuộc về Uri cũ
            val canOpen = context.contentResolver.openFileDescriptor(original, "r")?.use { true } ?: false
            if (canOpen) original else uri
        } catch (_: Exception) {
            uri
        }
    }

    private fun extractGpsFromSourceUri(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        companionVideoUri: Uri? = null
    ): MediaGpsInfo? {
        val targetUri = getUnredactedUri(context, uri)
        if (!isVideo) {
            // 1. Ưu tiên đọc qua FileDescriptor (hỗ trợ seek đầy đủ cho HEIC, JPEG, WebP, DNG)
            var coords: Pair<Double, Double>? = try {
                context.contentResolver.openFileDescriptor(targetUri, "r")?.use { pfd ->
                    extractGpsFromExifInstance(ExifInterface(pfd.fileDescriptor))
                }
            } catch (_: Exception) { null }

            // 2. Fallback đọc qua InputStream (nếu Uri gốc bị từ chối quyền setRequireOriginal)
            if (coords == null) {
                coords = try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        extractGpsFromExifStream(inputStream)
                    }
                } catch (_: Exception) { null }
            }

            // 3. Fallback quét trực tiếp cấu trúc Binary TIFF/EXIF GPS IFD (0x8825) + XMP + ISO-6709 trên mảng byte
            if (coords == null) {
                coords = extractGpsFromRawUriBytes(context, targetUri, isVideo = false)
                    ?: extractGpsFromRawUriBytes(context, uri, isVideo = false)
            }

            if (coords != null && isValidLatLon(coords.first, coords.second)) {
                return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, coords.first, coords.second)
            }

            if (companionVideoUri != null) {
                val compCoords = extractGpsFromVideoUri(context, companionVideoUri)
                if (compCoords != null && isValidLatLon(compCoords.first, compCoords.second)) {
                    return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, compCoords.first, compCoords.second)
                }
            }
            return null
        } else {
            val vidCoords = extractGpsFromVideoUri(context, targetUri)
                ?: extractGpsFromVideoUri(context, uri)
            if (vidCoords != null && isValidLatLon(vidCoords.first, vidCoords.second)) {
                return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, vidCoords.first, vidCoords.second)
            }
            return null
        }
    }

    private fun extractGpsFromExifInstance(exif: ExifInterface): Pair<Double, Double>? {
        try {
            // Ưu tiên tính trực tiếp từ phân số Rational DMS bằng Double 64-bit để giữ nguyên độ chính xác tuyệt đối như ExifTool
            val latAttr = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) ?: "N"
            val lonAttr = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) ?: "E"
            if (!latAttr.isNullOrEmpty() && !lonAttr.isNullOrEmpty()) {
                val lat = convertRationalDmsToDecimal(latAttr, latRef)
                val lon = convertRationalDmsToDecimal(lonAttr, lonRef)
                if (lat != null && lon != null && isValidLatLon(lat, lon)) {
                    return lat to lon
                }
            }
            val latLong = FloatArray(2)
            @Suppress("DEPRECATION")
            if (exif.getLatLong(latLong)) {
                val lat = latLong[0].toDouble()
                val lon = latLong[1].toDouble()
                if (isValidLatLon(lat, lon)) return lat to lon
            }
        } catch (_: Exception) {}
        return null
    }

    private fun extractGpsFromExifStream(inputStream: InputStream): Pair<Double, Double>? {
        return try {
            val buffered = if (inputStream.markSupported()) inputStream else java.io.BufferedInputStream(inputStream, 262144)
            extractGpsFromExifInstance(ExifInterface(buffered))
        } catch (_: Exception) {
            null
        }
    }

    private fun convertRationalDmsToDecimal(dms: String, ref: String): Double? {
        return try {
            val cleaned = dms.trim()
            val directVal = cleaned.toDoubleOrNull()
            if (directVal != null) {
                return if (ref.startsWith("S", ignoreCase = true) || ref.startsWith("W", ignoreCase = true)) {
                    -kotlin.math.abs(directVal)
                } else directVal
            }
            val parts = cleaned.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            fun parsePart(part: String): Double {
                val sub = part.trim().split('/')
                return if (sub.size == 2) {
                    val num = sub[0].trim().toDouble()
                    val den = sub[1].trim().toDouble()
                    if (den == 0.0) 0.0 else num / den
                } else {
                    part.trim().toDouble()
                }
            }
            val deg = parsePart(parts[0])
            val min = if (parts.size >= 2) parsePart(parts[1]) else 0.0
            val sec = if (parts.size >= 3) parsePart(parts[2]) else 0.0
            var result = kotlin.math.abs(deg) + (min / 60.0) + (sec / 3600.0)
            if (deg < 0 || ref.startsWith("S", ignoreCase = true) || ref.startsWith("W", ignoreCase = true)) {
                result = -result
            }
            result
        } catch (_: Exception) {
            null
        }
    }

    private fun extractGpsFromVideoUri(context: Context, uri: Uri): Pair<Double, Double>? {
        val retriever = MediaMetadataRetriever()
        try {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                }
            } catch (_: Exception) {
                retriever.setDataSource(context, uri)
            }
            val locStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            if (!locStr.isNullOrEmpty()) {
                val parsed = parseIso6709Location(locStr)
                if (parsed != null && isValidLatLon(parsed.first, parsed.second)) {
                    return parsed
                }
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // Fallback: Quét trực tiếp 1 MB đầu và 2 MB cuối của file video để bắt atom ©xyz / loci / XMP
        return extractGpsFromRawUriBytes(context, uri, isVideo = true)
    }

    private fun extractGpsFromRawUriBytes(context: Context, uri: Uri, isVideo: Boolean): Pair<Double, Double>? {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { fis ->
                    val channel = fis.channel
                    val totalSize = channel.size()
                    if (totalSize <= 0L) return null

                    val headLen = minOf(1024 * 1024L, totalSize).toInt()
                    val headBuf = ByteBuffer.allocate(headLen)
                    channel.position(0L)
                    while (headBuf.hasRemaining()) {
                        if (channel.read(headBuf) <= 0) break
                    }
                    val headBytes = headBuf.array()
                    extractGpsFromBinaryBytes(headBytes, isVideo)?.let { return it }

                    if (totalSize > headLen) {
                        val tailLen = minOf(2 * 1024 * 1024L, totalSize).toInt()
                        val tailStart = (totalSize - tailLen).coerceAtLeast(0L)
                        val tailBuf = ByteBuffer.allocate(tailLen)
                        channel.position(tailStart)
                        while (tailBuf.hasRemaining()) {
                            if (channel.read(tailBuf) <= 0) break
                        }
                        extractGpsFromBinaryBytes(tailBuf.array(), isVideo)?.let { return it }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun parseIso6709Location(location: String): Pair<Double, Double>? {
        val regex = Regex("""([+-]\d{1,3}(?:\.\d+)?)([+-]\d{1,4}(?:\.\d+)?)""")
        val match = regex.find(location.trim()) ?: return null
        val rawLat = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val rawLon = match.groupValues.getOrNull(2)?.toDoubleOrNull() ?: return null

        val lat = if (kotlin.math.abs(rawLat) > 90.0) {
            val sign = if (rawLat < 0) -1.0 else 1.0
            val absVal = kotlin.math.abs(rawLat)
            val deg = (absVal / 100.0).toInt()
            val min = absVal - (deg * 100.0)
            sign * (deg + (min / 60.0))
        } else rawLat

        val lon = if (kotlin.math.abs(rawLon) > 180.0) {
            val sign = if (rawLon < 0) -1.0 else 1.0
            val absVal = kotlin.math.abs(rawLon)
            val deg = (absVal / 100.0).toInt()
            val min = absVal - (deg * 100.0)
            sign * (deg + (min / 60.0))
        } else rawLon

        return if (isValidLatLon(lat, lon)) lat to lon else null
    }

    /**
     * Phân tích trực tiếp mảng byte nhị phân của ảnh/video để tìm tọa độ GPS từ:
     * 1. Cấu trúc nhị phân TIFF / EXIF GPS IFD (Tag 0x8825) trong JPEG, HEIC, WebP, PNG, DNG
     * 2. Gói siêu dữ liệu XMP (exif:GPSLatitude / exif:GPSLongitude)
     * 3. Chuỗi ISO-6709 trong MP4/MOV (©xyz, location, com.apple.quicktime.location.ISO6709)
     * 4. Box nhị phân 3GPP 'loci' (16.16 fixed-point longitude/latitude)
     */
    private fun extractGpsFromBinaryBytes(bytes: ByteArray, isVideo: Boolean): Pair<Double, Double>? {
        if (bytes.size < 16) return null

        if (!isVideo) {
            extractGpsFromExifStream( java.io.ByteArrayInputStream(bytes) )?.let { return it }
            parseBinaryTiffGpsIfd(bytes)?.let { return it }
        }

        parseXmpGpsFromBytes(bytes)?.let { return it }
        parseMp4Iso6709AndLociFromBytes(bytes)?.let { return it }

        if (isVideo) {
            parseBinaryTiffGpsIfd(bytes)?.let { return it }
        }
        return null
    }

    private fun parseBinaryTiffGpsIfd(bytes: ByteArray): Pair<Double, Double>? {
        val len = bytes.size
        var i = 0
        while (i <= len - 16) {
            val isLe = bytes[i] == 0x49.toByte() && bytes[i + 1] == 0x49.toByte() &&
                    bytes[i + 2] == 0x2A.toByte() && bytes[i + 3] == 0x00.toByte()
            val isBe = bytes[i] == 0x4D.toByte() && bytes[i + 1] == 0x4D.toByte() &&
                    bytes[i + 2] == 0x00.toByte() && bytes[i + 3] == 0x2A.toByte()
            if (isLe || isBe) {
                val coords = tryParseTiffAtOffset(bytes, i, isLe)
                if (coords != null && isValidLatLon(coords.first, coords.second)) {
                    return coords
                }
            }
            i++
        }
        return null
    }

    private fun tryParseTiffAtOffset(bytes: ByteArray, tiffStart: Int, littleEndian: Boolean): Pair<Double, Double>? {
        val len = bytes.size
        fun u16(pos: Int): Int {
            if (pos < 0 || pos + 2 > len) return -1
            val b0 = bytes[pos].toInt() and 0xFF
            val b1 = bytes[pos + 1].toInt() and 0xFF
            return if (littleEndian) (b1 shl 8) or b0 else (b0 shl 8) or b1
        }
        fun i32(pos: Int): Int {
            if (pos < 0 || pos + 4 > len) return 0
            val b0 = bytes[pos].toInt() and 0xFF
            val b1 = bytes[pos + 1].toInt() and 0xFF
            val b2 = bytes[pos + 2].toInt() and 0xFF
            val b3 = bytes[pos + 3].toInt() and 0xFF
            return if (littleEndian) {
                (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
        fun u32(pos: Int): Long = i32(pos).toLong() and 0xFFFFFFFFL

        val firstIfdRel = u32(tiffStart + 4).toInt()
        if (firstIfdRel < 8 || tiffStart + firstIfdRel + 2 > len) return null

        var ifdOffset = tiffStart + firstIfdRel
        var gpsIfdRel = -1
        var hop = 0
        while (ifdOffset >= tiffStart && ifdOffset + 2 <= len && hop < 4) {
            hop++
            val entryCount = u16(ifdOffset)
            if (entryCount <= 0 || entryCount > 512) break
            val entriesEnd = ifdOffset + 2 + entryCount * 12
            if (entriesEnd > len) break

            for (e in 0 until entryCount) {
                val entryPos = ifdOffset + 2 + e * 12
                val tag = u16(entryPos)
                if (tag == 0x8825) {
                    gpsIfdRel = u32(entryPos + 8).toInt()
                    break
                }
            }
            if (gpsIfdRel > 0) break
            val nextIfdRel = u32(entriesEnd).toInt()
            if (nextIfdRel <= 0) break
            ifdOffset = tiffStart + nextIfdRel
        }

        if (gpsIfdRel <= 0) return null
        val gpsIfdPos = tiffStart + gpsIfdRel
        if (gpsIfdPos < tiffStart || gpsIfdPos + 2 > len) return null

        val gpsCount = u16(gpsIfdPos)
        if (gpsCount <= 0 || gpsCount > 128 || gpsIfdPos + 2 + gpsCount * 12 > len) return null

        var latRef = "N"
        var lonRef = "E"
        var latVal: Double? = null
        var lonVal: Double? = null

        fun readCoordTriplet(type: Int, count: Int, valueFieldPos: Int): Double? {
            if ((type == 5 || type == 10) && count >= 3) {
                val dataRel = u32(valueFieldPos).toInt()
                val dataPos = tiffStart + dataRel
                if (dataPos < tiffStart || dataPos + 24 > len) return null
                fun rat(idx: Int): Double {
                    val p = dataPos + idx * 8
                    return if (type == 10) {
                        val num = i32(p).toDouble()
                        val den = i32(p + 4).toDouble()
                        if (den == 0.0) 0.0 else num / den
                    } else {
                        val num = u32(p).toDouble()
                        val den = u32(p + 4).toDouble()
                        if (den == 0.0) 0.0 else num / den
                    }
                }
                val deg = rat(0)
                val min = rat(1)
                val sec = rat(2)
                return kotlin.math.abs(deg) + (kotlin.math.abs(min) / 60.0) + (kotlin.math.abs(sec) / 3600.0)
            }
            return null
        }

        for (e in 0 until gpsCount) {
            val entryPos = gpsIfdPos + 2 + e * 12
            val tag = u16(entryPos)
            val type = u16(entryPos + 2)
            val count = u32(entryPos + 4).toInt()
            val valPos = entryPos + 8

            when (tag) {
                0x0001 -> {
                    val ch = (bytes[valPos].toInt() and 0xFF).toChar()
                    if (ch == 'S' || ch == 's') latRef = "S"
                    else if (ch == 'N' || ch == 'n') latRef = "N"
                }
                0x0002 -> {
                    latVal = readCoordTriplet(type, count, valPos)
                }
                0x0003 -> {
                    val ch = (bytes[valPos].toInt() and 0xFF).toChar()
                    if (ch == 'W' || ch == 'w') lonRef = "W"
                    else if (ch == 'E' || ch == 'e') lonRef = "E"
                }
                0x0004 -> {
                    lonVal = readCoordTriplet(type, count, valPos)
                }
            }
        }

        if (latVal != null && lonVal != null) {
            val finalLat = if (latRef == "S") -latVal else latVal
            val finalLon = if (lonRef == "W") -lonVal else lonVal
            if (isValidLatLon(finalLat, finalLon)) {
                return finalLat to finalLon
            }
        }
        return null
    }

    private fun parseXmpGpsCoordinate(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null
        val lastChar = s.last().uppercaseChar()
        val isNeg = lastChar == 'S' || lastChar == 'W' || s.startsWith("-")
        val numPart = if (lastChar in listOf('N', 'S', 'E', 'W')) s.dropLast(1).trim() else s
        val parts = numPart.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val deg = parts[0].toDoubleOrNull() ?: return null
        val min = if (parts.size >= 2) (parts[1].toDoubleOrNull() ?: 0.0) else 0.0
        val sec = if (parts.size >= 3) (parts[2].toDoubleOrNull() ?: 0.0) else 0.0
        val absVal = kotlin.math.abs(deg) + (min / 60.0) + (sec / 3600.0)
        return if (isNeg) -absVal else absVal
    }

    private fun parseXmpGpsFromBytes(bytes: ByteArray): Pair<Double, Double>? {
        return try {
            val text = String(bytes, Charsets.ISO_8859_1)
            if (!text.contains("GPSLatitude", ignoreCase = true)) return null
            val latRegex = Regex("""exif:GPSLatitude(?:\s*=\s*"([^"]+)"|>([^<]+)</exif:GPSLatitude>)""", RegexOption.IGNORE_CASE)
            val lonRegex = Regex("""exif:GPSLongitude(?:\s*=\s*"([^"]+)"|>([^<]+)</exif:GPSLongitude>)""", RegexOption.IGNORE_CASE)
            val latMatch = latRegex.find(text) ?: return null
            val lonMatch = lonRegex.find(text) ?: return null
            val latStr = (latMatch.groupValues[1].ifEmpty { latMatch.groupValues[2] }).trim()
            val lonStr = (lonMatch.groupValues[1].ifEmpty { lonMatch.groupValues[2] }).trim()
            var lat = parseXmpGpsCoordinate(latStr) ?: return null
            var lon = parseXmpGpsCoordinate(lonStr) ?: return null

            val latRefRegex = Regex("""exif:GPSLatitudeRef(?:\s*=\s*"([^"]+)"|>([^<]+)</exif:GPSLatitudeRef>)""", RegexOption.IGNORE_CASE)
            val lonRefRegex = Regex("""exif:GPSLongitudeRef(?:\s*=\s*"([^"]+)"|>([^<]+)</exif:GPSLongitudeRef>)""", RegexOption.IGNORE_CASE)
            val latRef = latRefRegex.find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
            val lonRef = lonRefRegex.find(text)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }?.trim().orEmpty()
            if (latRef.startsWith("S", ignoreCase = true) && lat > 0) lat = -lat
            if (lonRef.startsWith("W", ignoreCase = true) && lon > 0) lon = -lon

            if (isValidLatLon(lat, lon)) lat to lon else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMp4Iso6709AndLociFromBytes(bytes: ByteArray): Pair<Double, Double>? {
        return try {
            val text = String(bytes, Charsets.ISO_8859_1)
            // 1. Tìm chuẩn chuỗi ISO-6709 (ví dụ: +21.0285+105.8542/ hoặc +10.7756+106.7019+015.00/)
            val isoRegex = Regex("""([+-]\d{1,2}\.\d{3,10})([+-]\d{1,3}\.\d{3,10})(?:[+-]\d+(?:\.\d+)?)?/?""")
            for (m in isoRegex.findAll(text)) {
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && isValidLatLon(lat, lon)) {
                    return lat to lon
                }
            }

            // 2. Tìm box nhị phân 3GPP 'loci' (longitude & latitude lưu dạng 32-bit signed 16.16 fixed-point)
            val len = bytes.size
            var i = 4
            while (i <= len - 24) {
                if (bytes[i] == 'l'.code.toByte() &&
                    bytes[i + 1] == 'o'.code.toByte() &&
                    bytes[i + 2] == 'c'.code.toByte() &&
                    bytes[i + 3] == 'i'.code.toByte()
                ) {
                    val boxSize = ((bytes[i - 4].toInt() and 0xFF) shl 24) or
                            ((bytes[i - 3].toInt() and 0xFF) shl 16) or
                            ((bytes[i - 2].toInt() and 0xFF) shl 8) or
                            (bytes[i - 1].toInt() and 0xFF)
                    val boxEnd = if (boxSize in 24..512) minOf(len, i - 4 + boxSize) else minOf(len, i + 128)
                    var p = i + 10 // qua 'loci'(4) + version/flags(4) + pad/lang(2)
                    while (p < boxEnd && bytes[p] != 0.toByte()) p++
                    p += 2 // qua null-terminator (1) + role (1)
                    if (p + 8 <= boxEnd) {
                        val rawLon = ((bytes[p].toInt() and 0xFF) shl 24) or
                                ((bytes[p + 1].toInt() and 0xFF) shl 16) or
                                ((bytes[p + 2].toInt() and 0xFF) shl 8) or
                                (bytes[p + 3].toInt() and 0xFF)
                        val rawLat = ((bytes[p + 4].toInt() and 0xFF) shl 24) or
                                ((bytes[p + 5].toInt() and 0xFF) shl 16) or
                                ((bytes[p + 6].toInt() and 0xFF) shl 8) or
                                (bytes[p + 7].toInt() and 0xFF)
                        val lon = rawLon / 65536.0
                        val lat = rawLat / 65536.0
                        if (isValidLatLon(lat, lon)) {
                            return lat to lon
                        }
                    }
                }
                i++
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Trích xuất GPS trực tiếp từ file SGV2 đã mã hoá trong Vault (hỗ trợ cả ảnh, Live Photo và video lớn).
     * Đọc phân đoạn đầu (Head) và phân đoạn cuối (Tail) bằng giải mã khối ngẫu nhiên O(1) của SGV2.
     */
    private fun extractGpsFromEncryptedVaultItem(
        context: Context,
        item: VaultItem,
        key: ByteArray
    ): MediaGpsInfo? {
        return try {
            val info = CryptoEngine.readSgv2FileInfo(context, item.uri, key)
            val totalDecSize = info?.decryptedSize ?: 0L

            if (!item.isVideo) {
                // Giải mã 1 MB đầu của ảnh (nơi chứa toàn bộ EXIF / TIFF / XMP của JPEG, HEIC, WebP, PNG)
                val headBytes = CryptoEngine.decryptSgv2FullSlice(
                    context = context,
                    uri = item.uri,
                    dek = key,
                    plainOffset = 0L,
                    maxLength = 1024 * 1024
                )
                if (headBytes != null && headBytes.isNotEmpty()) {
                    extractGpsFromBinaryBytes(headBytes, isVideo = false)?.let { (lat, lon) ->
                        return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                    }
                }

                // Với HEIC/DNG có metadata nằm ở phần sau hoặc Live Photo có gắn kèm video MP4/MOV:
                if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L && info.liveVideoSize > 0L) {
                    val liveHead = CryptoEngine.decryptSgv2FullSlice(
                        context = context,
                        uri = item.uri,
                        dek = key,
                        plainOffset = info.liveVideoOffset,
                        maxLength = minOf(512 * 1024L, info.liveVideoSize).toInt()
                    )
                    if (liveHead != null && liveHead.isNotEmpty()) {
                        extractGpsFromBinaryBytes(liveHead, isVideo = true)?.let { (lat, lon) ->
                            return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                        }
                    }
                    if (info.liveVideoSize > 512 * 1024L) {
                        val tailLen = minOf(1024 * 1024L, info.liveVideoSize).toInt()
                        val tailOffset = info.liveVideoOffset + info.liveVideoSize - tailLen
                        val liveTail = CryptoEngine.decryptSgv2FullSlice(
                            context = context,
                            uri = item.uri,
                            dek = key,
                            plainOffset = tailOffset,
                            maxLength = tailLen
                        )
                        if (liveTail != null && liveTail.isNotEmpty()) {
                            extractGpsFromBinaryBytes(liveTail, isVideo = true)?.let { (lat, lon) ->
                                return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                            }
                        }
                    }
                } else if (totalDecSize > 1024 * 1024L && totalDecSize <= 32 * 1024 * 1024L) {
                    val tailLen = minOf(1024 * 1024L, totalDecSize - 1024 * 1024L).toInt()
                    val imgTail = CryptoEngine.decryptSgv2FullSlice(
                        context = context,
                        uri = item.uri,
                        dek = key,
                        plainOffset = totalDecSize - tailLen,
                        maxLength = tailLen
                    )
                    if (imgTail != null && imgTail.isNotEmpty()) {
                        extractGpsFromBinaryBytes(imgTail, isVideo = false)?.let { (lat, lon) ->
                            return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                        }
                    }
                }
                null
            } else {
                // Đối với Video: kiểm tra 1 MB đầu VÀ 2 MB cuối (nơi chứa moov/udta/meta/©xyz/loci của MP4/MOV)
                val headBytes = CryptoEngine.decryptSgv2FullSlice(
                    context = context,
                    uri = item.uri,
                    dek = key,
                    plainOffset = 0L,
                    maxLength = 1024 * 1024
                )
                if (headBytes != null && headBytes.isNotEmpty()) {
                    extractGpsFromBinaryBytes(headBytes, isVideo = true)?.let { (lat, lon) ->
                        return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                    }
                }

                if (totalDecSize > 512 * 1024L) {
                    val tailLen = minOf(2 * 1024 * 1024L, totalDecSize).toInt()
                    val tailStart = (totalDecSize - tailLen).coerceAtLeast(0L)
                    val tailBytes = CryptoEngine.decryptSgv2FullSlice(
                        context = context,
                        uri = item.uri,
                        dek = key,
                        plainOffset = tailStart,
                        maxLength = tailLen
                    )
                    if (tailBytes != null && tailBytes.isNotEmpty()) {
                        extractGpsFromBinaryBytes(tailBytes, isVideo = true)?.let { (lat, lon) ->
                            return GeoNamesReverseGeocoder.reverseGeocodeDetailed(context, lat, lon)
                        }
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun detectEmbeddedMotionVideo(context: Context, uri: Uri, totalFileSize: Long): Pair<Long, Long>? {
        if (totalFileSize < 16384L) return null
        val pfd = try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        } ?: return null

        return try {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { fis ->
                val channel = fis.channel

                val headSize = minOf(65536L, totalFileSize).toInt()
                val headBuf = ByteBuffer.allocate(headSize)
                channel.read(headBuf, 0L)
                val headStr = String(headBuf.array(), Charsets.ISO_8859_1)

                val xmpOffsetRegexes = listOf(
                    Regex("""MicroVideoOffset\s*[=:]\s*"(\d+)""""),
                    Regex("""<GCamera:MicroVideoOffset>(\d+)</GCamera:MicroVideoOffset>"""),
                    Regex("""Item:Semantic="MotionPhoto"[^>]*Item:Length="(\d+)""""),
                    Regex("""Item:Length="(\d+)"[^>]*Item:Semantic="MotionPhoto"""")
                )

                for (regex in xmpOffsetRegexes) {
                    val match = regex.find(headStr)
                    val videoLen = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: continue
                    if (videoLen in 4096L..(totalFileSize - 1024L)) {
                        val candidateOffset = totalFileSize - videoLen
                        if (isMp4HeaderAt(channel, candidateOffset, totalFileSize)) {
                            return candidateOffset to videoLen
                        }
                    }
                }

                val maxTailScan = minOf(24L * 1024L * 1024L, totalFileSize - 1024L)
                if (maxTailScan <= 4096L) return null

                val scanStart = (totalFileSize - maxTailScan).coerceAtLeast(1024L)
                val chunkSize = 256 * 1024
                val overlap = 32
                val chunkBuf = ByteArray(chunkSize + overlap)
                var pos = scanStart

                val samsungMarker = "MotionPhoto_Data".toByteArray(Charsets.US_ASCII)
                val validVideoBrands = setOf("mp41", "mp42", "isom", "iso2", "avc1", "qt  ", "3gp4", "3gp5", "mmp4")

                while (pos < totalFileSize - 16) {
                    val toRead = minOf(chunkBuf.size.toLong(), totalFileSize - pos).toInt()
                    val bb = ByteBuffer.wrap(chunkBuf, 0, toRead)
                    var readBytes = 0
                    while (readBytes < toRead) {
                        val n = channel.read(bb, pos + readBytes)
                        if (n <= 0) break
                        readBytes += n
                    }
                    if (readBytes < 16) break

                    var i = 0
                    while (i <= readBytes - 16) {
                        if (chunkBuf[i] == samsungMarker[0] &&
                            chunkBuf[i + 1] == samsungMarker[1] &&
                            chunkBuf[i + 2] == samsungMarker[2] &&
                            i + 24 <= readBytes
                        ) {
                            var matchSamsung = true
                            for (k in samsungMarker.indices) {
                                if (chunkBuf[i + k] != samsungMarker[k]) {
                                    matchSamsung = false
                                    break
                                }
                            }
                            if (matchSamsung) {
                                val mp4Start = pos + i + samsungMarker.size
                                if (isMp4HeaderAt(channel, mp4Start, totalFileSize)) {
                                    return mp4Start to (totalFileSize - mp4Start)
                                }
                            }
                        }

                        if (chunkBuf[i + 4] == 'f'.code.toByte() &&
                            chunkBuf[i + 5] == 't'.code.toByte() &&
                            chunkBuf[i + 6] == 'y'.code.toByte() &&
                            chunkBuf[i + 7] == 'p'.code.toByte()
                        ) {
                            val boxSize = ((chunkBuf[i].toInt() and 0xFF) shl 24) or
                                    ((chunkBuf[i + 1].toInt() and 0xFF) shl 16) or
                                    ((chunkBuf[i + 2].toInt() and 0xFF) shl 8) or
                                    (chunkBuf[i + 3].toInt() and 0xFF)
                            val brand = String(chunkBuf, i + 8, 4, Charsets.US_ASCII)
                            if (boxSize in 16..128 && brand in validVideoBrands) {
                                val mp4Start = pos + i
                                val mp4Len = totalFileSize - mp4Start
                                if (mp4Start >= 1024L && mp4Len >= 4096L) {
                                    return mp4Start to mp4Len
                                }
                            }
                        }
                        i++
                    }

                    pos += chunkSize
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isMp4HeaderAt(channel: java.nio.channels.FileChannel, offset: Long, totalSize: Long): Boolean {
        if (offset < 0L || offset + 12L > totalSize) return false
        val buf = ByteBuffer.allocate(12)
        var read = 0
        while (read < 12) {
            val n = channel.read(buf, offset + read)
            if (n <= 0) break
            read += n
        }
        if (read < 12) return false
        val arr = buf.array()
        return arr[4] == 'f'.code.toByte() &&
                arr[5] == 't'.code.toByte() &&
                arr[6] == 'y'.code.toByte() &&
                arr[7] == 'p'.code.toByte()
    }

    private fun cropCenterSquareBitmap(source: Bitmap, targetSizePx: Int): Bitmap {
        val w = source.width.coerceAtLeast(1)
        val h = source.height.coerceAtLeast(1)
        val edge = minOf(w, h)
        val offsetX = (w - edge) / 2
        val offsetY = (h - edge) / 2
        val cropped = Bitmap.createBitmap(source, offsetX, offsetY, edge, edge)
        if (cropped.width == targetSizePx && cropped.height == targetSizePx) {
            return cropped
        }
        val scaled = Bitmap.createScaledBitmap(cropped, targetSizePx, targetSizePx, true)
        if (scaled !== cropped && cropped !== source) {
            cropped.recycle()
        }
        return scaled
    }

    private fun vibrateThreeTimes(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            val timings = longArrayOf(0L, 380L, 160L, 380L, 160L, 450L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    vibrator.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateTierBitmaps(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        chosenVideoFrame: Bitmap? = null,
        recycleInputBitmap: Boolean = true
    ): Triple<ByteArray, ByteArray, Pair<Int, Int>> {
        var origW = 0
        var origH = 0

        val baseBmp: Bitmap = if (isVideo) {
            if (chosenVideoFrame != null) {
                origW = chosenVideoFrame.width
                origH = chosenVideoFrame.height
                chosenVideoFrame
            } else {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    origW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    origH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } else {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            origW = options.outWidth.coerceAtLeast(0)
            origH = options.outHeight.coerceAtLeast(0)

            val exifRotation = getExifRotationDegrees(context, uri)
            if (exifRotation == 90 || exifRotation == 270) {
                val tmp = origW
                origW = origH
                origH = tmp
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(options, 2048, 2048)
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

            if (exifRotation != 0) {
                val matrix = Matrix().apply { postRotate(exifRotation.toFloat()) }
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated != decoded) decoded.recycle()
                rotated
            } else {
                decoded
            }
        }

        val bw = baseBmp.width.coerceAtLeast(1)
        val bh = baseBmp.height.coerceAtLeast(1)
        if (origW <= 0 || origH <= 0) {
            origW = bw
            origH = bh
        }

        val screenW = minOf(1080, bw)
        val screenH = (bh * (screenW.toFloat() / bw)).toInt().coerceAtLeast(1)
        val screenBmp = Bitmap.createScaledBitmap(baseBmp, screenW, screenH, true)

        val thumbBmp = createSmoothThumbnail(screenBmp, THUMB_SIZE_PX)

        val compressFormat = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        val targetQuality = 75

        val thumbBaos = ByteArrayOutputStream()
        thumbBmp.compress(compressFormat, targetQuality, thumbBaos)

        val screenBaos = ByteArrayOutputStream()
        screenBmp.compress(compressFormat, targetQuality, screenBaos)

        if (baseBmp != screenBmp && (recycleInputBitmap || baseBmp !== chosenVideoFrame)) {
            baseBmp.recycle()
        }
        if (thumbBmp != screenBmp) thumbBmp.recycle()
        if (screenBmp !== chosenVideoFrame || recycleInputBitmap) {
            screenBmp.recycle()
        }

        return Triple(thumbBaos.toByteArray(), screenBaos.toByteArray(), Pair(origW, origH))
    }

    private fun getExifRotationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    private fun createSmoothThumbnail(source: Bitmap, targetMinEdge: Int): Bitmap {
        var current = source
        while (true) {
            val cw = current.width
            val ch = current.height

            if (cw <= targetMinEdge * 2 || ch <= targetMinEdge * 2) {
                break
            }

            val nextBmp = Bitmap.createScaledBitmap(current, cw / 2, ch / 2, true)

            if (current != source) {
                current.recycle()
            }
            current = nextBmp
        }

        val scale = targetMinEdge.toFloat() / minOf(current.width, current.height)
        val finalW = (current.width * scale).toInt().coerceAtLeast(1)
        val finalH = (current.height * scale).toInt().coerceAtLeast(1)

        val result = Bitmap.createScaledBitmap(current, finalW, finalH, true)

        if (current != source) {
            current.recycle()
        }

        return result
    }

    fun deleteMedia(context: Context, item: VaultItem) {
        val vaultUriVal = _vaultUri.value
        val key = _dek.value
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                com.example.ui.screens.ThumbBitmapCache.remove(item.encryptedName)
                com.example.ui.screens.ScreenBitmapCache.remove(item.encryptedName)
                com.example.ui.screens.FullBitmapCache.remove(item.encryptedName)
                val scanned = withContext(Dispatchers.IO) {
                    CryptoEngine.deleteFile(context, item.uri)
                    if (vaultUriVal != null && key != null) {
                        val updatedNotes = _mediaNotes.value.toMutableMap().apply { remove(item.encryptedName) }
                        _mediaNotes.value = updatedNotes
                        persistMediaNotesLocked(context, vaultUriVal, key, updatedNotes)

                        val updatedGps = _mediaGps.value.toMutableMap().apply { remove(item.encryptedName) }
                        scannedNoGpsKeys.remove(item.encryptedName)
                        _mediaGps.value = updatedGps
                        persistMediaGpsLocked(context, vaultUriVal, key, updatedGps, scannedNoGpsKeys.toSet())
                        scanFiles(context, vaultUriVal, key)
                    } else {
                        _items.value
                    }
                }
                _items.value = scanned
                _selectedMedia.value = null
                playCompletionTickAnimation()
            } catch (e: Exception) {
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    fun deleteMultipleMedia(context: Context, itemsToDelete: List<VaultItem>, onFinished: (() -> Unit)? = null) {
        if (itemsToDelete.isEmpty()) return
        val vaultUriVal = _vaultUri.value
        val key = _dek.value
        viewModelScope.launch {
            _isLoading.value = true
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _error.value = null
            try {
                val scanned = withContext(Dispatchers.IO) {
                    val total = itemsToDelete.size.coerceAtLeast(1)
                    val deletedKeys = itemsToDelete.map { it.encryptedName }.toSet()
                    deletedKeys.forEach { k ->
                        com.example.ui.screens.ThumbBitmapCache.remove(k)
                        com.example.ui.screens.ScreenBitmapCache.remove(k)
                        com.example.ui.screens.FullBitmapCache.remove(k)
                    }
                    itemsToDelete.forEachIndexed { idx, item ->
                        CryptoEngine.deleteFile(context, item.uri)
                        _importProgress.value = (idx + 1f) / total
                    }
                    if (vaultUriVal != null && key != null) {
                        val updatedNotes = _mediaNotes.value.filterKeys { it !in deletedKeys }
                        _mediaNotes.value = updatedNotes
                        persistMediaNotesLocked(context, vaultUriVal, key, updatedNotes)

                        val updatedGps = _mediaGps.value.filterKeys { it !in deletedKeys }
                        scannedNoGpsKeys.removeAll(deletedKeys)
                        _mediaGps.value = updatedGps
                        persistMediaGpsLocked(context, vaultUriVal, key, updatedGps, scannedNoGpsKeys.toSet())
                        scanFiles(context, vaultUriVal, key)
                    } else {
                        _items.value
                    }
                }
                _items.value = scanned
                _selectedMedia.value = null
                playCompletionTickAnimation()
                onFinished?.invoke()
            } catch (e: Exception) {
                _error.value = "Failed to delete selected files: ${e.message}"
            } finally {
                _isProcessCompleted.value = false
                _importProgress.value = 0f
                _isLoading.value = false
            }
        }
    }

    /**
     * Cấp phát tên file xuất ra duy nhất trong thư mục đích:
     * Nếu có nhiều file trùng tên gốc (ví dụ cùng là "IMG_0001.JPG"), tự động đánh số
     * "IMG_0001 (1).JPG", "IMG_0001 (2).JPG"... để đảm bảo xuất đủ 100% không bị ghi đè hay xoá mất.
     */
    private fun allocateUniqueExportFileName(
        originalName: String,
        usedNamesLower: MutableSet<String>
    ): String {
        val cleanName = originalName.trim().ifEmpty { "exported_media" }
        if (usedNamesLower.add(cleanName.lowercase(Locale.ROOT))) {
            return cleanName
        }
        val dotIndex = cleanName.lastIndexOf('.')
        val baseName = if (dotIndex > 0) cleanName.substring(0, dotIndex) else cleanName
        val extWithDot = if (dotIndex > 0) cleanName.substring(dotIndex) else ""

        var counter = 1
        while (true) {
            val candidate = "$baseName ($counter)$extWithDot"
            if (usedNamesLower.add(candidate.lowercase(Locale.ROOT))) {
                return candidate
            }
            counter++
        }
    }

    private fun queryExistingFileNamesLower(context: Context, directoryUri: Uri): MutableSet<String> {
        val result = mutableSetOf<String>()
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                DocumentsContract.getTreeDocumentId(directoryUri)
            )
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrEmpty()) {
                        result.add(name.lowercase(Locale.ROOT))
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Giải mã và xuất (Export) các file ảnh/video gốc ra ngay bên trong thư mục Vault hiện tại.
     * Đảm bảo xuất đủ 100% số file được chọn kể cả khi có nhiều file trùng tên nhau.
     */
    fun exportMultipleMedia(
        context: Context,
        itemsToExport: List<VaultItem>,
        showOverlayLoading: Boolean = false,
        onFinished: ((Int) -> Unit)? = null
    ) {
        if (itemsToExport.isEmpty()) {
            onFinished?.invoke(0)
            return
        }
        val vaultUriVal = _vaultUri.value ?: return
        val key = _dek.value ?: return

        importJob?.cancel()
        isImportCancelled.set(false)
        _isCancellingImport.value = false
        _isExporting.value = true

        importJob = viewModelScope.launch {
            if (showOverlayLoading) {
                _isLoading.value = true
            }
            _importProgress.value = 0f
            _isProcessCompleted.value = false
            _importQueueTotal.value = itemsToExport.size
            _importQueueIndex.value = 0
            _currentImportThumb.value = null
            _currentImportIsVideo.value = false
            _currentImportIsLivePhoto.value = false
            _error.value = null

            var exportedCount = 0
            val createdExportUris = Collections.synchronizedList(mutableListOf<Uri>())

            try {
                withContext(Dispatchers.IO) {
                    val totalFiles = itemsToExport.size.coerceAtLeast(1)
                    val usedFileNamesLower = queryExistingFileNamesLower(context, vaultUriVal)

                    itemsToExport.forEachIndexed { index, item ->
                        if (isImportCancelled.get()) throw CancellationException("Export cancelled")
                        _importQueueIndex.value = index + 1
                        _currentImportIsVideo.value = item.isVideo
                        _currentImportIsLivePhoto.value = item.isLivePhoto

                        // Giải mã nhanh ảnh THUMB để hiển thị trên khung tiến độ vuông
                        try {
                            CryptoEngine.getSgv2TierStream(context, item.uri, CryptoEngine.Tier.THUMB, key)?.use { tStream ->
                                val tBytes = tStream.readBytes()
                                val bmp = BitmapFactory.decodeByteArray(tBytes, 0, tBytes.size)
                                if (bmp != null) {
                                    _currentImportThumb.value = cropCenterSquareBitmap(bmp, THUMB_SIZE_PX)
                                }
                            }
                        } catch (_: Exception) {}

                        val info = CryptoEngine.readSgv2FileInfo(context, item.uri, key)
                        val totalPlainBytes = (info?.decryptedSize ?: 0L).let { baseSize ->
                            if (info != null && info.isLivePhoto && info.liveVideoOffset > 0L && info.liveVideoSize > 0L) {
                                info.liveVideoOffset + info.liveVideoSize
                            } else {
                                baseSize
                            }
                        }

                        val mimeType = resolveMimeTypeForFileName(item.originalName, item.isVideo)
                        val uniqueExportName = allocateUniqueExportFileName(item.originalName, usedFileNamesLower)

                        val destUri = CryptoEngine.createChildFile(context, vaultUriVal, uniqueExportName, mimeType)
                            ?: CryptoEngine.createChildFile(context, vaultUriVal, uniqueExportName, "application/octet-stream")
                            ?: throw java.io.IOException("Cannot create exported file: $uniqueExportName")

                        createdExportUris.add(destUri)
                        _lastExportedFileUri.value = destUri

                        val streamCopied = CryptoEngine.getSgv2TierStream(context, item.uri, CryptoEngine.Tier.FULL, key)?.use { plainStream ->
                            context.contentResolver.openOutputStream(destUri)?.use { outStream ->
                                val buf = ByteArray(65536)
                                var copied = 0L
                                var read: Int
                                while (plainStream.read(buf).also { read = it } != -1) {
                                    if (isImportCancelled.get()) throw CancellationException("Export cancelled")
                                    outStream.write(buf, 0, read)
                                    copied += read
                                    if (totalPlainBytes > 0L) {
                                        val fileProg = (copied.toFloat() / totalPlainBytes.toFloat()).coerceIn(0f, 1f)
                                        _importProgress.value = (index + fileProg) / totalFiles
                                    }
                                }
                                outStream.flush()
                                true
                            } ?: false
                        } ?: false

                        if (!streamCopied) {
                            throw java.io.IOException("Failed to write exported data for $uniqueExportName")
                        }

                        exportedCount++
                        _importProgress.value = (index + 1f) / totalFiles
                    }
                }

                vibrateThreeTimes(context)
                if (showOverlayLoading) {
                    playCompletionTickAnimation()
                } else {
                    _importProgress.value = 1f
                }
                Toast.makeText(
                    context,
                    "Exported $exportedCount original file(s) to Vault folder",
                    Toast.LENGTH_SHORT
                ).show()
                onFinished?.invoke(exportedCount)
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val snapshot = synchronized(createdExportUris) { createdExportUris.toList() }
                    snapshot.forEach { uriToDelete ->
                        CryptoEngine.deleteFile(context, uriToDelete)
                    }
                }
            } catch (e: Exception) {
                _error.value = "Export failed: ${e.message}"
            } finally {
                withContext(NonCancellable) {
                    _currentImportThumb.value = null
                    _currentImportIsVideo.value = false
                    _currentImportIsLivePhoto.value = false
                    _isCancellingImport.value = false
                    _isExporting.value = false
                    isImportCancelled.set(false)
                    _isProcessCompleted.value = false
                    _isLoading.value = false
                    _importProgress.value = 0f
                    _importQueueTotal.value = 0
                    _importQueueIndex.value = 0
                }
            }
        }
    }

    /**
     * Mở đúng thư mục lưu file vừa tải xuống (Export) bằng cơ chế tương thích với nền tảng Android hiện tại.
     */
    fun openExportedFolder(context: Context) {
        val vaultTreeUri = _vaultUri.value ?: return
        val lastFileUri = _lastExportedFileUri.value
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(vaultTreeUri)
            val folderDocUri = DocumentsContract.buildDocumentUriUsingTree(vaultTreeUri, treeDocId)

            val dirIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderDocUri, DocumentsContract.Document.MIME_TYPE_DIR)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            if (dirIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(dirIntent)
                return
            }

            for (pkg in listOf("com.google.android.documentsui", "com.android.documentsui")) {
                val docsUiIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(folderDocUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    setPackage(pkg)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }
                if (docsUiIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(docsUiIntent)
                    return
                }
            }

            if (lastFileUri != null) {
                val mime = context.contentResolver.getType(lastFileUri) ?: "*/*"
                val viewFileIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(lastFileUri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (viewFileIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(viewFileIntent)
                    return
                }
            }

            val openTreeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, folderDocUri)
                }
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(openTreeIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Saved to Vault folder", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveMimeTypeForFileName(fileName: String, isVideo: Boolean): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> if (isVideo) "video/mp4" else "image/jpeg"
        }
    }

    private fun isVideoFile(fileName: String) = fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v")

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) name = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        } catch (e: Exception) {}
        if (name == null) name = uri.lastPathSegment
        return name
    }

    private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var size = -1L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && !cursor.isNull(idx)) size = cursor.getLong(idx)
                }
            }
        } catch (e: Exception) {}
        if (size <= 0L) {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    if (afd.length >= 0) size = afd.length
                }
            } catch (e: Exception) {}
        }
        return size
    }

    /**
     * Giữ nguyên giờ địa phương nguyên bản như hiển thị trong EXIF tool (không cộng lệch múi giờ UTC):
     * 1. Ưu tiên đọc trực tiếp từ thẻ EXIF (ảnh) hoặc METADATA_KEY_DATE (video) và parse thẳng các chữ số
     *    năm-tháng-ngày giờ:phút:giây theo TimeZone.getDefault() (không đổi từ UTC sang +7h).
     * 2. Nếu file không có thẻ EXIF/metadata hợp lệ, thử tách ngày giờ từ tên file (ví dụ VID_20251217_193000).
     * 3. Cuối cùng mới fallback sang cột datetaken / last_modified / date_modified.
     */
    private fun getActualDateTaken(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        originalName: String? = null
    ): Long {
        try {
            if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val dateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                    if (!dateStr.isNullOrEmpty()) {
                        val parsedMillis = parseLiteralLocalDateTime(dateStr)
                        if (parsedMillis != null) return parsedMillis
                    }
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val exif = ExifInterface(inputStream)
                        val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)

                        if (!dateTime.isNullOrEmpty()) {
                            val parsedMillis = parseLiteralLocalDateTime(dateTime)
                            if (parsedMillis != null) return parsedMillis
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback 2: Tách ngày giờ từ tên file nếu có (ví dụ: VID_20251217_193000.mp4, 2025-12-17...)
        val fileNameToCheck = originalName ?: getFileNameFromUri(context, uri)
        if (!fileNameToCheck.isNullOrEmpty()) {
            val fromName = parseDateFromFileName(fileNameToCheck)
            if (fromName != null) return fromName
        }

        // Fallback 3: Truy vấn datetaken / last_modified / date_modified từ ContentResolver
        try {
            context.contentResolver.query(uri, arrayOf("datetaken"), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("datetaken")
                    if (idx != -1 && !it.isNull(idx)) {
                        val dt = it.getLong(idx)
                        if (dt > 0L) return dt
                    }
                }
            }
        } catch (e: Exception) {}

        try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, "date_modified"),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) {
                    val lastModIdx = it.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (lastModIdx != -1 && !it.isNull(lastModIdx)) {
                        val lastMod = it.getLong(lastModIdx)
                        if (lastMod > 0L) return if (lastMod < 10_000_000_000L) lastMod * 1000L else lastMod
                    }
                    val modIdx = it.getColumnIndex("date_modified")
                    if (modIdx != -1 && !it.isNull(modIdx)) {
                        val modified = it.getLong(modIdx)
                        if (modified > 0L) return if (modified < 10_000_000_000L) modified * 1000L else modified
                    }
                }
            }
        } catch (e: Exception) {}

        return System.currentTimeMillis()
    }

    /**
     * Đọc giữ nguyên các con số Năm-Tháng-Ngày Giờ-Phút-Giây như hiển thị trong EXIF tool
     * theo múi giờ địa phương của máy (TimeZone.getDefault()), tránh hoàn toàn lỗi cộng lệch +7 tiếng.
     */
    private fun parseLiteralLocalDateTime(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return null

        // Tách trực tiếp các nhóm chữ số YYYY, MM, DD, hh, mm, ss từ chuỗi EXIF / ISO-8601
        val regex = Regex("""(\d{4})[:\-/]?(\d{2})[:\-/]?(\d{2})[T\s_]*(\d{2})[:\-]?(\d{2})[:\-]?(\d{2})""")
        val match = regex.find(cleaned)
        if (match != null) {
            val year = match.groupValues[1].toIntOrNull() ?: return null
            val month = match.groupValues[2].toIntOrNull() ?: return null
            val day = match.groupValues[3].toIntOrNull() ?: return null
            val hour = match.groupValues[4].toIntOrNull() ?: 0
            val minute = match.groupValues[5].toIntOrNull() ?: 0
            val second = match.groupValues[6].toIntOrNull() ?: 0

            // Bỏ qua các giá trị mặc định chưa khởi tạo của chuẩn MP4/QuickTime (1904 hoặc 1970)
            if (year in 1980..2100 && month in 1..12 && day in 1..31) {
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                    isLenient = false
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
                    set(Calendar.MINUTE, minute.coerceIn(0, 59))
                    set(Calendar.SECOND, second.coerceIn(0, 59))
                    set(Calendar.MILLISECOND, 0)
                }
                return try {
                    cal.timeInMillis
                } catch (_: Exception) {
                    null
                }
            }
        }

        // Trường hợp chuỗi chỉ có YYYY:MM:DD hoặc YYYY-MM-DD
        val dateOnlyRegex = Regex("""(\d{4})[:\-/](\d{2})[:\-/](\d{2})""")
        val dateMatch = dateOnlyRegex.find(cleaned)
        if (dateMatch != null) {
            val year = dateMatch.groupValues[1].toIntOrNull() ?: return null
            val month = dateMatch.groupValues[2].toIntOrNull() ?: return null
            val day = dateMatch.groupValues[3].toIntOrNull() ?: return null
            if (year in 1980..2100 && month in 1..12 && day in 1..31) {
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                    isLenient = false
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 12)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return try {
                    cal.timeInMillis
                } catch (_: Exception) {
                    null
                }
            }
        }
        return null
    }

    private fun parseDateFromFileName(fileName: String): Long? {
        val base = fileName.substringBeforeLast('.')
        val regexWithTime = Regex("""(?:^|\D)(20\d{2}|19[89]\d)[-_]?(0[1-9]|1[0-2])[-_]?(0[1-9]|[12]\d|3[01])[-_T ]?([01]\d|2[0-3])[-_]?([0-5]\d)[-_]?([0-5]\d)(?:\D|$)""")
        val m1 = regexWithTime.find(base)
        if (m1 != null) {
            val year = m1.groupValues[1].toInt()
            val month = m1.groupValues[2].toInt()
            val day = m1.groupValues[3].toInt()
            val hour = m1.groupValues[4].toInt()
            val minute = m1.groupValues[5].toInt()
            val second = m1.groupValues[6].toInt()
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                set(year, month - 1, day, hour, minute, second)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (options.outHeight > reqH || options.outWidth > reqW) {
            val halfH = options.outHeight / 2
            val halfW = options.outWidth / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) inSampleSize *= 2
        }
        return inSampleSize
    }
}