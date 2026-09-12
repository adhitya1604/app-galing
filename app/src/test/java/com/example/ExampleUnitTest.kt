package com.example

import com.example.ocr.OcrExtractor
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testShopeeXpressTrackingDetection() {
        val rawText = """
            SPX Standard
            No. Resi: SPXID048293849182
            Penerima: Dani Ramdani
            081234567890
            Alamat: Jl. Raya Cileunyi No. 45, Cileunyi Wetan, Kab. Bandung
        """.trimIndent()

        val result = OcrExtractor.parseExtractedText(rawText)
        assertEquals("SPXID048293849182", result.trackingNumber)
        assertEquals("Dani Ramdani", result.recipientName)
        assertTrue(result.courierCompany.contains("Shopee Xpress") || result.courierCompany.contains("SPX"))
        assertTrue(result.isRecognized)
    }

    @Test
    fun testJtExpressTrackingDetection() {
        val rawText = """
            J&T Express EZ
            AWB
            JX0928374619
            Kepada: Ahmad Hidayat
            Jl. Soekarno Hatta No. 590, Buahbatu, Kota Bandung
        """.trimIndent()

        val result = OcrExtractor.parseExtractedText(rawText)
        assertEquals("JX0928374619", result.trackingNumber)
        assertEquals("Ahmad Hidayat", result.recipientName)
        assertEquals("J&T Express", result.courierCompany)
        assertTrue(result.isRecognized)
    }

    @Test
    fun testJnePureNumericTrackingDetection() {
        val rawText = """
            JNE REGULER
            No. Resi: 0123456789012345
            Penerima: Budi Santoso
            Alamat: Jl. Sukamaju No. 25, Pasteur, Bandung
        """.trimIndent()

        val result = OcrExtractor.parseExtractedText(rawText)
        assertEquals("0123456789012345", result.trackingNumber)
        assertEquals("Budi Santoso", result.recipientName)
        assertEquals("JNE Express", result.courierCompany)
    }

    @Test
    fun testSiCepatTrackingDetection() {
        val rawText = """
            SICEPAT EKSPRES
            No. Resi: 002938172635
            Penerima: Dewi Lestari
            Alamat: Jl. Dago Asri I No. 18, Dago, Coblong, Kota Bandung
        """.trimIndent()

        val result = OcrExtractor.parseExtractedText(rawText)
        assertEquals("002938172635", result.trackingNumber)
        assertEquals("Dewi Lestari", result.recipientName)
        assertEquals("SiCepat Ekspres", result.courierCompany)
    }

    @Test
    fun testGeneralArbitraryTrackingNumberWithoutForcedPrefix() {
        // Unprefixed tracking number with spaces from OCR
        val rawText = """
            LOGISTIK EKSPRES
            RESI
            KDG 9812 3456 71
            Penerima: Rian Pratama
            Alamat: Jl. Riau No. 88, Cihapit, Bandung
        """.trimIndent()

        val result = OcrExtractor.parseExtractedText(rawText)
        assertEquals("KDG9812345671", result.trackingNumber)
        // Ensure no "DG-" was forcefully prepended!
        assertFalse(result.trackingNumber.startsWith("DG-KDG"))
        assertEquals("Rian Pratama", result.recipientName)
    }
}
