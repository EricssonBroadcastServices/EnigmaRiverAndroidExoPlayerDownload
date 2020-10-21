package com.redbeemedia.enigma.exoplayerdownload;

import android.util.Base64;
import android.util.Pair;

import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;

import org.json.JSONException;
import org.json.JSONObject;

/*package-protected*/ class DrmLicenceInfo extends BaseJsonSerializable {
    private static final int SAVE_FORMAT_VERSION = 1;

    private static final String DRM_KEY = "DRM_KEY";
    private static final String EXPIRATION_TIME = "EXPIRATION_TIME";

    private final String drmKey;
    private final long expirationTime;

    public DrmLicenceInfo(byte[] drmKey, long expirationTime) {
        this(Base64.encodeToString(drmKey, Base64.DEFAULT), expirationTime);
    }

    private DrmLicenceInfo(String drmKey, long expirationTime) {
        if(drmKey == null) {
            throw new NullPointerException("drmKey must not be null");
        }
        this.drmKey = drmKey;
        this.expirationTime = expirationTime;
    }

    @Override
    protected int storeInJson(JSONObject jsonObject) throws JSONException {
        jsonObject.put(DRM_KEY, drmKey);
        jsonObject.put(EXPIRATION_TIME, expirationTime);
        return SAVE_FORMAT_VERSION;
    }

    public static DrmLicenceInfo fromBytes(byte[] data) {
        return BaseJsonSerializable.fromBytes(data, DrmLicenceInfo.class, (saveFormatVersion, jsonObject) -> {
            if(saveFormatVersion == 1) {
                long expirationTime = jsonObject.getLong(EXPIRATION_TIME);
                String drmKey = jsonObject.getString(DRM_KEY);
                return new DrmLicenceInfo(drmKey, expirationTime);
            } else {
                throw new IllegalArgumentException("Unknown download meta data save format: "+saveFormatVersion);
            }
        });
    }

    public byte[] getDrmKey() {
        return Base64.decode(drmKey, Base64.DEFAULT);
    }

    //TODO need to define what format this is in
    public long getExpirationTime() {
        return expirationTime;
    }

    public static DrmLicenceInfo create(byte[] offlineLicenseKeySetId, OfflineLicenseHelper<?> offlineLicenseHelper) throws DrmSession.DrmSessionException {
        Pair<Long, Long> remainingSec = offlineLicenseHelper.getLicenseDurationRemainingSec(offlineLicenseKeySetId);
        long expirationTime = Math.min(remainingSec.first, remainingSec.second);
        return new DrmLicenceInfo(offlineLicenseKeySetId, expirationTime);
    }

    public static DrmLicenceInfo decodeFromString(String base64String) {
        if(base64String == null) {
            return null;
        }
        return fromBytes(Base64.decode(base64String, Base64.DEFAULT));
    }
}
