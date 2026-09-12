package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.OcrExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `verify app name resource`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Duo Galing", appName)
  }

  @Test
  fun `verify OCR heuristic parses standard Indonesian shipping label`() {
    val sampleLabelText = """
      JNE EXPRESS
      RESI: JNE882941098273
      PENERIMA: Budi Santoso
      ALAMAT: Jl. Sukamaju No. 25, RT 03/RW 05, Kel. Pasteur, Kec. Sukajadi, Kota Bandung 40161
      PENGIRIM: Toko Sumber Rejeki
    """.trimIndent()

    val result = OcrExtractor.parseExtractedText(sampleLabelText)

    assertTrue(result.isRecognized)
    assertEquals("JNE882941098273", result.trackingNumber)
    assertEquals("Budi Santoso", result.recipientName)
    assertTrue(result.address.contains("Jl. Sukamaju No. 25"))
  }

  @Test
  fun `verify OCR heuristic parses Duo Galing format`() {
    val duoGalingLabel = """
      DUO GALING EXPRESS
      AWB: DG-991823746190
      Kepada: Dewi Lestari
      Tujuan: Jl. Dago Asri I No. 18, Dago, Kec. Coblong, Kota Bandung 40135
    """.trimIndent()

    val result = OcrExtractor.parseExtractedText(duoGalingLabel)

    assertTrue(result.isRecognized)
    assertEquals("DG-991823746190", result.trackingNumber)
    assertEquals("Dewi Lestari", result.recipientName)
    assertTrue(result.address.contains("Dago"))
  }
}
