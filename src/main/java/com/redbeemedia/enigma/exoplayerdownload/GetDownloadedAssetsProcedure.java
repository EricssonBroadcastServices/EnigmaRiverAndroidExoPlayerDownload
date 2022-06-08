package com.redbeemedia.enigma.exoplayerdownload;

import android.util.Log;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.IMetadataManager;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;
import com.redbeemedia.enigma.exoplayerintegration.ExoPlayerIntegrationContext;

import java.util.ArrayList;
import java.util.List;

/*package-protected*/ class GetDownloadedAssetsProcedure {
    private final IResultHandler<List<DownloadedPlayable>> resultHandler;
    private final String userId;

    public GetDownloadedAssetsProcedure(IResultHandler<List<DownloadedPlayable>> resultHandler) {
        this.resultHandler = resultHandler;
        this.userId = null;
    }

    public GetDownloadedAssetsProcedure(IResultHandler<List<DownloadedPlayable>> resultHandler, String userId) {
        this.resultHandler = resultHandler;
        this.userId = userId;
    }

    public void begin() {
        List<DownloadedPlayable> playables = new ArrayList<>();
        try {

            DownloadIndex downloadIndex = ExoPlayerDownloadContext.getDownloadManager().getDownloadIndex();
            DownloadCursor downloadCursor = downloadIndex.getDownloads(Download.STATE_COMPLETED);
            while(downloadCursor.moveToNext()) {
                Download download = downloadCursor.getDownload();

                IMetadataManager metadataManager = EnigmaDownloadContext.getMetadataManager();
                DownloadedAssetMetaData metaData = DownloadedAssetMetaData.fromBytes(metadataManager.load(download.request.id));

                metaData.setFileSize(download.getBytesDownloaded());

                DownloadedPlayable.IInternalDownloadData downloadData = new ExoPlayerDownloadData(download.request.id, metaData);

                if (userId != null) {
                    if (!metaData.getSession().getUserId().equalsIgnoreCase(userId)) {
                        continue;
                    }
                }

                playables.add(new DownloadedPlayable(downloadData));
            }
        } catch (Exception e) {
            resultHandler.onError(new UnexpectedError(e));
            return;
        }
        resultHandler.onResult(playables);
    }
}
