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
    private static final String PLAY_SESSION_ID = "PLAY_SESSION_ID";
    private static final String CDN_PROVIDER = "CDN_PROVIDER";
    private static final String ANALYTICS_BASE_URL = "ANALYTICS_BASE_URL";
    private static final String EXPIRATION_TIME = "EXPIRATION_TIME";
    private static final String PUBLICATION_END = "PUBLICATION_END";
    private static final String DRM_LICENCE_INFO = "DRM_LICENCE_INFO";
    private static final String DURATION = "DURATION";

    private final String assetId;
    private final ISession session;
    private final String playSessionId;
    private final String baseUrl;
    private final String cdnProvider;
    private final int duration;
    private DrmLicenceInfo drmLicenceInfo;
    private long playTokenExpiration;
    private String publicationEnd;
    private Long fileSize = 0L;

    public DownloadedAssetMetaData(String assetId, DrmLicenceInfo drmLicenceInfo, ISession session, long playTokenExpiration, String publicationEnd, String playSessionId, String baseUrl, String cdnProvider, int duration) {
        this.assetId = assetId;
        this.drmLicenceInfo = drmLicenceInfo;
        this.session = session;
        this.playTokenExpiration = playTokenExpiration;
        this.publicationEnd = publicationEnd;
        this.playSessionId = playSessionId;
        this.baseUrl = baseUrl;
        this.cdnProvider = cdnProvider;
        this.duration = duration;
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
        jsonObject.put(PLAY_SESSION_ID, this.playSessionId);
        jsonObject.put(CDN_PROVIDER, this.cdnProvider);
        jsonObject.put(DURATION, this.duration);
        jsonObject.put(ANALYTICS_BASE_URL, this.baseUrl);
        jsonObject.put(DRM_LICENCE_INFO, DrmLicenceInfo.encodeToString(drmLicenceInfo));
        jsonObject.put(SESSION, BaseJsonSerializable.encodeToString(session));
        jsonObject.put(EXPIRATION_TIME, playTokenExpiration);
        jsonObject.put(PUBLICATION_END, publicationEnd);
        return SAVE_FORMAT_VERSION;
    }

    public static DownloadedAssetMetaData fromBytes(byte[] data) {
        return BaseJsonSerializable.fromBytes(data, DownloadedAssetMetaData.class, (saveFormatVersion, jsonObject) -> {
            long playTokenExpiration = jsonObject.optLong(EXPIRATION_TIME,0);
            String publicationEnd = jsonObject.optString(PUBLICATION_END,"0");
            String playSessionId = jsonObject.optString(PLAY_SESSION_ID);
            String cdnProvider = jsonObject.optString(CDN_PROVIDER);
            int duration = jsonObject.optInt(DURATION,0);
            String analyticsBaseUrl = jsonObject.optString(ANALYTICS_BASE_URL);
            if(playSessionId==null){
                throw new IllegalArgumentException("PLAY_SESSION_ID is missing, Please download the asset again.");
            }

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
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, null, playTokenExpiration, publicationEnd, playSessionId, analyticsBaseUrl,cdnProvider,duration);
            } else if(saveFormatVersion == 2) {
                String assetId = jsonObject.getString(ASSET_ID);
                DrmLicenceInfo drmLicenceInfo = DrmLicenceInfo.decodeFromString(jsonObject.optString(DRM_LICENCE_INFO, null));
                if (playTokenExpiration > 0 && drmLicenceInfo != null) {
                    drmLicenceInfo.setExpirationTime(playTokenExpiration);
                }
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, null, playTokenExpiration,publicationEnd, playSessionId, analyticsBaseUrl,cdnProvider,duration);
            } else if(saveFormatVersion == 3) {
                String assetId = jsonObject.getString(ASSET_ID);
                DrmLicenceInfo drmLicenceInfo = DrmLicenceInfo.decodeFromString(jsonObject.optString(DRM_LICENCE_INFO, null));
                if (playTokenExpiration > 0 && drmLicenceInfo != null) {
                    drmLicenceInfo.setExpirationTime(playTokenExpiration);
                }
                ISession session = JsonSerializableUtil.readParcelableFromBase64String(jsonObject.optString(SESSION, null), Session.CREATOR);
                return new DownloadedAssetMetaData(assetId, drmLicenceInfo, session, playTokenExpiration, publicationEnd, playSessionId, analyticsBaseUrl,cdnProvider,duration);
            } else {
                throw new IllegalArgumentException("Unknown download meta data save format: "+saveFormatVersion);
            }
        });
    }

    public static DownloadedAssetMetaData newDefaultMetadata() {
        return new DownloadedAssetMetaData("N/A", null, null, 0,"", "", "","null",-1);
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

    public String getPlaySessionId() {
        return playSessionId;
    }

    public String getCdnProvider() {
        return cdnProvider;
    }

    public String getAnalyticsBaseUrl() {
        return baseUrl;
    }

    public int getDuration() {
        return duration;
    }

    public String getPublicationEnd() {
        return publicationEnd;
    }
}
