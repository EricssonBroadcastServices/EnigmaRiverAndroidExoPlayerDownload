package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.redbeemedia.enigma.core.testutil.json.JsonObjectBuilder;

import org.junit.Assert;
import org.junit.Test;

public class ExoFormatMatcherTest {
    @Test
    public void testRole() {
        JsonObjectBuilder objectBuilder = new JsonObjectBuilder();
        objectBuilder.put("dashRole", "main");
        ExoFormatMatcher exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Format format = createFormat(C.ROLE_FLAG_MAIN | C.ROLE_FLAG_COMMENTARY, null);
        Assert.assertTrue(exoFormatMatcher.matches(0, format));

        objectBuilder.put("dashRole", "alternate");
        exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Assert.assertFalse(exoFormatMatcher.matches(0, format));
    }

    @Test
    public void testLanguage() {
        JsonObjectBuilder objectBuilder = new JsonObjectBuilder();
        objectBuilder.put("dashLang", "deu");
        ExoFormatMatcher exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Format format = createFormat(C.ROLE_FLAG_MAIN, null);
        Assert.assertFalse(exoFormatMatcher.matches(0, format));

        objectBuilder.put("dashLang", null);
        exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Assert.assertTrue(exoFormatMatcher.matches(0, format));

        objectBuilder.put("dashLang", "deu");
        exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        format = createFormat(C.ROLE_FLAG_MAIN, "eng");
        Assert.assertFalse(exoFormatMatcher.matches(0, format));

        format = createFormat(C.ROLE_FLAG_MAIN, "deu");
        Assert.assertTrue(exoFormatMatcher.matches(0, format));
    }

    @Test
    public void testChannelCount() {
        JsonObjectBuilder objectBuilder = new JsonObjectBuilder();
        objectBuilder.put("dashChannels", "3");
        ExoFormatMatcher exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Format format = createFormat(C.ROLE_FLAG_MAIN, null, 2);
        Assert.assertFalse(exoFormatMatcher.matches(0, format));

        format = createFormat(C.ROLE_FLAG_MAIN, null, 3);
        Assert.assertTrue(exoFormatMatcher.matches(0, format));

        objectBuilder.put("dashChannels", null);
        exoFormatMatcher = new ExoFormatMatcher(objectBuilder.getJsonObject());
        Assert.assertTrue(exoFormatMatcher.matches(0, format));
    }

    private static Format createFormat(int roleFlags, String language) {
        return Format.createContainerFormat(
                null,
                null,
                null,
                null,
                null,
                123,
                0,
                roleFlags,
                language
        );
    }

    private static Format createFormat(int roleFlags, String language, int channelCount) {
        Format format = createFormat(roleFlags, language);
        return format.copyWithContainerInfo(
                format.id,
                format.label,
                format.sampleMimeType,
                format.codecs,
                format.metadata,
                format.bitrate,
                format.width,
                format.height,
                channelCount,
                format.selectionFlags,
                format.language
        );
    }
}
