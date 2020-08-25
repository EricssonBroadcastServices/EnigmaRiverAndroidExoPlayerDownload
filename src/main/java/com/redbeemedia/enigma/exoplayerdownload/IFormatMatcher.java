package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.Format;

/*package-protected*/ interface IFormatMatcher {
    boolean matches(int groupIndex, Format format);
}
