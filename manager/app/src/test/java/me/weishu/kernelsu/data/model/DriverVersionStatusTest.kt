package me.weishu.kernelsu.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverVersionStatusTest {
    @Test fun independentBuildNumbersWithCompatibleInterfaceAreNotAnError() {
        assertEquals(DriverVersionStatus.BuildDifferent, driverVersionStatus(32639, 32631, 2, 2, false))
        assertEquals(DriverVersionStatus.Matched, driverVersionStatus(32631, 32631, 2, 2, false))
    }

    @Test fun realInterfaceOrMinimumDriverFailuresRemainBlocked() {
        assertEquals(DriverVersionStatus.Incompatible, driverVersionStatus(32639, 32631, 3, 2, false))
        assertEquals(DriverVersionStatus.Incompatible, driverVersionStatus(32639, 32512, 2, 2, true))
        assertEquals(DriverVersionStatus.Unknown, driverVersionStatus(32639, 32631, 2, null, false))
        assertEquals(DriverVersionStatus.Unknown, driverVersionStatus(32639, 32631, 2, 0, false))
        assertEquals(DriverVersionStatus.Unavailable, driverVersionStatus(32639, null, 2, null, false))
    }
}
