package me.weishu.kernelsu.core.tasks

import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BootKernelVersionTest {
    @Test fun researchedCanaryVariants() {
        val directory = System.getenv("KSU_CANARY_FIXTURE_DIR")
        assumeTrue("Set KSU_CANARY_FIXTURE_DIR for real-image regression", directory != null)
        val four = BootKernelVersion.inspectKernel(java.io.File(directory, "boot-kernel.bin").readBytes())!!
        val sixteen = BootKernelVersion.inspectKernel(java.io.File(directory, "kernel-16k.bin").readBytes())!!
        assertEquals("6.1.172-android14-11-g3fc92ed75e44-ab16169542", four.release)
        assertEquals(four.release, sixteen.release)
        assertEquals(four.kmi, sixteen.kmi)
        assertEquals(4096, four.pageSizeBytes)
        assertEquals(16384, sixteen.pageSizeBytes)
    }
    private fun image(pageFlag: Long): ByteArray {
        val bytes = ByteArray(64) + "Linux version 6.1.172-android14-11-test (clang)\u0000".toByteArray()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(24, pageFlag)
        byteArrayOf(0x41, 0x52, 0x4d, 0x64).copyInto(bytes, 56)
        return bytes
    }

    private fun legacy(bytes: ByteArray): ByteArray {
        val block = ByteArrayOutputStream()
        block.write(0xf0)
        var extra = bytes.size - 15
        while (extra >= 255) { block.write(255); extra -= 255 }
        block.write(extra)
        block.write(bytes)
        return byteArrayOf(2, 0x21, 0x4c, 0x18) +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(block.size()).array() + block.toByteArray()
    }

    @Test fun sameReleaseMustDistinguishKernelPages() {
        val four = BootKernelVersion.inspectKernel(image(10))!!
        val sixteen = BootKernelVersion.inspectKernel(image(12))!!
        assertEquals(four.release, sixteen.release)
        assertEquals(four.kmi, sixteen.kmi)
        assertEquals(4096, four.pageSizeBytes)
        assertEquals(16384, sixteen.pageSizeBytes)
        assertNull(BootKernelVersion.inspectKernel(image(0)))
    }

    @Test fun legacyLz4AndTruncation() {
        val raw = image(12)
        val compressed = legacy(raw)
        assertEquals(BootKernelVersion.inspectKernel(raw), BootKernelVersion.inspectKernel(compressed))
        for (end in 0 until compressed.size) assertNull(BootKernelVersion.inspectKernel(compressed.copyOf(end)))
        assertNull(BootKernelVersion.inspectKernel(byteArrayOf(2, 0x21, 0x4c, 0x18, -1, -1, -1, 127)))
    }

    @Test fun bootHeaderAlignmentDoesNotSetKernelPages() {
        val kernel = legacy(image(12))
        val header = ByteArray(4096)
        "ANDROID!".toByteArray().copyInto(header)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(8, kernel.size).putInt(40, 4)
        val file = kotlin.io.path.createTempFile().toFile()
        try {
            file.writeBytes(header + kernel)
            assertEquals(16384, BootKernelVersion.parseTargetFromBoot(file)!!.pageSizeBytes)
            assertEquals("android14-6.1", BootKernelVersion.parseKmiFromBoot(file))
            file.writeBytes(header + kernel.dropLast(1).toByteArray())
            assertNull(BootKernelVersion.parseTargetFromBoot(file))
        } finally { file.delete() }
    }

    @Test fun compressedBannerAndOversizedKernelCannotPass() {
        assertNull(BootKernelVersion.inspectKernel("Linux version 6.1.172-android14-11".toByteArray()))
        val header = ByteArray(64)
        "ANDROID!".toByteArray().copyInto(header)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).putInt(8, Int.MAX_VALUE).putInt(40, 4)
        assertNull(BootKernelVersion.bootKernelBlock(header))
    }
}
