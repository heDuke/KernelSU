package me.weishu.kernelsu.core.utils

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BootInputInspectionTest {
    @Test fun headerAlignmentDoesNotClaimKernelPageSize() {
        val image = ByteArray(8192)
        "ANDROID!".toByteArray().copyInto(image)
        val bytes = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)
        bytes.putInt(8, 4096); bytes.putInt(20, 1584); bytes.putInt(40, 4)
        val report = inspectBootInput(image)
        assertEquals(4096, report.imagePageSize)
        assertEquals("boot", report.partition)
        assertNull(report.kernelPageSize)
        assertNull(report.release)
        assertThrows(IllegalArgumentException::class.java) { inspectBootInput(image, 4096) }
        bytes.putInt(40, 99)
        assertThrows(IllegalArgumentException::class.java) { inspectBootInput(image) }
        assertThrows(IllegalArgumentException::class.java) { inspectBootInput(ByteArray(43)) }
    }
    @Test fun initBootIdentificationDoesNotInventItsCompanionKernelTarget() {
        val image = ByteArray(8192)
        "ANDROID!".toByteArray().copyInto(image)
        val header = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(12, 4096); header.putInt(20, 1584); header.putInt(40, 4)
        val inspection = inspectBootInput(image)
        assertEquals("init_boot", inspection.partition)
        assertNull(inspection.release)
        assertNull(inspection.kernelPageSize)
    }
    @Test fun actualModuleMetadataAndWrongArchitectureAreChecked() {
        val file = System.getenv("HUSKY_LKM_FIXTURE")?.let(::File)
        assumeTrue("Set HUSKY_LKM_FIXTURE to the actual candidate", file?.isFile == true)
        val bytes = file!!.readBytes()
        val report = inspectBootInput(bytes)
        assertEquals("ELF64 ARM64 LKM", report.format)
        assertEquals("6.1.172-android14-11-g3fc92ed75e44-ab16169542", report.release)
        assertEquals(4096, report.kernelPageSize)
        bytes[18] = 62
        assertThrows(IllegalArgumentException::class.java) { inspectBootInput(bytes) }
    }
}
