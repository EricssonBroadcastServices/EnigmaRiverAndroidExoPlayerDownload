package com.redbeemedia.enigma.exoplayerdownload;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.redbeemedia.enigma.core.businessunit.IBusinessUnit;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.drm.DrmInfoFactory;
import com.redbeemedia.enigma.core.drm.IDrmInfo;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.error.InternalError;
import com.redbeemedia.enigma.core.error.NoSupportedMediaFormatsError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.error.UnsupportedMediaFormatError;
import com.redbeemedia.enigma.core.format.ChainedMediaFormatSelector;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormatUtil;
import com.redbeemedia.enigma.core.format.IMediaFormatSelector;
import com.redbeemedia.enigma.core.format.IMediaFormatSupportSpec;
import com.redbeemedia.enigma.core.http.AuthenticatedExposureApiCall;
import com.redbeemedia.enigma.core.http.IHttpCall;
import com.redbeemedia.enigma.core.json.JsonObjectResponseHandler;
import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.AndroidThreadUtil;
import com.redbeemedia.enigma.core.util.UrlPath;
import com.redbeemedia.enigma.download.DownloadStartRequest;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.IMetadataManager;
import com.redbeemedia.enigma.download.VideoDownloadable;
import com.redbeemedia.enigma.download.resulthandler.BaseResultHandler;
import com.redbeemedia.enigma.download.resulthandler.IDownloadStartResultHandler;
import com.redbeemedia.enigma.exoplayerintegration.ExoUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                    } catch (Exception e) {
                        resultHandler.onError(new UnexpectedError(e));
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
        String requestId = jsonObject.optString("requestId");
        String playToken = jsonObject.optString("playToken");

        JSONArray formats = jsonObject.getJSONArray("formats");

        IMediaFormatSupportSpec mediaFormatSupportSpec = ExoPlayerDownloadContext.getDownloadSupportSpec();
        IMediaFormatSelector selector = new ChainedMediaFormatSelector(
                EnigmaDownloadContext.getDefaultDownloadFormatSelector(),
                request.getMediaFormatSelector());

        JSONObject format = EnigmaMediaFormatUtil.selectUsableMediaFormat(formats, mediaFormatSupportSpec, selector);
        if(format == null) {
            throw new ProcedureException(new NoSupportedMediaFormatsError("No suitable format found in response."));
        }

        Uri mediaUri = Uri.parse(format.getString("mediaLocator"));
        final EnigmaMediaFormat mediaFormat = EnigmaMediaFormat.parseMediaFormat(format);
        if(mediaFormat == null) {
            throw new ProcedureException(new UnsupportedMediaFormatError("Could not parse format: "+format.toString()));
        }

        if(mediaFormat.getDrmTechnology() == EnigmaMediaFormat.DrmTechnology.WIDEVINE) {
            JSONObject widevineJson = format.getJSONObject("drm").getJSONObject(EnigmaMediaFormat.DrmTechnology.WIDEVINE.getKey());
            String licenseServerUrl = widevineJson.getString("licenseServerUrl");

            downloadWidevineLicense(mediaFormat, mediaUri, licenseServerUrl, playToken, requestId);
        } else {
            startDownload(mediaFormat, mediaUri, null);
        }
    }

    private void startDownload(EnigmaMediaFormat mediaFormat, Uri mediaUri, byte[] drmKey) throws ProcedureException {
        AndroidThreadUtil.runOnUiThread(() -> {
            try {
                DataSource.Factory dataSourceFactory = ExoPlayerDownloadContext.getDataSourceFactory();
                RenderersFactory renderersFactory = ExoPlayerDownloadContext.getRenderersFactory();
                DownloadHelper downloadHelper = getDownloadHelper(mediaFormat, mediaUri, dataSourceFactory, renderersFactory);
                downloadHelper.prepare(new DownloadHelper.Callback() {
                    @Override
                    public void onPrepared(DownloadHelper helper) {
                        for(int periodIndex = 0; periodIndex < helper.getPeriodCount(); ++periodIndex) {
                            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = helper.getMappedTrackInfo(periodIndex);
                            helper.replaceTrackSelections(periodIndex, getTrackSelectorParameters(mappedTrackInfo));
                        }
                        AndroidThreadUtil.runOnUiThread(() -> {
                            try {
                                String contentId = request.getContentId();
                                DownloadedAssetMetaData metaData = new DownloadedAssetMetaData(request.getAssetId());
                                if(drmKey != null) {
                                    metaData.setDrmKey(drmKey);
                                }
                                IMetadataManager metadataManager = EnigmaDownloadContext.getMetadataManager();
                                metadataManager.store(contentId, metaData.getBytes());
                                final DownloadRequest downloadRequest = helper.getDownloadRequest(contentId, null);
                                helper.release();
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



    private void downloadWidevineLicense(EnigmaMediaFormat mediaFormat, Uri mediaUri, String licenseServerUri, String playToken, String requestId) throws ProcedureException {
        try {
            WidevineHelper.getManifest(mediaUri.toString(), new BaseResultHandler<Document>() {
                @Override
                public void onResult(Document document) {
                    try {
                        List<Element> elements = WidevineHelper.findContentProtectionTags(document.getDocumentElement(), C.WIDEVINE_UUID);
                        String pssh = WidevineHelper.getPssh(elements);
                        byte[] initData = Base64.decode(pssh, Base64.DEFAULT);

                        HttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory("license_downloader");
                        IDrmInfo drmInfo = DrmInfoFactory.createWidevineDrmInfo(licenseServerUri, playToken, requestId);
                        HashMap<String, String> optional = new HashMap<>();
                        for(Map.Entry<String, String> entry : drmInfo.getDrmKeyRequestProperties()) {
                            optional.put(entry.getKey(), entry.getValue());
                        }
                        OfflineLicenseHelper<FrameworkMediaCrypto> offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(licenseServerUri, false, dataSourceFactory, optional);

                        DrmInitData drmInitData = new DrmInitData(new DrmInitData.SchemeData(C.WIDEVINE_UUID, MimeTypes.VIDEO_MP4, initData));
                        byte[] licenceData = offlineLicenseHelper.downloadLicense(drmInitData);

                        offlineLicenseHelper.release();

                        startDownload(mediaFormat, mediaUri, licenceData);
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
        } else if(drmTechnology == EnigmaMediaFormat.DrmTechnology.WIDEVINE) {
            if(streamFormat == EnigmaMediaFormat.StreamFormat.DASH) {
                return DownloadHelper.forDash(mediaUri, dataSourceFactory, renderersFactory);
            }
        }
        throw new RuntimeException("Unsupported DRM technology: "+drmTechnology);
    }


    private DefaultTrackSelector.Parameters getTrackSelectorParameters(MappingTrackSelector.MappedTrackInfo mappedTrackInfo) {
        DefaultTrackSelector.ParametersBuilder selectorBuilder = new DefaultTrackSelector.ParametersBuilder();

        VideoDownloadable video = request.getVideo();
        if(video == null) {
            //This is what DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS does
            selectorBuilder.setForceHighestSupportedBitrate(true);
        } else {
            boolean success = overrideTrackSelection(selectorBuilder, mappedTrackInfo, ExoUtil.DEFAULT_VIDEO_RENDERER_INDEX, new IFormatMatcher() {
                @Override
                public boolean matches(Format format) {
                    return format.bitrate == video.getBitrate();
                }
            });
            if(!success) {
                Log.d("ExoPlayerDownload", "Failed to select video track for download");
            }
        }

        //This is where we will also add overrides for audio and text

        return selectorBuilder.build();
    }

    private static boolean overrideTrackSelection(DefaultTrackSelector.ParametersBuilder parametersBuilder,
                                               MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
                                               int rendererIndex ,
                                               IFormatMatcher formatMatcher) {
        boolean atLeastOneOverrideMade = false;
        TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
        for(int groupIndex = 0; groupIndex < trackGroupArray.length; ++groupIndex) {
            TrackGroup trackGroup = trackGroupArray.get(groupIndex);
            IntList matchingTrackIndicesList = new IntList(trackGroup.length);
            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                if(formatMatcher.matches(trackGroup.getFormat(trackIndex))) {
                    matchingTrackIndicesList.add(trackIndex);
                }
            }
            int[] matchingTrackIndices = matchingTrackIndicesList.toArray();
            if(matchingTrackIndices != null && matchingTrackIndices.length > 0) {
                DefaultTrackSelector.SelectionOverride selectionOverride = new DefaultTrackSelector.SelectionOverride(groupIndex, matchingTrackIndices);
                parametersBuilder.setSelectionOverride(rendererIndex, trackGroupArray, selectionOverride);
                atLeastOneOverrideMade = true;
            }
        }
        return atLeastOneOverrideMade;
    }

    private interface IFormatMatcher {
        boolean matches(Format format);
    }

    private static class IntList {
        private int[] array;
        private int length = 0;

        public IntList(int estimatedSize) {
            this.array = new int[estimatedSize];
        }

        public IntList add(int value) {
            if(length >= array.length) {
                int[] newArray = new int[length+1];
                System.arraycopy(array, 0, newArray, 0, array.length);
                array = newArray;
            }
            array[length++] = value;
            return this;
        }

        public int[] toArray() {
            int[] trimmed = new int[length];
            System.arraycopy(array, 0, trimmed, 0, trimmed.length);
            return trimmed;
        }
    }
}
