package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.format.IMediaFormatSupportSpec;

import java.util.HashSet;
import java.util.Set;

/*package-protected*/ class DefaultMediaFormatSupportSpec implements IMediaFormatSupportSpec {
    @Override
    public boolean supports(EnigmaMediaFormat enigmaMediaFormat) {
        EnigmaMediaFormat.DrmTechnology drmTechnology = enigmaMediaFormat.getDrmTechnology();
        return drmTechnology == EnigmaMediaFormat.DrmTechnology.NONE ||
               drmTechnology == EnigmaMediaFormat.DrmTechnology.WIDEVINE;
    }

    @Override
    public Set<EnigmaMediaFormat> getSupportedFormats() {
        EnigmaMediaFormat dash = new EnigmaMediaFormat(EnigmaMediaFormat.StreamFormat.DASH, EnigmaMediaFormat.DrmTechnology.NONE);
        EnigmaMediaFormat dashWidevine = new EnigmaMediaFormat(EnigmaMediaFormat.StreamFormat.DASH, EnigmaMediaFormat.DrmTechnology.WIDEVINE);
        EnigmaMediaFormat hls = new EnigmaMediaFormat(EnigmaMediaFormat.StreamFormat.HLS, EnigmaMediaFormat.DrmTechnology.NONE);
        EnigmaMediaFormat mp3 = new EnigmaMediaFormat(EnigmaMediaFormat.StreamFormat.MP3, EnigmaMediaFormat.DrmTechnology.NONE);
        Set<EnigmaMediaFormat> enigmaMediaFormats = new HashSet<>();
        enigmaMediaFormats.add(dash);
        enigmaMediaFormats.add(hls);
        enigmaMediaFormats.add(mp3);
        enigmaMediaFormats.add(dashWidevine);
        return enigmaMediaFormats;
    }
}
