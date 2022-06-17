package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.download.assetdownload.IAssetDownload;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import java.util.List;

/*package-protected*/ interface IEnigmaAssetDownloadManager {
    void getDownloadsInProgress(IResultHandler<List<IAssetDownload>> resultHandler);

    void getDownloadsInProgress(ISession session, IResultHandler<List<IAssetDownload>> resultHandler);
}
