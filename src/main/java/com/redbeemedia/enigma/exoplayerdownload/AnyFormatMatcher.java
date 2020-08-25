package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.Format;

import java.util.List;

/*package-protected*/ class AnyFormatMatcher implements IFormatMatcher {
    private final List<IFormatMatcher> formatMatchers;

    public AnyFormatMatcher(List<IFormatMatcher> formatMatchers) {
        this.formatMatchers = formatMatchers;
    }

    @Override
    public boolean matches(int groupIndex, Format format) {
        for(IFormatMatcher formatMatcher : formatMatchers) {
            if(formatMatcher.matches(groupIndex, format)) {
                return true;
            }
        }
        return false;
    }
}
