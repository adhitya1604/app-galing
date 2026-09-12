package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import com.example.data.model.OcrResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.regex.Pattern
import kotlin.coroutines.resume

object OcrExtractor {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Preprocesses the bitmap before OCR:
     * - Enhances contrast
     * - Converts to high-contrast grayscale to make ink pop against white label
     */
    fun preprocessBitmap(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhancedBitmap)
        val paint = Paint()

        val cm = ColorMatrix()
        cm.setSaturation(0f)

        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(original, 0f, 0f, paint)
        return enhancedBitmap
    }

    /**
     * Crop center 80% to focus on the label box
     */
    fun cropLabelArea(source: Bitmap): Bitmap {
        val cropX = (source.width * 0.05).toInt()
        val cropY = (source.height * 0.15).toInt()
        val cropWidth = (source.width * 0.90).toInt().coerceAtMost(source.width - cropX)
        val cropHeight = (source.height * 0.70).toInt().coerceAtMost(source.height - cropY)
        return Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)
    }

    suspend fun processImage(context: Context, imageUri: Uri): OcrResult = withContext(Dispatchers.IO) {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext OcrResult(
            rawText = "",
            trackingNumber = "",
            recipientName = "",
            address = "",
            courierCompany = "",
            isRecognized = false
        )
        processBitmap(bitmap)
    }

    /**
     * Dual-pass bitmap processing:
     * 1. Try original crisp bitmap first (best for ML Kit neural networks).
     * 2. If no valid tracking number recognized, fallback to high-contrast preprocessed bitmap.
     */
    suspend fun processBitmap(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        // Pass 1: Raw original bitmap
        val rawResult = runRecognizer(bitmap)
        if (rawResult.isRecognized && rawResult.trackingNumber.isNotBlank()) {
            return@withContext rawResult
        }

        // Pass 2: Enhanced contrast bitmap fallback
        val processed = preprocessBitmap(bitmap)
        val enhancedResult = runRecognizer(processed)
        if (enhancedResult.isRecognized && enhancedResult.trackingNumber.isNotBlank()) {
            return@withContext enhancedResult
        }

        // Return whichever extracted more information
        if (rawResult.rawText.length >= enhancedResult.rawText.length) rawResult else enhancedResult
    }

    private suspend fun runRecognizer(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val result = parseExtractedText(visionText.text)
                continuation.resume(result)
            }
            .addOnFailureListener {
                continuation.resume(parseExtractedText(""))
            }
    }

    /**
     * Highly versatile parser for Indonesian logistics labels.
     * Accurately supports:
     * - Shopee Xpress (SPX)
     * - J&T Express (JP, JX, JT, JZ, JS, JD, numeric)
     * - JNE Express (numeric, TLG, BDG, SOC, etc.)
     * - SiCepat Ekspres (00..., TKP...)
     * - Anteraja (100...)
     * - Ninja Xpress (NLID, NVID, SHP)
     * - Lion Parcel (LP, 99...)
     * - Pos Indonesia (POS, P...)
     * - TikTok Shop (TTID...)
     * - Lazada Logistics (LXRP...)
     * - Wahana Express
     * - Duo Galing Express
     * - Any general alphanumeric or numeric tracking number without forcing fixed prefixes!
     */
    fun parseExtractedText(text: String): OcrResult {
        if (text.isBlank()) {
            return OcrResult(
                rawText = "",
                trackingNumber = "",
                recipientName = "",
                address = "",
                courierCompany = "",
                isRecognized = false,
                confidence = 0f
            )
        }

        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        var trackingNumber = ""
        var recipientName = ""
        val addressLines = mutableListOf<String>()

        // -------------------------------------------------------------
        // STEP 1: KEYWORD-BASED TRACKING NUMBER EXTRACTION (Top Priority)
        // -------------------------------------------------------------
        // Check for keywords: No. Resi, Resi, AWB, No. AWB, Waybill, Connote, Tracking, Barcode, No. Pesanan
        val resiKeywordRegex = Regex(
            "(?i)^(?:no\\.?\\s*)?(?:resi|awb|waybill|connote|tracking(?:\\s*no(?:mor)?)?|barcode|kode\\s*resi|nomor\\s*resi)\\b"
        )

        for (i in lines.indices) {
            val line = lines[i]

            // Case A: Keyword and tracking number are on the SAME line
            // e.g., "No. Resi: SPXID048293849182", "AWB: 002938172635", "Resi: JP8293847192"
            val inlineMatch = Regex("(?i)(?:resi|awb|waybill|connote|tracking|barcode)\\s*[:#=\\-\\s]\\s*([A-Z0-9\\s\\-]{7,32})").find(line)
            if (inlineMatch != null) {
                val candidateRaw = inlineMatch.groupValues[1].trim()
                val candidateClean = cleanTrackingCandidate(candidateRaw)
                if (isValidTrackingNumber(candidateClean)) {
                    trackingNumber = candidateClean
                    break
                }
            }

            // Case B: Keyword is on line i, and the actual code is on the NEXT line (i + 1)
            // Very common in Indonesian printed labels:
            // Line 1: "No. Resi / AWB"
            // Line 2: "SPXID02938471928"
            if (resiKeywordRegex.containsMatchIn(line)) {
                // If there's a code right after a colon on this line
                val afterColon = line.substringAfter(':').trim()
                val candidateClean = cleanTrackingCandidate(afterColon)
                if (candidateClean.length >= 7 && isValidTrackingNumber(candidateClean)) {
                    trackingNumber = candidateClean
                    break
                }

                // Check next line (and line after next)
                for (offset in 1..2) {
                    if (i + offset < lines.size) {
                        val nextLine = lines[i + offset]
                        val nextCandidate = cleanTrackingCandidate(nextLine)
                        if (isValidTrackingNumber(nextCandidate)) {
                            trackingNumber = nextCandidate
                            break
                        }
                    }
                }
                if (trackingNumber.isNotEmpty()) break
            }
        }

        // -------------------------------------------------------------
        // STEP 2: KNOWN COURIER PATTERNS (Without forcing any prefix!)
        // -------------------------------------------------------------
        if (trackingNumber.isEmpty()) {
            val courierPatterns = listOf(
                // Shopee Xpress / SPX: e.g. SPXID02938471928, SPXID048293849182B, SPXMP01928374
                Regex("\\b(SPX[A-Z0-9]{8,22})\\b", RegexOption.IGNORE_CASE),
                // Shopee Order/AWB: e.g. ID240918274910A
                Regex("\\b(ID[0-9]{12,18}[A-Z0-9]?)\\b"),
                // J&T Express: e.g. JP0192837465, JX0192837465, JT0019283746, JZ..., JS..., JD...
                Regex("\\b((?:JP|JX|JT|JZ|JS|JD)[0-9]{8,14})\\b", RegexOption.IGNORE_CASE),
                // J&T pure numeric 12 digits (often starts with 888 or 04)
                Regex("\\b((?:888|04)[0-9]{9,11})\\b"),
                // SiCepat: 12-14 digits starting with 00 (e.g. 002938172635) or TKP format
                Regex("\\b(00[0-9]{10,12})\\b"),
                Regex("\\b(TKP[0-9A-Z\\-]{8,16})\\b", RegexOption.IGNORE_CASE),
                // Anteraja: 13-14 digits starting with 100 (e.g. 10002938471928)
                Regex("\\b(100[0-9]{10,12})\\b"),
                // JNE Express: JNE prefix or branch code prefix + 8-14 digits
                Regex("\\b((?:JNE|TLG|BDG|SOC|CGK|SUB|DPS|KNO|BKI)[0-9]{8,14})\\b", RegexOption.IGNORE_CASE),
                // JNE pure numeric 15-16 digits
                Regex("\\b([0-9]{15,16})\\b"),
                // Ninja Xpress: e.g. NLIDAP01928374, NVID01928374, SHP01928374
                Regex("\\b((?:NLIDAP|NLID|NVID|SHP)[0-9A-Z]{7,15})\\b", RegexOption.IGNORE_CASE),
                // Lion Parcel: e.g. LP0192837465, 990192837465
                Regex("\\b(LP[0-9A-Z]{8,14})\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(99[0-9]{10,12})\\b"),
                // Pos Indonesia: e.g. POS0192837465, P24091827491
                Regex("\\b((?:POS|P)[0-9]{10,13})\\b", RegexOption.IGNORE_CASE),
                // TikTok Shop / Lazada: e.g. TTID0192837461, LXRP-01928374
                Regex("\\b(TTID[0-9A-Z]{8,16})\\b", RegexOption.IGNORE_CASE),
                Regex("\\b(LXRP[0-9A-Z\\-]{8,16})\\b", RegexOption.IGNORE_CASE),
                // Duo Galing: DG-991823746190
                Regex("\\b(DG-[0-9]{8,14})\\b", RegexOption.IGNORE_CASE)
            )

            for (pattern in courierPatterns) {
                for (line in lines) {
                    val match = pattern.find(line)
                    if (match != null) {
                        val clean = cleanTrackingCandidate(match.groupValues[1])
                        if (isValidTrackingNumber(clean)) {
                            trackingNumber = clean
                            break
                        }
                    }
                }
                if (trackingNumber.isNotEmpty()) break
            }
        }

        // -------------------------------------------------------------
        // STEP 3: GENERAL UNIVERSAL BARCODE & TRACKING NUMBER SCANNER
        // (NO forced prefix! Supports any courier / logistics provider)
        // -------------------------------------------------------------
        if (trackingNumber.isEmpty()) {
            val candidates = mutableListOf<String>()

            for (line in lines) {
                // Split line into words or test entire line as single code
                val tokens = line.split("\\s+".toRegex()).filter { it.isNotBlank() }

                // Check tokens individually
                for (token in tokens) {
                    val clean = cleanTrackingCandidate(token)
                    if (isValidTrackingNumber(clean)) {
                        candidates.add(clean)
                    }
                }

                // Also check if line without internal spaces forms a valid code
                // e.g. OCR saw "SPXID 0482 9384 9182" or "JP 8293 8471 92"
                val mergedClean = cleanTrackingCandidate(line.replace(" ", ""))
                if (isValidTrackingNumber(mergedClean)) {
                    candidates.add(mergedClean)
                }
            }

            if (candidates.isNotEmpty()) {
                // Rank candidates:
                // 1. Alphanumeric with mix of letters & digits (strong indicator of resi)
                // 2. Pure numbers with 10-18 digits (JNE, SiCepat, Anteraja, J&T)
                // 3. Length between 10 and 20
                trackingNumber = candidates.maxByOrNull { candidate ->
                    var score = 0
                    val hasLetters = candidate.any { it.isLetter() }
                    val hasDigits = candidate.any { it.isDigit() }
                    if (hasLetters && hasDigits) score += 50
                    if (candidate.startsWith("SPX") || candidate.startsWith("JP") || candidate.startsWith("JX") ||
                        candidate.startsWith("00") || candidate.startsWith("100") || candidate.startsWith("DG-")) {
                        score += 80
                    }
                    if (candidate.length in 10..18) score += 30
                    score
                } ?: candidates.first()
            }
        }

        // -------------------------------------------------------------
        // STEP 4: DETECT COURIER / EKSPEDISI COMPANY
        // -------------------------------------------------------------
        val detectedCourier = detectCourier(text, trackingNumber)

        // -------------------------------------------------------------
        // STEP 5: RECIPIENT NAME EXTRACTION
        // -------------------------------------------------------------
        val recipientKeywords = listOf(
            "penerima", "kepada", "yth", "to", "receiver", "consignee", "nama penerima", "nama"
        )
        val recipientPattern = Pattern.compile(
            "(?i)(?:penerima|kepada|yth|to|receiver|consignee|nama\\s*penerima)\\s*[:#]?\\s*([A-Za-z\\s\\.,']{3,35})"
        )

        for (i in lines.indices) {
            val line = lines[i]
            val matcher = recipientPattern.matcher(line)
            if (matcher.find()) {
                val found = matcher.group(1)?.trim() ?: ""
                val cleanName = cleanRecipientName(found)
                if (cleanName.length >= 3) {
                    recipientName = cleanName
                    break
                }
            } else {
                // Check if line contains keyword only, and name is on the next line
                val lower = line.lowercase().replace(":", "").trim()
                if (recipientKeywords.any { lower == it || lower == "penerima / receiver" } && i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val cleanName = cleanRecipientName(nextLine)
                    if (cleanName.length >= 3) {
                        recipientName = cleanName
                        break
                    }
                }
            }
        }

        // Fallback for recipient name if no explicit label found
        if (recipientName.isEmpty()) {
            for (line in lines) {
                val clean = cleanRecipientName(line)
                if (clean.matches(Regex("^[A-Z][a-z]+(?:\\s[A-Z][a-z]+){1,3}$")) &&
                    !isSystemOrCourierWord(clean)
                ) {
                    recipientName = clean
                    break
                }
            }
        }

        // -------------------------------------------------------------
        // STEP 6: ADDRESS EXTRACTION
        // -------------------------------------------------------------
        var addressCollecting = false
        val addressStopKeywords = listOf(
            "pengirim", "sender", "berat", "weight", "ongkir", "biaya", "barcode",
            "catatan", "notes", "cod", "instruksi", "keterangan", "deskripsi", "barang"
        )
        val streetKeywords = listOf(
            "jl.", "jalan", "gang", "gg.", "blok", "rt.", "rt", "rw.", "rw",
            "kel.", "kelurahan", "kec.", "kecamatan", "kota", "kab.", "kabupaten",
            "provinsi", "perum", "komp.", "komplek", "dusun", "desa", "no."
        )

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            if (lower.contains("alamat:") || lower.contains("alamat penerima:") ||
                lower.contains("address:") || lower.contains("tujuan:") || lower.contains("alamat pengiriman:")
            ) {
                addressCollecting = true
                val cleanLine = line.replace(
                    Regex("(?i)(?:alamat(?:\\s*penerima|\\s*pengiriman)?|address|tujuan)\\s*[:#]?\\s*"),
                    ""
                ).trim()
                if (cleanLine.isNotBlank()) {
                    addressLines.add(cleanLine)
                }
                continue
            }

            if (addressCollecting) {
                if (addressStopKeywords.any { lower.startsWith(it) || lower.contains("$it:") }) {
                    addressCollecting = false
                } else {
                    addressLines.add(line)
                    if (addressLines.size >= 4) addressCollecting = false
                }
            } else {
                if (streetKeywords.any { lower.contains(it) } && !lower.contains("pengirim") && !lower.contains("sender")) {
                    addressLines.add(line)
                }
            }
        }

        val fullAddress = addressLines.joinToString(", ")
            .replace(", ,", ",")
            .replace("  ", " ")
            .trim()

        val isRecognized = trackingNumber.isNotBlank() || recipientName.isNotBlank() || fullAddress.isNotBlank()

        return OcrResult(
            rawText = text,
            trackingNumber = trackingNumber,
            recipientName = recipientName,
            address = fullAddress,
            courierCompany = detectedCourier,
            confidence = if (trackingNumber.isNotBlank() && recipientName.isNotBlank() && fullAddress.isNotBlank()) 0.98f else 0.85f,
            isRecognized = isRecognized
        )
    }

    /**
     * Cleans tracking candidate: removes punctuation, stray colons, brackets, spaces.
     */
    private fun cleanTrackingCandidate(raw: String): String {
        return raw.trim()
            .replace(":", "")
            .replace("#", "")
            .replace("*", "")
            .replace("\"", "")
            .replace("'", "")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "")
            .replace("]", "")
            .trim { it <= ' ' || it == '-' || it == '.' || it == '/' || it == ',' }
            .uppercase()
    }

    /**
     * Strict validation for tracking number:
     * - Length 7 to 30 chars
     * - Only uppercase alphanumeric and hyphens
     * - Contains digits (or is recognized format)
     * - NOT a phone number
     * - NOT a postal code
     * - NOT a date or price or common UI word
     */
    private fun isValidTrackingNumber(candidate: String): Boolean {
        if (candidate.length !in 7..30) return false
        if (!candidate.matches(Regex("^[A-Z0-9\\-]+$"))) return false

        // Filter out Indonesian phone numbers: e.g. 081234567890, 6281234567890
        if (candidate.matches(Regex("^(?:08|628|\\+628)[0-9]{8,12}$"))) return false

        // Filter out 5-digit postal code
        if (candidate.matches(Regex("^[0-9]{5}$"))) return false

        // Filter out dates (e.g. 20240910, 10092024)
        if (candidate.matches(Regex("^(?:202[0-9])(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])$"))) return false

        // Filter out words that are pure text or dictionary terms
        val excludedWords = listOf(
            "STANDARD", "REGULER", "EXPRESS", "CARGO", "INDONESIA", "PENERIMA",
            "PENGIRIM", "KECAMATAN", "KELURAHAN", "KABUPATEN", "BANDUNG", "JAKARTA",
            "SURABAYA", "ONGKIR", "FRAGILE", "SHOPEE", "TOKOPEDIA", "BUKALAPAK",
            "LAZADA", "TIKTOK", "PAKET", "BARANG", "DELIVERY", "NON-COD", "RETURN"
        )
        if (excludedWords.contains(candidate)) return false

        // Must contain at least some digits (courier tracking numbers invariably have digits)
        val digitCount = candidate.count { it.isDigit() }
        if (digitCount < 3) return false

        return true
    }

    /**
     * Cleans recipient name by removing phone numbers, WA tags, etc.
     */
    private fun cleanRecipientName(raw: String): String {
        return raw
            .replace(Regex("\\(?\\+?[0-9\\-\\s]{9,15}\\)?"), "")
            .replace(Regex("(?i)(?:telp|tlp|hp|wa|phone)\\s*[:#]?\\s*[0-9\\s\\-]+"), "")
            .replace(Regex("[,:;\\*\\-_/|]"), " ")
            .trim()
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * Checks if word is common courier UI or system label
     */
    private fun isSystemOrCourierWord(word: String): Boolean {
        val lower = word.lowercase()
        return lower.contains("duo galing") ||
            lower.contains("ekspedisi") ||
            lower.contains("express") ||
            lower.contains("standard") ||
            lower.contains("reguler") ||
            lower.contains("shopee") ||
            lower.contains("j&t") ||
            lower.contains("jne") ||
            lower.contains("sicepat") ||
            lower.contains("anteraja") ||
            lower.contains("alamat") ||
            lower.contains("pengirim") ||
            lower.contains("paket") ||
            lower.contains("ongkir")
    }

    /**
     * Automatically identifies the courier / ekspedisi company
     */
    fun detectCourier(text: String, trackingNumber: String): String {
        val upperText = text.uppercase()
        val upperTracking = trackingNumber.uppercase()

        return when {
            upperTracking.startsWith("SPX") || upperText.contains("SPX") || upperText.contains("SHOPEE") -> "Shopee Xpress (SPX)"
            upperTracking.startsWith("JP") || upperTracking.startsWith("JX") || upperTracking.startsWith("JT") ||
                upperTracking.startsWith("JZ") || upperTracking.startsWith("JS") || upperTracking.startsWith("JD") ||
                upperTracking.startsWith("888") || upperText.contains("J&T") || upperText.contains("JNT") -> "J&T Express"
            upperTracking.startsWith("JNE") || upperTracking.startsWith("TLG") || upperTracking.startsWith("BDG") ||
                upperTracking.startsWith("SOC") || upperTracking.startsWith("CGK") || upperTracking.startsWith("SUB") ||
                upperText.contains("JNE") -> "JNE Express"
            upperTracking.startsWith("00") || upperTracking.startsWith("TKP") || upperText.contains("SICEPAT") -> "SiCepat Ekspres"
            upperTracking.startsWith("100") || upperText.contains("ANTERAJA") -> "Anteraja"
            upperTracking.startsWith("NLID") || upperTracking.startsWith("NVID") || upperTracking.startsWith("SHP") ||
                upperText.contains("NINJA") -> "Ninja Xpress"
            upperTracking.startsWith("LP") || upperTracking.startsWith("99") || upperText.contains("LION PARCEL") -> "Lion Parcel"
            upperTracking.startsWith("POS") || (upperTracking.startsWith("P") && upperTracking.length >= 11) ||
                upperText.contains("POS INDONESIA") -> "POS Indonesia"
            upperTracking.startsWith("LXRP") || upperText.contains("LAZADA") || upperText.contains("LEX") -> "Lazada Logistics"
            upperTracking.startsWith("TTID") || upperText.contains("TIKTOK") -> "TikTok Shop"
            upperTracking.startsWith("WAHANA") || upperText.contains("WAHANA") -> "Wahana Express"
            upperTracking.startsWith("DG-") || upperText.contains("DUO GALING") -> "Duo Galing Express"
            else -> "Ekspedisi Reguler"
        }
    }

    /**
     * Preset sample labels for instant, reliable testing in emulators
     */
    data class PresetLabel(
        val title: String,
        val courierCompany: String,
        val trackingNumber: String,
        val recipientName: String,
        val address: String,
        val notes: String
    )

    val PRESET_LABELS = listOf(
        PresetLabel(
            title = "Paket Shopee Xpress (SPX)",
            courierCompany = "Shopee Xpress (SPX)",
            trackingNumber = "SPXID048293849182",
            recipientName = "Dani Ramdani",
            address = "Jl. Raya Cileunyi No. 45, RT 02/RW 03, Cileunyi Wetan, Kab. Bandung 40622",
            notes = "Barang pesanan Shopee, titip di pos satpam jika tidak di tempat"
        ),
        PresetLabel(
            title = "Paket J&T Express",
            courierCompany = "J&T Express",
            trackingNumber = "JX0928374619",
            recipientName = "Ahmad Hidayat",
            address = "Jl. Soekarno Hatta No. 590, Sekejati, Buahbatu, Kota Bandung 40286",
            notes = "J&T EZ, hubungi via WA sebelum sampai"
        ),
        PresetLabel(
            title = "Paket JNE Express",
            courierCompany = "JNE Express",
            trackingNumber = "0123456789012345",
            recipientName = "Budi Santoso",
            address = "Jl. Sukamaju No. 25, RT 03/RW 05, Kel. Pasteur, Kec. Sukajadi, Kota Bandung 40161",
            notes = "JNE Reguler, rumah cat hijau samping fotokopi"
        ),
        PresetLabel(
            title = "Paket SiCepat Ekspres",
            courierCompany = "SiCepat Ekspres",
            trackingNumber = "002938172635",
            recipientName = "Dewi Lestari",
            address = "Jl. Dago Asri I No. 18, Dago, Kec. Coblong, Kota Bandung 40135",
            notes = "SiCepat HALU, barang pecah belah (Fragile)"
        ),
        PresetLabel(
            title = "Paket Anteraja",
            courierCompany = "Anteraja",
            trackingNumber = "10002938471928",
            recipientName = "Siti Nurhaliza",
            address = "Jl. Setiabudi No. 182, Isola, Kec. Sukasari, Kota Bandung 40154",
            notes = "Lantai 2 Kost Griya Setiabudi"
        ),
        PresetLabel(
            title = "Paket Duo Galing Express",
            courierCompany = "Duo Galing Express",
            trackingNumber = "DG-991823746190",
            recipientName = "Rian Pratama",
            address = "Jl. Riau No. 88, Cihapit, Kec. Bandung Wetan, Kota Bandung 40114",
            notes = "Paket internal Duo Galing Express"
        )
    )
}
