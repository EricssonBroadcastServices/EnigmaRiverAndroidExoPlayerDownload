package com.redbeemedia.enigma.exoplayerdownload;

import android.net.Uri;

import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.drm.DrmInfoFactory;
import com.redbeemedia.enigma.core.drm.IDrmInfo;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.error.InternalError;
import com.redbeemedia.enigma.core.error.MaxDownloadCountLimitReachedError;
import com.redbeemedia.enigma.core.error.NoSupportedMediaFormatsError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.error.UnexpectedHttpStatusError;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.http.AuthenticatedExposureApiCall;
import com.redbeemedia.enigma.core.http.ExposureHttpError;
import com.redbeemedia.enigma.core.http.HttpStatus;
import com.redbeemedia.enigma.core.http.IHttpHandler;
import com.redbeemedia.enigma.core.json.JsonObjectResponseHandler;
import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.UrlPath;
import com.redbeemedia.enigma.core.util.error.EnigmaErrorException;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.resulthandler.BaseResultHandler;
import com.redbeemedia.enigma.download.resulthandler.IDrmLicenceRenewResultHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/*package-protected*/ class RenewWidevineLicenceProcedure {
    private final ISession session;
    private final String contentId;
    private final DownloadedAssetMetaData metaData;
    private final IDrmLicenceRenewResultHandler resultHandler;

    public RenewWidevineLicenceProcedure(ISession session, String contentId, DownloadedAssetMetaData metaData, IDrmLicenceRenewResultHandler resultHandler) {
        this.session = session;
        this.contentId = contentId;
        this.metaData = metaData;
        this.resultHandler = resultHandler;
    }

    public void begin() {
        try {
            String assetId = metaData.getAssetId();
            UrlPath endpoint = session.getBusinessUnit().getApiBaseUrl("v2").append("/entitlement/").append(assetId).append("/download");
            EnigmaRiverContext.getHttpHandler().doHttp(endpoint.toURL(), new AuthenticatedExposureApiCall("GET", session), new JsonObjectResponseHandler() {
                @Override
                protected void onSuccess(JSONObject jsonObject) throws JSONException {
                    try {
                        onDownloadEntitlement(jsonObject);
                    } catch (ProcedureException e) {
                        resultHandler.onError(e.error);
                    } catch (Exception e) {
                        resultHandler.onError(new UnexpectedError(e));
                    }
                }

                @Override
                public void onResponse(HttpStatus status, InputStream inputStream) {
                    if(status.getResponseCode() == 403) {
                        try {
                            ExposureHttpError exposureHttpError = ExposureHttpError.parse(inputStream);
                            if("MAX_DOWNLOAD_COUNT_LIMIT_REACHED".equals(exposureHttpError.getMessage())) {
                                resultHandler.onError(new MaxDownloadCountLimitReachedError(assetId));
                                return;
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    super.onResponse(status, inputStream);
                }

                @Override
                protected void onError(EnigmaError error) {
                    resultHandler.onError(error);
                }
            });
        } catch (Exception e) {
            resultHandler.onError(new UnexpectedError(e));
        }
    }

    private void onDownloadEntitlement(JSONObject jsonObject) throws JSONException, ProcedureException {
        String requestId = jsonObject.optString("requestId");
        String playToken = jsonObject.optString("playToken");

        JSONArray formats = jsonObject.getJSONArray("formats");

        EnigmaMediaFormat expectedFormat = EnigmaMediaFormat.DASH().widevine();
        JSONObject format = selectMatchingFormatFromJson(formats, expectedFormat);
        if(format == null) {
            throw new ProcedureException(new NoSupportedMediaFormatsError("Could not find "+expectedFormat+" format found in response."));
        }

        Uri mediaUri = Uri.parse(format.getString("mediaLocator"));

        JSONObject widevineJson = format.getJSONObject("drm").getJSONObject(EnigmaMediaFormat.DrmTechnology.WIDEVINE.getKey());
        String licenseServerUrl = widevineJson.getString("licenseServerUrl");

        renewWidevineLicense(mediaUri, licenseServerUrl, playToken, requestId);
    }

    private JSONObject selectMatchingFormatFromJson(JSONArray formats, EnigmaMediaFormat requiredFormat) throws JSONException {
        for(int i = 0; i < formats.length(); ++i) {
            JSONObject mediaFormat = formats.getJSONObject(i);
            EnigmaMediaFormat parsedFormat = EnigmaMediaFormat.parseMediaFormat(mediaFormat);
            if(requiredFormat.equals(parsedFormat)) {
                return mediaFormat;
            }
        }
        return null; //No match
    }

    public void renewWidevineLicense(Uri mediaUri, String licenseServerUri, String playToken, String requestId) throws ProcedureException {
        try {
            WidevineHelper.getManifest(mediaUri.toString(), new BaseResultHandler<Document>() {
                @Override
                public void onResult(Document document) {
                    try {
                        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
                        factory.setUserAgent("license_renewer");
                        HttpDataSource.Factory dataSourceFactory = factory;
                        IDrmInfo drmInfo = DrmInfoFactory.createWidevineDrmInfo(licenseServerUri, playToken, requestId);

                        HashMap<String, String> optional = new HashMap<>();
                        for(Map.Entry<String, String> entry : drmInfo.getDrmKeyRequestProperties()) {
                            optional.put(entry.getKey(), entry.getValue());
                        }
                        OfflineLicenseHelper offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(
                                licenseServerUri,
                                false,
                                dataSourceFactory, new DrmSessionEventListener.EventDispatcher());
                        try {
                            renewLicence(offlineLicenseHelper);
                        } finally {
                            offlineLicenseHelper.release();
                        }
                        fireAndForgetDownloadRenewed();
                        resultHandler.onSuccess();
                    } catch (ProcedureException e) {
                        resultHandler.onError(e.error);
                    } catch (Exception e) {
                        resultHandler.onError(new UnexpectedError(e));
                    }
                }

                @Override
                public void onError(EnigmaError error) {
                    resultHandler.onError(error);
                }
            });
        } catch (MalformedURLException e) {
            throw new ProcedureException(new InternalError("Could not parse mediaLocator: "+mediaUri.toString()));
        }
    }

    private void fireAndForgetDownloadRenewed() {
        try {
            URL endpoint = session.getBusinessUnit().getApiBaseUrl("v2")
                                .append("/entitlement/")
                                .append(metaData.getAssetId())
                                .append("/downloadrenewed")
                                .toURL();
            EnigmaRiverContext.getHttpHandler().doHttp(endpoint, new AuthenticatedExposureApiCall("POST", session), new IHttpHandler.IHttpResponseHandler() {
                @Override
                public void onResponse(HttpStatus httpStatus) {
                    if(httpStatus.isError()) {
                        onException(new EnigmaErrorException(new UnexpectedHttpStatusError(httpStatus)));
                    }
                }

                @Override
                public void onResponse(HttpStatus httpStatus, InputStream inputStream) {
                    onResponse(httpStatus);
                }

                @Override
                public void onException(Exception e) {
                    e.printStackTrace();
                }
            });
        } catch (Exception e) {
            //Call was made as 'fire-and-forget'
            e.printStackTrace();
        }
    }

    private void renewLicence(OfflineLicenseHelper offlineLicenseHelper) throws ProcedureException, DrmSession.DrmSessionException {
        DrmLicenceInfo drmLicenceInfo = metaData.getDrmLicenceInfo();
        if(drmLicenceInfo == null) {
            throw new ProcedureException(new InternalError("Missing existing licence, can not renew."));
        }
        byte[] newLicenceData = offlineLicenseHelper.renewLicense(drmLicenceInfo.getDrmKey());
        metaData.setDrmLicenceInfo(DrmLicenceInfo.create(newLicenceData, offlineLicenseHelper));

        //Save updated metaData
        EnigmaDownloadContext.getMetadataManager().store(contentId, metaData.getBytes());
    }
}
