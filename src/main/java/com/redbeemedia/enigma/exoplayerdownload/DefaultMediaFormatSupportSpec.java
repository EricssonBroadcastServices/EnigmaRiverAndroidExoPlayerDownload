package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.format.IMediaFormatSupportSpec;

/*package-protected*/ class DefaultMediaFormatSupportSpec implements IMediaFormatSupportSpec {
    @Override
    public boolean supports(EnigmaMediaFormat enigmaMediaFormat) {
        EnigmaMediaFormat.DrmTechnology drmTechnology = enigmaMediaFormat.getDrmTechnology();
        return drmTechnology == EnigmaMediaFormat.DrmTechnology.NONE ||
               drmTechnology == EnigmaMediaFormat.DrmTechnology.WIDEVINE;
    }
}
