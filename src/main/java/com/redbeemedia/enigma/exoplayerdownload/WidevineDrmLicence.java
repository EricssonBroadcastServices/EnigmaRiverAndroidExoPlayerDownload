package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Handler;

import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.HandlerWrapper;
import com.redbeemedia.enigma.core.util.ProxyCallback;
import com.redbeemedia.enigma.download.IDrmLicence;
import com.redbeemedia.enigma.download.resulthandler.IDrmLicenceRenewResultHandler;

/*package-protected*/ class WidevineDrmLicence implements IDrmLicence {
    private final String contentId;
    private final DownloadedAssetMetaData metaData;

    public WidevineDrmLicence(String contentId, DownloadedAssetMetaData metaData) {
        this.contentId = contentId;
        this.metaData = metaData;
    }

    @Override
    public long getExpiryTime() {
        DrmLicenceInfo licenceInfo = metaData.getDrmLicenceInfo();
        if(licenceInfo != null) {
            return licenceInfo.getExpirationTime();
        } else {
            return IDrmLicence.EXPIRY_TIME_UNKNOWN;
        }
    }

    @Override
    public void renew(ISession session, IDrmLicenceRenewResultHandler resultHandler) {
        if(session == null) {
            throw new NullPointerException("session must not be null");
        }
        new RenewWidevineLicenceProcedure(session, contentId, metaData, resultHandler).begin();
    }

    @Override
    public void renew(ISession session, IDrmLicenceRenewResultHandler resultHandler, Handler handler) {
        renew(session, ProxyCallback.createCallbackOnThread(new HandlerWrapper(handler), IDrmLicenceRenewResultHandler.class, resultHandler));
    }
}
