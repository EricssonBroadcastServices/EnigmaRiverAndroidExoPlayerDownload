package com.redbeemedia.enigma.exoplayerdownload;

import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;

public class DownloadedAssetMetaDataTest {

    /**
     * Notes about this test.
     * The purpose of this test is to ensure that metadata stored in the old v1 format can still be
     * loaded, even if any new serializations uses a newer format. If this test fails after
     * changing the format, developers have the following options:
     * 1. Fix DownloadedAssetMetaData so that the test passes
     * 2. If the intended effect is that old stored metadata should not be recoverable, delete this
     * test.
     */
    @Test
    public void testV1Loadable() {
        { //Test without drm
            //Note that android.util.Base64 is used in DownloadedAssetMetaData, but we can use anything here.
            byte[] v1Data = Base64.getDecoder().decode("AXsiQVNTRVRfSUQiOiJtb2NrQXNzZXRWMSJ9");
            DownloadedAssetMetaData recoveredMetadata = DownloadedAssetMetaData.fromBytes(v1Data);
            Assert.assertEquals("mockAssetV1", recoveredMetadata.getAssetId());
            Assert.assertNull(recoveredMetadata.getDrmKey());
        }

        { //Test with drm
            //Note that android.util.Base64 is used in DownloadedAssetMetaData, but we can use anything here.
            byte[] v1DataWithDrm = Base64.getDecoder().decode("AXsiRFJNX0tFWSI6IkFRSURCQVU9XG4iLCJBU1NFVF9JRCI6ImRybVByb3RlY3RlZEFzc2V0In0=");
            DownloadedAssetMetaData recoveredMetadata = DownloadedAssetMetaData.fromBytes(v1DataWithDrm);
            Assert.assertEquals("drmProtectedAsset", recoveredMetadata.getAssetId());
            Assert.assertArrayEquals(new byte[]{1,2,3,4,5}, recoveredMetadata.getDrmKey());
        }
    }
}
