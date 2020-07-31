package com.redbeemedia.enigma.exoplayerdownload;

import android.util.Base64;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/*package-protected*/ class DownloadedAssetMetaData {
    private static final String ASSET_ID = "ASSET_ID";
    private static final String DRM_KEY = "DRM_KEY";

    private final String assetId;
    private String drmKey = null;

    public DownloadedAssetMetaData(String assetId) {
        this.assetId = assetId;
    }

    public String getAssetId() {
        return assetId;
    }

    public byte[] getBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(1); //Save format version
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(ASSET_ID, assetId);
            jsonObject.put(DRM_KEY, drmKey);
            byte[] jsonBytes = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
            baos.write(jsonBytes, 0, jsonBytes.length);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static DownloadedAssetMetaData fromBytes(byte[] data) {
        if(data == null) {
            return null;
        }
        byte saveFormatVersion = data[0];
        if(saveFormatVersion == 1) {
            String jsonData = new String(data, 1, data.length-1, StandardCharsets.UTF_8);
            try {
                JSONObject jsonObject = new JSONObject(jsonData);
                String assetId = jsonObject.getString(ASSET_ID);
                DownloadedAssetMetaData metaData = new DownloadedAssetMetaData(assetId);
                String drmKey = jsonObject.optString(DRM_KEY, null);
                if(drmKey != null) {
                    metaData.drmKey = drmKey;
                }
                return metaData;
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalArgumentException("Unknown download meta data save format: "+saveFormatVersion);
        }
    }

    public static DownloadedAssetMetaData newDefaultMetadata() {
        return new DownloadedAssetMetaData("N/A");
    }

    public void setDrmKey(byte[] drmKey) {
        this.drmKey = Base64.encodeToString(drmKey, Base64.DEFAULT);
    }

    public byte[] getDrmKey() {
        if(drmKey != null) {
            return Base64.decode(drmKey, Base64.DEFAULT);
        }
        return null;
    }
}
