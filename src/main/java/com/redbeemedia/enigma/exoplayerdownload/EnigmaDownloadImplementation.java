package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.businessunit.IBusinessUnit;
import com.redbeemedia.enigma.download.DownloadStartRequest;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.resulthandler.IDownloadStartResultHandler;
import com.redbeemedia.enigma.download.IEnigmaDownloadImplementation;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import java.util.List;

/*package-protected*/ class EnigmaDownloadImplementation implements IEnigmaDownloadImplementation {
    @Override
    public void startAssetDownload(IBusinessUnit businessUnit, DownloadStartRequest request, IDownloadStartResultHandler resultHandler) {
        new StartDownloadProcedure(businessUnit, request, resultHandler)
                .begin();
    }

    @Override
    public void getDownloadedAssets(IResultHandler<List<DownloadedPlayable>> resultHandler) {
        new GetDownloadedAssetsProcedure(resultHandler)
                .begin();
    }

    @Override
    public void removeDownloadedAsset(DownloadedPlayable.IInternalDownloadData downloadData, IResultHandler<Void> resultHandler) {
        new RemoveDownloadedAssetProcedure(downloadData, resultHandler)
                .begin();
    }
}
