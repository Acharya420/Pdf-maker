package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ImageItem
import com.example.data.model.ImageLayoutMode
import com.example.data.model.MarginPreset
import com.example.data.model.PageOrientation
import com.example.data.model.PageOrganizeItem
import com.example.data.model.PageSizePreset
import com.example.data.model.PdfCreationConfig
import com.example.data.model.PdfQualityPreset
import com.example.data.model.SplitMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfMakerEngineTest {

    private lateinit var context: Context
    private lateinit var vaultManager: PdfVaultManager
    private lateinit var imageToPdfEngine: ImageToPdfEngine
    private lateinit var pdfToolsEngine: PdfToolsEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
        vaultManager = PdfVaultManager(context)
        imageToPdfEngine = ImageToPdfEngine(context)
        pdfToolsEngine = PdfToolsEngine(context, vaultManager)
    }

    private fun createSampleImageFile(name: String, color: Int): File {
        val file = File(context.cacheDir, name)
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        return file
    }

    @Test
    fun imageToPdf_merge_split_rotate_organize_protect_endToEnd() = runBlocking {
        val img1 = createSampleImageFile("test_img_1.jpg", android.graphics.Color.BLUE)
        val img2 = createSampleImageFile("test_img_2.jpg", android.graphics.Color.MAGENTA)

        val items = listOf(
            ImageItem(uriString = Uri.fromFile(img1).toString(), displayName = "img1.jpg", rotationDegrees = 0),
            ImageItem(uriString = Uri.fromFile(img2).toString(), displayName = "img2.jpg", rotationDegrees = 90)
        )
        val config = PdfCreationConfig(
            fileName = "UnitTestDoc",
            pageSizePreset = PageSizePreset.A4,
            orientation = PageOrientation.PORTRAIT,
            marginPreset = MarginPreset.MEDIUM,
            imageLayoutMode = ImageLayoutMode.FIT_PAGE,
            qualityPreset = PdfQualityPreset.MEDIUM
        )

        val estimated = imageToPdfEngine.estimateOutputSizeBytes(items, config)
        assertTrue("Estimated size should be positive", estimated > 0L)

        val outPdf1 = vaultManager.createUniquePdfFile("UnitTestDoc")
        val genRes = imageToPdfEngine.generatePdf(items, config, outPdf1) {}
        assertTrue("PDF generation failed: ${genRes.exceptionOrNull()}", genRes.isSuccess)
        assertTrue(outPdf1.exists() && outPdf1.length() > 500L)

        // Inspect metadata
        val metaRes = pdfToolsEngine.inspectPdf(Uri.fromFile(outPdf1))
        assertTrue(metaRes.isSuccess)
        val meta = metaRes.getOrThrow()
        assertEquals(2, meta.pageCount)
        assertFalse(meta.isEncrypted)

        // Split into individual pages
        val splitRes = pdfToolsEngine.splitPdf(Uri.fromFile(outPdf1), null, SplitMode.ExtractAllPages, "SplitTest")
        assertTrue("Split failed: ${splitRes.exceptionOrNull()}", splitRes.isSuccess)
        val splitFiles = splitRes.getOrThrow()
        assertEquals(2, splitFiles.size)
        assertEquals(1, splitFiles[0].second)

        // Merge back together
        val mergedFile = vaultManager.createUniquePdfFile("MergedTest")
        val mergeRes = pdfToolsEngine.mergePdfs(
            listOf(Uri.fromFile(splitFiles[0].first), Uri.fromFile(splitFiles[1].first)),
            emptyMap(),
            mergedFile
        )
        assertTrue("Merge failed: ${mergeRes.exceptionOrNull()}", mergeRes.isSuccess)
        assertEquals(2, mergeRes.getOrThrow().second)

        // Organize pages (reverse & rotate first)
        val organizedFile = vaultManager.createUniquePdfFile("OrganizedTest")
        val organizeRes = pdfToolsEngine.organizePages(
            Uri.fromFile(mergedFile),
            null,
            listOf(
                PageOrganizeItem(originalPageIndex = 1, rotationDegrees = 90, isDeleted = false),
                PageOrganizeItem(originalPageIndex = 0, rotationDegrees = 0, isDeleted = true)
            ),
            organizedFile
        )
        assertTrue("Organize failed: ${organizeRes.exceptionOrNull()}", organizeRes.isSuccess)
        assertEquals(1, organizeRes.getOrThrow().second)

        // Password protect and verify unlock
        val lockedFile = vaultManager.createUniquePdfFile("LockedTest")
        val lockRes = pdfToolsEngine.protectOrUnlockPdf(Uri.fromFile(organizedFile), null, "secret123", lockedFile)
        assertTrue("Protect failed: ${lockRes.exceptionOrNull()}", lockRes.isSuccess)

        val lockedFail = pdfToolsEngine.inspectPdf(Uri.fromFile(lockedFile), null)
        assertTrue("Opening locked PDF without password should fail", lockedFail.isFailure)

        val lockedOk = pdfToolsEngine.inspectPdf(Uri.fromFile(lockedFile), "secret123")
        assertTrue("Opening locked PDF with password should succeed", lockedOk.isSuccess)
        assertTrue(lockedOk.getOrThrow().isEncrypted)

        // Rotate PDF by 90 degrees and verify
        val rotatedFile = vaultManager.createUniquePdfFile("RotatedTest")
        val rotateRes = pdfToolsEngine.rotatePdf(Uri.fromFile(mergedFile), null, 90, rotatedFile)
        assertTrue("Rotate failed: ${rotateRes.exceptionOrNull()}", rotateRes.isSuccess)
        assertEquals(2, rotateRes.getOrThrow().second)
    }

    @Test
    fun imageSelection_reordering_customPageSize_landscape_and_shareIntent() = runBlocking {
        val imgA = createSampleImageFile("order_a.jpg", android.graphics.Color.RED)
        val imgB = createSampleImageFile("order_b.jpg", android.graphics.Color.GREEN)

        val vm = com.example.viewmodel.ImageToPdfViewModel(imageToPdfEngine, vaultManager)
        vm.addImages(listOf(Uri.fromFile(imgA), Uri.fromFile(imgB)))
        assertEquals(2, vm.images.value.size)
        assertEquals("order_a.jpg", vm.images.value[0].displayName)

        // Reorder: swap index 0 and 1
        vm.moveImage(0, 1)
        assertEquals("order_b.jpg", vm.images.value[0].displayName)
        assertEquals("order_a.jpg", vm.images.value[1].displayName)

        // Update page size, orientation, compression target & custom DPI
        vm.updateConfig {
            it.copy(
                fileName = "LandscapeCustom",
                pageSizePreset = PageSizePreset.CUSTOM,
                customWidthMm = 200f,
                customHeightMm = 140f,
                orientation = PageOrientation.LANDSCAPE,
                qualityPreset = PdfQualityPreset.CUSTOM_DPI,
                customDpi = 120,
                compressionTarget = com.example.data.model.CompressionTarget.TARGET_KB,
                targetFileSizeKb = 150
            )
        }

        val outFile = vaultManager.createUniquePdfFile(vm.config.value.fileName)
        val genRes = imageToPdfEngine.generatePdf(vm.images.value, vm.config.value, outFile) {}
        assertTrue("Custom landscape PDF failed: ${genRes.exceptionOrNull()}", genRes.isSuccess)
        assertTrue(outFile.exists() && outFile.length() > 500L)

        // Verify landscape dimensions via inspectPdf (width > height)
        val meta = pdfToolsEngine.inspectPdf(Uri.fromFile(outFile)).getOrThrow()
        assertTrue("Expected landscape width > height", meta.firstPageWidthPt > meta.firstPageHeightPt)

        // Verify share intent builds cleanly via FileProvider
        val shareIntent = vaultManager.createSharePdfIntent(outFile)
        assertEquals(android.content.Intent.ACTION_SEND, shareIntent.action)
        assertEquals("application/pdf", shareIntent.type)
        assertTrue(shareIntent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM) != null)
    }
}
