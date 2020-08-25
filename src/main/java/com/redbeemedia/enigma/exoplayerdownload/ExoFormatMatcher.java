package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONObject;

import java.util.Locale;

/*package-protected*/ class ExoFormatMatcher implements IFormatMatcher {
    private final int dashRole;
    private final String dashLang;
    private final int dashChannels;

    public ExoFormatMatcher(JSONObject jsonObject) {
        this.dashRole = parseDashRoleSchemeValue(jsonObject.optString("dashRole", null));
        this.dashLang = Util.normalizeLanguageCode(jsonObject.optString("dashLang", null));
        this.dashChannels = jsonObject.optInt("dashChannels", -1);
    }

    @Override
    public boolean matches(int groupIndex, Format format) {
        if(dashRole != 0 && format.roleFlags != 0) {
            if((format.roleFlags & dashRole) == 0) {
                return false;
            }
        }
        if(dashLang != null && format.language != null) {
            if(!dashLang.equalsIgnoreCase(format.language)) {
                return false;
            }
        }
        if(dashChannels != -1 && format.channelCount != Format.NO_VALUE) {
            if(dashChannels != format.channelCount) {
                return false;
            }
        }
        return true;
    }

    // Copied from DashParser in ExoPlayer
    private static int parseDashRoleSchemeValue(String value) {
        if (value == null) {
            return 0;
        }
        switch (value.toLowerCase(Locale.ENGLISH)) {
            case "main":
                return C.ROLE_FLAG_MAIN;
            case "alternate":
                return C.ROLE_FLAG_ALTERNATE;
            case "supplementary":
                return C.ROLE_FLAG_SUPPLEMENTARY;
            case "commentary":
                return C.ROLE_FLAG_COMMENTARY;
            case "dub":
                return C.ROLE_FLAG_DUB;
            case "emergency":
                return C.ROLE_FLAG_EMERGENCY;
            case "caption":
                return C.ROLE_FLAG_CAPTION;
            case "subtitle":
                return C.ROLE_FLAG_SUBTITLE;
            case "sign":
                return C.ROLE_FLAG_SIGN;
            case "description":
                return C.ROLE_FLAG_DESCRIBES_VIDEO;
            case "enhanced-audio-intelligibility":
                return C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY;
            default:
                return 0;
        }
    }
}
