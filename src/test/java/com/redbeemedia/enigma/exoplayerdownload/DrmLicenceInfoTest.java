package com.redbeemedia.enigma.exoplayerdownload;

import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;

public class DrmLicenceInfoTest {
    /**
     * Notes about this test.
     * The purpose of this test is to ensure that the DrmLicenceInfo stored in the old v1 format can still be
     * loaded, even if any new serializations uses a newer format. If this test fails after
     * changing the format, developers have the following options:
     * 1. Fix DrmLicenceInfo so that the test passes
     * 2. If the intended effect is that old stored DrmLicenceInfo should not be recoverable, delete this
     * test.
     */
    @Test
    public void testV1Loadable() {
        byte[] v1Data = Base64.getDecoder().decode("AXsiRFJNX0tFWSI6IkJRb0ZDbU09XG4iLCJFWFBJUkFUSU9OX1RJTUUiOjEyMzQ1Njd9");
        DrmLicenceInfo recoveredLicenceInfo = DrmLicenceInfo.fromBytes(v1Data);
        Assert.assertArrayEquals(new byte[]{5,10,5,10,99}, recoveredLicenceInfo.getDrmKey());
        Assert.assertEquals(1234567L, recoveredLicenceInfo.getExpirationTime());
    }
}
