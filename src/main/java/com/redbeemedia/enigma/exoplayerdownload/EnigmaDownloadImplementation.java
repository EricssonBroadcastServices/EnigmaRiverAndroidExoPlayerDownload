package com.redbeemedia.enigma.exoplayerdownload;

import android.content.Context;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadCursor;
import com.google.android.exoplayer2.offline.DownloadIndex;
import com.redbeemedia.enigma.core.businessunit.IBusinessUnit;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.download.DownloadStartRequest;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.IEnigmaDownloadImplementation;
import com.redbeemedia.enigma.download.assetdownload.IAssetDownload;
import com.redbeemedia.enigma.download.resulthandler.IDownloadStartResultHandler;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import java.util.ArrayList;
import java.util.List;

/*package-protected*/ class EnigmaDownloadImplementation implements IEnigmaDownloadImplementation {
    @Override
    public void startAssetDownload(Context context, IBusinessUnit businessUnit, DownloadStartRequest request, IDownloadStartResultHandler resultHandler) {
        new StartDownloadProcedure(context, businessUnit, request, resultHandler)
                .begin();
    }

    @Override
    public void getDownloadedAssets(IResultHandler<List<DownloadedPlayable>> resultHandler) {
        new GetDownloadedAssetsProcedure(resultHandler)
                .begin();
    }

    @Override
    public void getDownloadedAssets(String userId,IResultHandler<List<DownloadedPlayable>> resultHandler) {
        new GetDownloadedAssetsProcedure(resultHandler, userId)
                .begin();
    }

    @Override
    public void removeDownloadedAsset(DownloadedPlayable.IInternalDownloadData downloadData, IResultHandler<Void> resultHandler) {
        new RemoveDownloadedAssetProcedure(downloadData, resultHandler)
                .begin();
    }

    @Override
    public void getDownloadsInProgress(IResultHandler<List<IAssetDownload>> resultHandler) {
        ExoPlayerDownloadContext.getEnigmaAssetDownloadManager().getDownloadsInProgress(resultHandler);
    }

    @Override
    public void getDownloadsInProgress(String userId, IResultHandler<List<IAssetDownload>> resultHandler) {
        ExoPlayerDownloadContext.getEnigmaAssetDownloadManager().getDownloadsInProgress(userId,resultHandler);
    }
}
