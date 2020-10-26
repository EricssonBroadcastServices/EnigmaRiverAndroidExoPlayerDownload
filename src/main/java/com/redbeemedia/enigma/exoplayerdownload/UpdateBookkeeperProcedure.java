package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.http.AuthenticatedExposureApiCall;
import com.redbeemedia.enigma.core.json.JsonObjectResponseHandler;
import com.redbeemedia.enigma.core.session.ISession;
import com.redbeemedia.enigma.core.util.UrlPath;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Inform the exposure backend about the completion of a download.
 */
/*package-protected*/ class UpdateBookkeeperProcedure {

    private final ISession session;
    private final String assetId;

    public UpdateBookkeeperProcedure(ISession session, String assetId) {
        this.session = session;
        this.assetId = assetId;
    }

    public void begin() {
        try {
            UrlPath endpoint = session.getBusinessUnit().getApiBaseUrl("v2").append("/entitlement/").append(assetId).append("/downloadcompleted");
            EnigmaRiverContext.getHttpHandler().doHttp(endpoint.toURL(), new AuthenticatedExposureApiCall("POST", session), new JsonObjectResponseHandler() {
                @Override
                protected void onSuccess(JSONObject jsonObject) throws JSONException {
                }

                @Override
                protected void onError(EnigmaError error) {
                    error.printStackTrace();
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
