package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.download.assetdownload.IAssetDownload;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import java.util.List;

/*package-protected*/ interface IEnigmaAssetDownloadManager {
    void getDownloadsInProgress(IResultHandler<List<IAssetDownload>> resultHandler);

    void getDownloadsInProgress(String userId, IResultHandler<List<IAssetDownload>> resultHandler);
}
