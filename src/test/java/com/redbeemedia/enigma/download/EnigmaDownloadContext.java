package com.redbeemedia.enigma.download;

import com.redbeemedia.enigma.core.context.IModuleInitializationSettings;
import com.redbeemedia.enigma.core.format.EnigmaMediaFormat;
import com.redbeemedia.enigma.core.format.IMediaFormatSelector;
import com.redbeemedia.enigma.core.format.SimpleMediaFormatSelector;

public class EnigmaDownloadContext {
    private static Initialization lastInitialization = null;

    public static class Initialization implements IModuleInitializationSettings {
        private IMediaFormatSelector defaultDownloadFormatSelector = new SimpleMediaFormatSelector(
                EnigmaMediaFormat.DASH().unenc(),
                EnigmaMediaFormat.DASH().widevine()
        );
        private IMetadataManager metadataManager = new MockMetadataManager();


        public IMediaFormatSelector getDefaultDownloadFormatSelector() {
            return defaultDownloadFormatSelector;
        }

        public Initialization setDefaultDownloadFormatSelector(IMediaFormatSelector defaultDownloadFormatSelector) {
            this.defaultDownloadFormatSelector = defaultDownloadFormatSelector;
            return this;
        }

        public IMetadataManager getMetadataManager() {
            return metadataManager;
        }

        public Initialization setMetadataManager(IMetadataManager metadataManager) {
            this.metadataManager = metadataManager;
            return this;
        }
    }

    public static IMediaFormatSelector getDefaultDownloadFormatSelector() {
        return lastInitialization.defaultDownloadFormatSelector;
    }

    public static IMetadataManager getMetadataManager() {
        return lastInitialization.metadataManager;
    }

    public static void resetInitialize(Initialization initialization) {
        lastInitialization = initialization;
    }
}
