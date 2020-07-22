package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;
import com.redbeemedia.enigma.exoplayerintegration.ExoPlayerIntegrationContext;

import java.util.ArrayList;
import java.util.List;

/*package-protected*/ class GetDownloadedAssetsProcedure {
    private final IResultHandler<List<DownloadedPlayable>> resultHandler;

    public GetDownloadedAssetsProcedure(IResultHandler<List<DownloadedPlayable>> resultHandler) {
        this.resultHandler = resultHandler;
    }

    public void begin() {
        List<DownloadedPlayable> playables = new ArrayList<>();
        try {

            DownloadIndex downloadIndex = ExoPlayerIntegrationContext.getDownloadManager().getDownloadIndex();
            DownloadCursor downloadCursor = downloadIndex.getDownloads(Download.STATE_COMPLETED);
            while(downloadCursor.moveToNext()) {
                Download download = downloadCursor.getDownload();
                DownloadedAssetMetaData metaData = DownloadedAssetMetaData.fromBytes(download.request.data);
                DownloadedPlayable.IInternalDownloadData downloadData = new ExoPlayerDownloadData(download.request.id, metaData);
                playables.add(new DownloadedPlayable(downloadData));
            }
        } catch (Exception e) {
            resultHandler.onError(new UnexpectedError(e));
            return;
        }
        resultHandler.onResult(playables);
    }
}
