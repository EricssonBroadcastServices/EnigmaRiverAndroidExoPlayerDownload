package com.redbeemedia.enigma.exoplayerdownload;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSessionEventListener;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.MimeTypes;
import com.redbeemedia.enigma.core.businessunit.IBusinessUnit;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.drm.DrmInfoFactory;
import com.redbeemedia.enigma.core.drm.IDrmInfo;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.error.InternalError;
import com.redbeemedia.enigma.core.error.MaxDownloadCountLimitReachedError;
import com.redbeemedia.enigma.core.error.NoSupportedMediaFormatsError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.error.UnsupportedMediaFormatError;
import com.redbeemedia.enigma.core.format.ChainedMediaFormatSelector;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormatUtil;
import com.redbeemedia.enigma.core.format.IMediaFormatSelector;
import com.redbeemedia.enigma.core.format.IMediaFormatSupportSpec;
import com.redbeemedia.enigma.core.http.AuthenticatedExposureApiCall;
import com.redbeemedia.enigma.core.http.ExposureHttpError;
import com.redbeemedia.enigma.core.http.HttpStatus;
import com.redbeemedia.enigma.core.http.IHttpCall;
import com.redbeemedia.enigma.core.json.JsonObjectResponseHandler;
import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.AndroidThreadUtil;
import com.redbeemedia.enigma.core.util.UrlPath;
import com.redbeemedia.enigma.download.AudioDownloadable;
import com.redbeemedia.enigma.download.DownloadStartRequest;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.IDownloadablePart;
import com.redbeemedia.enigma.download.IMetadataManager;
import com.redbeemedia.enigma.download.SubtitleDownloadable;
import com.redbeemedia.enigma.download.VideoDownloadable;
import com.redbeemedia.enigma.download.resulthandler.BaseResultHandler;
import com.redbeemedia.enigma.download.resulthandler.IDownloadStartResultHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*package-protected*/ class StartDownloadProcedure {
    private final IBusinessUnit businessUnit;
    private final ISession session;
    private final DownloadStartRequest request;
    private final IDownloadStartResultHandler resultHandler;
    private final WeakReference<Context> context;

    public StartDownloadProcedure(Context context, IBusinessUnit businessUnit, DownloadStartRequest request, IDownloadStartResultHandler resultHandler) {
        this.businessUnit = businessUnit;
        this.session = request.getSession();
        this.request = request;
        this.resultHandler = resultHandler;
        this.context = new WeakReference<>(context);
    }

    public void begin() {
        final String assetId = request.getAssetId();
        UrlPath endpoint = businessUnit.getApiBaseUrl("v2").append("/entitlement/").append(assetId).append("/download");
        try {
            // append device parameters
            endpoint = endpoint.appendQueryStringParameters(EnigmaRiverContext.getDeviceParameters().getParameters());
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
        } catch (MalformedURLException e) {
            resultHandler.onError(new UnexpectedError(e));
            return;
        }
    }

    private void onDownloadEntitlement(JSONObject jsonObject) throws JSONException, ProcedureException {
        String requestId = jsonObject.optString("requestId");
        String playSessionId = jsonObject.optString("playSessionId");
        JSONObject cdn = jsonObject.optJSONObject("cdn");
        String cdnProvider = cdn.optString("provider");
        String baseUrl = jsonObject.optJSONObject("analytics").optString("baseUrl");
        int duration = jsonObject.optInt("durationInMs",0);
        String playToken = jsonObject.optString("playToken");
        long playTokenExpiration = jsonObject.optLong("playTokenExpiration");
        String publicationEnd = jsonObject.optString("publicationEnd");

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
            throw new ProcedureException(new UnsupportedMediaFormatError("Could not parse format: " + format.toString()));
        }

        if(mediaFormat.getDrmTechnology() == EnigmaMediaFormat.DrmTechnology.WIDEVINE) {
            JSONObject widevineJson = format.getJSONObject("drm").getJSONObject(EnigmaMediaFormat.DrmTechnology.WIDEVINE.getKey());
            String licenseServerUrl = widevineJson.getString("licenseServerUrl");

            downloadWidevineLicense(mediaFormat, mediaUri, licenseServerUrl, playToken, requestId, playTokenExpiration,publicationEnd, playSessionId, baseUrl, cdnProvider, duration);
        } else {
            startDownload(mediaFormat, mediaUri, null, playTokenExpiration,publicationEnd, playSessionId, baseUrl, cdnProvider, duration);
        }
    }

    private void startDownload(EnigmaMediaFormat mediaFormat, Uri mediaUri, DrmLicenceInfo drmLicenceInfo, long playTokenExpiration,String publicationEnd, String playSessionId, String baseUrl, String cdnProvider, int duration) throws ProcedureException {
        AndroidThreadUtil.runOnUiThread(() -> {
            try {
                if(context.get() == null) {
                    resultHandler.onError(new UnexpectedError("Failed to prepare DownloadHelper. Context was null"));
                    return;
                }
                DataSource.Factory dataSourceFactory = ExoPlayerDownloadContext.getDataSourceFactory();
                RenderersFactory renderersFactory = ExoPlayerDownloadContext.getRenderersFactory();
                DownloadHelper downloadHelper = getDownloadHelper(context.get(), mediaFormat, mediaUri, dataSourceFactory, renderersFactory);
                downloadHelper.prepare(new DownloadHelper.Callback() {
                    @Override
                    public void onPrepared(DownloadHelper helper) {
                        List<StreamKey> streamKeys = new ArrayList<>();

                        for(int periodIndex = 0; periodIndex < helper.getPeriodCount(); ++periodIndex) {
                            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = helper.getMappedTrackInfo(periodIndex);

                            for(int rendererIndex = 0; rendererIndex < mappedTrackInfo.getRendererCount(); ++rendererIndex) {
                                IFormatMatcher formatMatcher = getFormatMatcher(mappedTrackInfo, rendererIndex);
                                if(formatMatcher != null) {
                                    IFormatMatchHandler matchHandler = new StreamKeyListBuilder(helper, periodIndex, rendererIndex, streamKeys);
                                    findMatchingFormats(mappedTrackInfo, rendererIndex, formatMatcher, matchHandler);
                                }
                            }
                        }
                        AndroidThreadUtil.runOnUiThread(() -> {
                            try {
                                String contentId = request.getContentId();
                                DownloadedAssetMetaData metaData = new DownloadedAssetMetaData(request.getAssetId(), drmLicenceInfo, request.getSession(), playTokenExpiration,publicationEnd, playSessionId, baseUrl, cdnProvider, duration);
                                IMetadataManager metadataManager = EnigmaDownloadContext.getMetadataManager();
                                metadataManager.store(contentId, metaData.getBytes());
                                metadataManager.store(metaData.getAssetId(), metaData.getBytes());
                                final DownloadRequest downloadRequest = new DownloadRequest.Builder(contentId, mediaUri).setStreamKeys(streamKeys).build();
                                helper.release();
                                ExoPlayerDownloadContext.sendAddDownload(downloadRequest);
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


    private void downloadWidevineLicense(EnigmaMediaFormat mediaFormat, Uri mediaUri, String licenseServerUri, String playToken, String requestId, long playTokenExpiration,String publicationEnd, String playSessionId, String baseUrl, String cdnProvider, int duration) throws ProcedureException {
        try {
            WidevineHelper.getManifest(mediaUri.toString(), new BaseResultHandler<Document>() {
                @Override
                public void onResult(Document document) {
                    try {
                        List<Element> elements = WidevineHelper.findContentProtectionTags(document.getDocumentElement(), C.WIDEVINE_UUID);
                        String pssh = WidevineHelper.getPssh(elements);
                        byte[] initData = Base64.decode(pssh, Base64.DEFAULT);

                        DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory();
                        factory.setUserAgent("license_downloader");
                        HttpDataSource.Factory dataSourceFactory = factory;
                        IDrmInfo drmInfo = DrmInfoFactory.createWidevineDrmInfo(licenseServerUri, playToken, requestId);
                        HashMap<String, String> optional = new HashMap<>();
                        for(Map.Entry<String, String> entry : drmInfo.getDrmKeyRequestProperties()) {
                            optional.put(entry.getKey(), entry.getValue());
                        }
                        OfflineLicenseHelper offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(
                                licenseServerUri,
                                false,
                                dataSourceFactory,
                                new DrmSessionEventListener.EventDispatcher());

                        DrmInitData drmInitData = new DrmInitData(new DrmInitData.SchemeData(C.WIDEVINE_UUID, MimeTypes.VIDEO_MP4, initData));
                        byte[] licenceData = offlineLicenseHelper.downloadLicense(new Format.Builder().setDrmInitData(drmInitData).build());
                        DrmLicenceInfo drmLicenceInfo = DrmLicenceInfo.create(licenceData, offlineLicenseHelper);

                        offlineLicenseHelper.release();

                        startDownload(mediaFormat, mediaUri, drmLicenceInfo, playTokenExpiration, publicationEnd, playSessionId, baseUrl, cdnProvider, duration);
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
            throw new ProcedureException(new InternalError("Could not parse mediaLocator: " + mediaUri.toString()));
        }
    }

    private DownloadHelper getDownloadHelper(Context context, EnigmaMediaFormat enigmaMediaFormat, Uri mediaUri, DataSource.Factory dataSourceFactory, RenderersFactory renderersFactory) {
        EnigmaMediaFormat.DrmTechnology drmTechnology = enigmaMediaFormat.getDrmTechnology();
        EnigmaMediaFormat.StreamFormat streamFormat = enigmaMediaFormat.getStreamFormat();
        if(drmTechnology == EnigmaMediaFormat.DrmTechnology.NONE) {
            // TODO: instead of MediaItem.fromUri we should rather use the builder and set the MIME type correctly as it is
            // required in ExoPlayerDownloadData.createMediaSource and currently only inferred from the URI
            if(streamFormat == EnigmaMediaFormat.StreamFormat.DASH) {
                return DownloadHelper.forMediaItem(context, MediaItem.fromUri(mediaUri), renderersFactory, dataSourceFactory);
            } else if(streamFormat == EnigmaMediaFormat.StreamFormat.HLS) {
                return DownloadHelper.forMediaItem(context, MediaItem.fromUri(mediaUri), renderersFactory, dataSourceFactory);
            } else if (streamFormat == EnigmaMediaFormat.StreamFormat.SMOOTHSTREAMING) {
                return DownloadHelper.forMediaItem(context, MediaItem.fromUri(mediaUri), renderersFactory, dataSourceFactory);
            } else if (streamFormat == EnigmaMediaFormat.StreamFormat.MP3) {
                return DownloadHelper.forMediaItem(context, new MediaItem.Builder().setMimeType("audio/mp3").setUri(mediaUri).build(), renderersFactory, dataSourceFactory);
            } else {
                throw new RuntimeException("Unsupported stream format: "+streamFormat);
            }
        } else if(drmTechnology == EnigmaMediaFormat.DrmTechnology.WIDEVINE) {
            if(streamFormat == EnigmaMediaFormat.StreamFormat.DASH) {
                return DownloadHelper.forMediaItem(context, MediaItem.fromUri(mediaUri), renderersFactory, dataSourceFactory);
            }
        }
        throw new RuntimeException("Unsupported DRM technology: "+drmTechnology);
    }

    private IFormatMatcher getFormatMatcher(MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int rendererIndex) {
        int rendererType = mappedTrackInfo.getRendererType(rendererIndex);
        if(rendererType == C.TRACK_TYPE_VIDEO) {
            VideoDownloadable video = request.getVideo();
            if(video == null) {
                return new MaxBitrateSelector(mappedTrackInfo, rendererIndex);
            } else {
                return (groupIndex, format) -> format.bitrate == video.getBitrate();
            }
        } else if(rendererType == C.TRACK_TYPE_AUDIO) {
            List<AudioDownloadable> audios = request.getAudios();
            if(audios.isEmpty()) {
                return new MaxBitrateSelector(mappedTrackInfo, rendererIndex);
            } else {
                return createAnyMatcher(audios);
            }
        } else if(rendererType == C.TRACK_TYPE_TEXT) {
            List<SubtitleDownloadable> subtitles = request.getSubtitles();
            if(subtitles.isEmpty()) {
                return new NoneSelector();
            } else {
                return createAnyMatcher(subtitles);
            }
        } else {
            return null;
        }
    }

    private static IntList getMaxBitrates(MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
                                          int rendererIndex) {
        TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
        IntList maxBitrates = new IntList(trackGroupArray.length);
        for(int groupIndex = 0; groupIndex < trackGroupArray.length; ++groupIndex) {
            TrackGroup trackGroup = trackGroupArray.get(groupIndex);
            int maxBitrate = -1;
            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                Format format = trackGroup.getFormat(trackIndex);
                if(format.bitrate > maxBitrate) {
                    maxBitrate = format.bitrate;
                }
            }
            maxBitrates.add(maxBitrate);
        }
        return maxBitrates;
    }

    private static void findMatchingFormats(MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
                                            int rendererIndex,
                                            IFormatMatcher formatMatcher,
                                            IFormatMatchHandler formatMatchHandler) {
        TrackGroupArray trackGroupArray = mappedTrackInfo.getTrackGroups(rendererIndex);
        for(int groupIndex = 0; groupIndex < trackGroupArray.length; ++groupIndex) {
            TrackGroup trackGroup = trackGroupArray.get(groupIndex);
            for(int trackIndex = 0; trackIndex < trackGroup.length; ++trackIndex) {
                Format format = trackGroup.getFormat(trackIndex);
                if(formatMatcher.matches(groupIndex, format)) {
                    formatMatchHandler.onMatch(groupIndex, trackIndex, format);
                }
            }
        }
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

        public boolean contains(int value) {
            for(int i = 0; i < array.length; ++i) {
                if(array[i] == value) {
                    return true;
                }
            }
            return false;
        }
    }

    private static class MaxBitrateSelector implements IFormatMatcher {
        private final int[] maxBitrates;

        public MaxBitrateSelector(MappingTrackSelector.MappedTrackInfo mappedTrackInfo, int rendererIndex) {
            this.maxBitrates = getMaxBitrates(mappedTrackInfo, rendererIndex).toArray();
        }

        @Override
        public boolean matches(int groupIndex, Format format) {
            try {
                return maxBitrates[groupIndex] == format.bitrate;
            } catch (ArrayIndexOutOfBoundsException e) {
                return false;
            }
        }
    }

    private static class NoneSelector implements IFormatMatcher {
        @Override
        public boolean matches(int groupIndex, Format format) {
            return false;
        }
    }

    private interface IFormatMatchHandler {
        void onMatch(int groupIndex, int trackIndex, Format format);
    }


    private static class StreamKeyListBuilder implements IFormatMatchHandler {
        private final DownloadHelper downloadHelper;
        private final int periodIndex;
        private final int rendererIndex;
        private final List<StreamKey> streamKeys;

        public StreamKeyListBuilder(DownloadHelper downloadHelper, int periodIndex, int rendererIndex, List<StreamKey> streamKeys) {
            this.downloadHelper = downloadHelper;
            this.periodIndex = periodIndex;
            this.rendererIndex = rendererIndex;
            this.streamKeys = streamKeys;
        }

        @Override
        public void onMatch(int groupIndex, int trackIndex, Format format) {
            streamKeys.add(new StreamKey(periodIndex, remapGroupIndex(groupIndex), trackIndex));
        }

        //Utility method to help overcome ExoPlayer's strange indexing
        private int remapGroupIndex(int groupIndex) {
            TrackGroupArray trackGroupArray = downloadHelper.getTrackGroups(periodIndex);
            MappingTrackSelector.MappedTrackInfo mappedTrackInfo = downloadHelper.getMappedTrackInfo(periodIndex);
            return trackGroupArray.indexOf(mappedTrackInfo.getTrackGroups(rendererIndex).get(groupIndex));
        }
    }

    private static IFormatMatcher createAnyMatcher(Collection<? extends IDownloadablePart> selectedParts) {
        List<IFormatMatcher> matchers = new ArrayList<>();
        for(IDownloadablePart part : selectedParts) {
            matchers.add(new ExoFormatMatcher(part.getRawJson()));
        }
        return new AnyFormatMatcher(matchers);
    }
}
