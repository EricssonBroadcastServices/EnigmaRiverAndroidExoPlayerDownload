package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Parcel;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.google.android.exoplayer2.offline.StreamKey;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.IDrmLicence;
import com.redbeemedia.enigma.exoplayerintegration.IMediaSourceFactory;
import com.redbeemedia.enigma.exoplayerintegration.IOfflineDrmKeySource;
import com.redbeemedia.enigma.exoplayerintegration.util.MediaSourceFactoryConfigurator;

import java.io.IOException;
import java.util.List;

/**
 * Data used/needed by exoplayerintegration module to start playback of a downloaded file.
 */
public class ExoPlayerDownloadData implements DownloadedPlayable.IInternalDownloadData, IMediaSourceFactory, IOfflineDrmKeySource {
    public final String contentId;
    private final DownloadedAssetMetaData metaData; //Expected to be non-null

    public ExoPlayerDownloadData(String contentId, DownloadedAssetMetaData metaData) {
        this.contentId = contentId; // This the same as download.request.id, which comes from DownloadStartRequest#getContentId
        this.metaData = metaData != null ? metaData : DownloadedAssetMetaData.newDefaultMetadata();
    }

    @Override
    public String getAssetId() {
        return metaData.getAssetId();
    }

    @Override
    public String getPlaySessionId() {
        return metaData.getPlaySessionId();
    }

    @Override
    public String getCdnProvider() {
        return metaData.getCdnProvider();
    }

    @Override
    public int getDuration() {
        return metaData.getDuration();
    }

    @Override
    public String getAnalyticsBaseUrl() {
        return metaData.getAnalyticsBaseUrl();
    }

    @Override
    public Long getFileSize() {
        return metaData.getFileSize();
    }

    @Override
    public long getPlayTokenExpiration() {
        return metaData.getPlayTokenExpiration();
    }

    @Override
    public String getPublicationEnd() {
        return metaData.getPublicationEnd();
    }

    @Override
    public byte[] getDrmKeys() {
        DrmLicenceInfo drmLicenceInfo = metaData.getDrmLicenceInfo();
        if (drmLicenceInfo == null) {
            return null;
        }
        return drmLicenceInfo.getDrmKey();
    }

    @Override
    public IDrmLicence getDrmLicence() {
        DrmLicenceInfo drmLicenceInfo = metaData.getDrmLicenceInfo();
        if (drmLicenceInfo != null) {
            return new WidevineDrmLicence(contentId, metaData);
        } else {
            return null;
        }
    }

    @Override
    public MediaSource createMediaSource(MediaSourceFactoryConfigurator configurator) {
        try {
            Cache downloadCache = ExoPlayerDownloadContext.getDownloadCache();
            DownloadIndex downloadIndex = ExoPlayerDownloadContext.getDownloadManager().getDownloadIndex();
            Download download = downloadIndex.getDownload(contentId);
            if (download == null) {
                throw new NullPointerException();
            }

            DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory();
            defaultHttpDataSourceFactory.setUserAgent("enigma_river_download_complementor");
            DataSource.Factory upstreamDataSourceFactory = defaultHttpDataSourceFactory;
            CacheDataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory();
            cacheDataSourceFactory.setCache(downloadCache);
            cacheDataSourceFactory.setUpstreamDataSourceFactory(upstreamDataSourceFactory);

            final MediaSource.Factory factory;
            if (download.request.mimeType != null) {
                if (download.request.mimeType.equals("audio/mp3"))
                {
                    factory = configurator.configure(new ProgressiveMediaSource.Factory(cacheDataSourceFactory));
                }
                else
                {
                    factory = configurator.configure(new DashMediaSource.Factory(cacheDataSourceFactory));
                }
            }
            else
            {
                if (download.request.uri.getLastPathSegment().endsWith(".mp3"))
                {
                    factory = configurator.configure(new ProgressiveMediaSource.Factory(cacheDataSourceFactory));
                }
                else
                {
                    factory = configurator.configure(new DashMediaSource.Factory(cacheDataSourceFactory));
                }
            }

            List<StreamKey> keys = download.request.streamKeys;
            MediaItem.Builder mediaBuilder = new MediaItem.Builder();
            if (keys != null) {
                mediaBuilder.setStreamKeys(keys);
            }
            mediaBuilder.setUri(download.request.uri);
            return factory.createMediaSource(mediaBuilder.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(contentId);
        byte[] metaDataBytes = metaData.getBytes();
        dest.writeInt(metaDataBytes.length);
        dest.writeByteArray(metaDataBytes);
    }

    public static final Creator<ExoPlayerDownloadData> CREATOR = new Creator<ExoPlayerDownloadData>() {
        @Override
        public ExoPlayerDownloadData createFromParcel(Parcel source) {
            String contentId = source.readString();
            int metaDataBytesLength = source.readInt();
            byte[] metaDataBytes = new byte[metaDataBytesLength];
            source.readByteArray(metaDataBytes);
            return new ExoPlayerDownloadData(contentId, DownloadedAssetMetaData.fromBytes(metaDataBytes));
        }

        @Override
        public ExoPlayerDownloadData[] newArray(int size) {
            return new ExoPlayerDownloadData[size];
        }
    };
}
