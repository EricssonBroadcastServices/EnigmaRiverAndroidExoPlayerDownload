package com.redbeemedia.enigma.exoplayerdownload;

import android.util.Base64;

import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.session.Session;

import org.json.JSONException;
import org.json.JSONObject;

/*package-protected*/ class DownloadedAssetMetaData extends BaseJsonSerializable {
    private static final int SAVE_FORMAT_VERSION = 3;

    private static final String ASSET_ID = "ASSET_ID";
    private static final String DRM_KEY = "DRM_KEY";
    private static final String SESSION = "SESSION";
    private static final String EXPIRATION_TIME = "EXPIRATION_TIME";
    private static final String DRM_LICENCE_INFO = "DRM_LICENCE_INFO";

    private final String assetId;
    private final ISession session;
    private DrmLicenceInfo drmLicenceInfo;
    private long playTokenExpiration;
    private Long fileSize = 0L;

    public DownloadedAssetMetaData(String assetId, DrmLicenceInfo drmLicenceInfo, ISession session, long playTokenExpiration) {
        this.assetId = assetId;
        this.drmLicenceInfo = drmLicenceInfo;
        this.session = session;
        this.playTokenExpiration = playTokenExpiration;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Long getFileSize() {
        return fileSize;
    }

    @Override
    protected int storeInJson(JSONObject jsonObject) throws JSONException {
        jsonObject.put(ASSET_ID, assetId);
        jsonObject.put(DRM_LICENCE_INFO, DrmLicenceInfo.encodeToString(drmLicenceInfo));
        jsonObject.put(SESSION, BaseJsonSerializable.encodeToString(session));
        jsonObject.put(EXPIRATION_TIME, playTokenExpiration);
        return SAVE_FORMAT_VERSION;
    }

    public static DownloadedAssetMetaData fromBytes(byte[] data) {
        return BaseJsonSerializable.fromBytes(data, DownloadedAssetMetaData.class, (saveFormatVersion, jsonObject) -> {
            long playTokenExpiration = jsonObject.optLong(EXPIRATION_TIME,0);
            if(saveFormatVersion == 1) {
                String assetId = jsonObject.getString(ASSET_ID);
                String drmKey = jsonObject.optString(DRM_KEY, null);
                DrmLicenceInfo drmLicenceInfo = null;
                if(drmKey != null) {
                    drmLicenceInfo = new DrmLicenceInfo(Base64.decode(drmKey, Base64.DEFAULT), Long.MAX_VALUE);
                }
                if (playTokenExpiration > 0 && drmLicenceInfo != null) {
                    drmLicenceInfo.setExpirationTime(playTokenExpiration);
                }
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, null, playTokenExpiration);
            } else if(saveFormatVersion == 2) {
                String assetId = jsonObject.getString(ASSET_ID);
                DrmLicenceInfo drmLicenceInfo = DrmLicenceInfo.decodeFromString(jsonObject.optString(DRM_LICENCE_INFO, null));
                if (playTokenExpiration > 0 && drmLicenceInfo != null) {
                    drmLicenceInfo.setExpirationTime(playTokenExpiration);
                }
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, null, playTokenExpiration);
            } else if(saveFormatVersion == 3) {
                String assetId = jsonObject.getString(ASSET_ID);
                DrmLicenceInfo drmLicenceInfo = DrmLicenceInfo.decodeFromString(jsonObject.optString(DRM_LICENCE_INFO, null));
                if (playTokenExpiration > 0 && drmLicenceInfo != null) {
                    drmLicenceInfo.setExpirationTime(playTokenExpiration);
                }
                ISession session = JsonSerializableUtil.readParcelableFromBase64String(jsonObject.optString(SESSION, null), Session.CREATOR);
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, session, playTokenExpiration);
            } else {
                throw new IllegalArgumentException("Unknown download meta data save format: "+saveFormatVersion);
            }
        });
    }

    public static DownloadedAssetMetaData newDefaultMetadata() {
        return new DownloadedAssetMetaData("N/A", null, null, 0);
    }

    public DrmLicenceInfo getDrmLicenceInfo() {
        return drmLicenceInfo;
    }

    public void setDrmLicenceInfo(DrmLicenceInfo drmLicenceInfo) {
        this.drmLicenceInfo = drmLicenceInfo;
    }

    public ISession getSession() {
        return session;
    }

    public long getPlayTokenExpiration() {
        return playTokenExpiration;
    }
}
