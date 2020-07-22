package com.redbeemedia.enigma.exoplayerdownload;

import android.net.Uri;

import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.upstream.DataSource;
import com.redbeemedia.enigma.core.businessunit.IBusinessUnit;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.error.InternalError;
import com.redbeemedia.enigma.core.error.NoSupportedMediaFormatsError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.error.UnsupportedMediaFormatError;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.http.AuthenticatedExposureApiCall;
import com.redbeemedia.enigma.core.http.IHttpCall;
import com.redbeemedia.enigma.core.json.JsonObjectResponseHandler;
import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.AndroidThreadUtil;
import com.redbeemedia.enigma.core.util.UrlPath;
import com.redbeemedia.enigma.download.DownloadStartRequest;
import com.redbeemedia.enigma.download.resulthandler.IDownloadStartResultHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;

/*package-protected*/ class StartDownloadProcedure {
    private final IBusinessUnit businessUnit;
    private final ISession session;
    private final DownloadStartRequest request;
    private final IDownloadStartResultHandler resultHandler;

    public StartDownloadProcedure(IBusinessUnit businessUnit, DownloadStartRequest request, IDownloadStartResultHandler resultHandler) {
        this.businessUnit = businessUnit;
        this.session = request.getSession();
        this.request = request;
        this.resultHandler = resultHandler;
    }

    public void begin() {
        UrlPath endpoint = businessUnit.getApiBaseUrl("v2").append("/entitlement/").append(request.getAssetId()).append("/download");
        try {
            IHttpCall call = new AuthenticatedExposureApiCall("GET", session);
            EnigmaRiverContext.getHttpHandler().doHttp(endpoint.toURL(), call, new JsonObjectResponseHandler() {
                @Override
                protected void onSuccess(JSONObject jsonObject) throws JSONException {
                    try {
                        onDownloadEntitlement(jsonObject);
                    } catch (ProcedureException e) {
                        resultHandler.onError(e.error);
                    }
                }

                @Override
                protected void onError(EnigmaError error) {
                    resultHandler.onError(error);
                }
            });
        } catch (MalformedURLException e) {
            resultHandler.onError(new UnexpectedError(e));
            return;
        }
    }

    private void onDownloadEntitlement(JSONObject jsonObject) throws JSONException, ProcedureException {
        JSONArray formats = jsonObject.getJSONArray("formats");
        JSONObject format = selectFormat(formats);
        if(format == null) {
            throw new ProcedureException(new NoSupportedMediaFormatsError("No suitable format found in response."));
        }

        Uri mediaUri = Uri.parse(format.getString("mediaLocator"));
        final EnigmaMediaFormat mediaFormat = EnigmaMediaFormat.parseMediaFormat(format);
        if(mediaFormat == null) {
            throw new ProcedureException(new UnsupportedMediaFormatError("Could not parse format: "+format.toString()));
        }

        AndroidThreadUtil.runOnUiThread(() -> {
            try {
                DataSource.Factory dataSourceFactory = ExoPlayerDownloadContext.getDataSourceFactory();
                RenderersFactory renderersFactory = ExoPlayerDownloadContext.getRenderersFactory();
                DownloadHelper downloadHelper = getDownloadHelper(mediaFormat, mediaUri, dataSourceFactory, renderersFactory);
                downloadHelper.prepare(new DownloadHelper.Callback() {
                    @Override
                    public void onPrepared(DownloadHelper helper) {
                        AndroidThreadUtil.runOnUiThread(() -> {
                            try {
                                String contentId = request.getContentId();
                                DownloadedAssetMetaData metaData = new DownloadedAssetMetaData(request.getAssetId());
                                final DownloadRequest downloadRequest = helper.getDownloadRequest(contentId, metaData.getBytes());
                                ExoPlayerDownloadContext.sendAddDownload(downloadRequest, false);
                            } catch(RuntimeException e) {
                                resultHandler.onError(new UnexpectedError(e));
                                return;
                            }
                            resultHandler.onStarted();
                        });
                    }

                    @Override
                    public void onPrepareError(DownloadHelper helper, IOException e) {
                        resultHandler.onError(new UnexpectedError(e, "Failed to prepare DownloadHelper"));
                    }
                });
            } catch (RuntimeException e) {
                resultHandler.onError(new UnexpectedError(e));
                return;
            }
        });
    }

    private DownloadHelper getDownloadHelper(EnigmaMediaFormat enigmaMediaFormat, Uri mediaUri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
        EnigmaMediaFormat.DrmTechnology drmTechnology = enigmaMediaFormat.getDrmTechnology();
        EnigmaMediaFormat.StreamFormat streamFormat = enigmaMediaFormat.getStreamFormat();
        if(drmTechnology == EnigmaMediaFormat.DrmTechnology.NONE) {
            if(streamFormat == EnigmaMediaFormat.StreamFormat.DASH) {
                return DownloadHelper.forDash(mediaUri, dataSourceFactory, renderersFactory);
            } else if(streamFormat == EnigmaMediaFormat.StreamFormat.HLS) {
                return DownloadHelper.forHls(mediaUri, dataSourceFactory, renderersFactory);
            } else if (streamFormat == EnigmaMediaFormat.StreamFormat.SMOOTHSTREAMING) {
                return DownloadHelper.forSmoothStreaming(mediaUri, dataSourceFactory, renderersFactory);
            } else {
                throw new RuntimeException("Unsupported stream format: "+streamFormat);
            }
        } else {
            throw new RuntimeException("Unsupported DRM technology: "+drmTechnology);
        }
    }

    private JSONObject selectFormat(JSONArray formats) throws ProcedureException, JSONException {
        List<JSONObject> suitableFormats = new ArrayList<>();

        for(int i = 0; i < formats.length(); ++i) {
            JSONObject formatObject = formats.getJSONObject(i);
            if("DASH".equals(formatObject.getString("format")) && !formatObject.has("drm")) {
                suitableFormats.add(formatObject);
            }
        }

        if(suitableFormats.size() > 1) {
            //Don't do this.
            //(this won't be a problem if we have MediaFormatSelector)!
            throw new ProcedureException(new InternalError("Multiple applicable formats found"));
        } if(suitableFormats.size() == 1) {
            return suitableFormats.get(0);
        } else {
            return null;
        }
    }
}
