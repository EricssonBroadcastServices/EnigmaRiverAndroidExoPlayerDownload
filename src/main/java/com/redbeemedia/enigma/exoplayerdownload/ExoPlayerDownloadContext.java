package com.redbeemedia.enigma.exoplayerdownload;

import android.app.Application;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.offline.WritableDownloadIndex;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.context.IModuleContextInitialization;
import com.redbeemedia.enigma.core.context.IModuleInfo;
import com.redbeemedia.enigma.core.context.IModuleInitializationSettings;
import com.redbeemedia.enigma.core.context.ModuleInfo;
import com.redbeemedia.enigma.core.context.exception.ModuleInitializationException;
import com.redbeemedia.enigma.core.format.IMediaFormatSupportSpec;
import com.redbeemedia.enigma.download.EnigmaDownload;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.IEnigmaDownloadImplementation;

import java.io.File;
import java.lang.reflect.Method;

public class ExoPlayerDownloadContext {
    private static final String NAME = ExoPlayerDownloadContext.class.getSimpleName();

    private static volatile InitializedContext initializedContext = null;

    private ExoPlayerDownloadContext() {} // Disable instantiation

    public static final IModuleInfo<Initialization> MODULE_INFO = new ModuleInfo<Initialization>(ExoPlayerDownloadContext.class) {
        @Override
        public Initialization createInitializationSettings() {
            return new Initialization();
        }
    };

    public static class Initialization implements IModuleInitializationSettings {
        private String userAgent = "enigma_river_downloader";
        private IMediaFormatSupportSpec downloadSupportSpec = new DefaultMediaFormatSupportSpec();
        private int downloadManagerRefreshRateMillis = 500;

        public String getUserAgent() {
            return userAgent;
        }

        public Initialization setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public void setDownloadSupportSpec(IMediaFormatSupportSpec downloadSupportSpec) {
            this.downloadSupportSpec = downloadSupportSpec;
        }

        public IMediaFormatSupportSpec getDownloadSupportSpec() {
            return downloadSupportSpec;
        }

        public Initialization setDownloadManagerRefreshRateMillis(int downloadManagerRefreshRateMillis) {
            this.downloadManagerRefreshRateMillis = downloadManagerRefreshRateMillis;
            return this;
        }

        public int getDownloadManagerRefreshRateMillis() {
            return downloadManagerRefreshRateMillis;
        }
    }

    /**
     * Called by core module through reflection.
     */
    private static synchronized void initialize(IModuleContextInitialization initialization) throws ModuleInitializationException {
        if(initializedContext == null) {
            initializedContext = new InitializedContext(initialization);

            registerDownloadImplementation(new EnigmaDownloadImplementation());
        } else {
            throw new IllegalStateException(NAME+" already initialized.");
        }
    }

    public static DataSource.Factory getDataSourceFactory() {
        assertInitialized();
        return initializedContext.dataSourceFactory;
    }


    public static RenderersFactory getRenderersFactory() {
        assertInitialized();
        return initializedContext.renderersFactory;
    }

    /*package-protected*/ static IMediaFormatSupportSpec getDownloadSupportSpec() {
        assertInitialized();
        return initializedContext.downloadSupportSpec;
    }

    public static DownloadManager getDownloadManager() {
        assertInitialized();
        return initializedContext.downloadManager;
    }

    public static Cache getDownloadCache() {
        assertInitialized();
        return initializedContext.downloadCache;
    }

    private static void registerDownloadImplementation(IEnigmaDownloadImplementation downloadImplementation) throws ModuleInitializationException {
        try {
            Method method = EnigmaDownload.class.getDeclaredMethod("registerDownloadImplementation", IEnigmaDownloadImplementation.class);
            boolean accessible = method.isAccessible();
            method.setAccessible(true);
            try {
                try {
                    method.invoke(null, downloadImplementation);
                } catch (Exception e) {
                    throw new ModuleInitializationException(e);
                }
            } finally {
                method.setAccessible(accessible);
            }
        } catch (NoSuchMethodException e) {
            throw new ModuleInitializationException("Could not register enigma download implementation" ,e);
        }
    }

    public static synchronized void assertInitialized() {
        if(initializedContext == null) {
            // If EnigmaRiverContext is not yet initialized,
            // getVersion() will throw an exception. This
            // indicates that the reason this module is not
            // yet initialized is that the parent module is
            // not initialized.
            String version = EnigmaRiverContext.getVersion();
            throw new IllegalStateException(NAME+" was not initialized from core module. Make sure "+version+" is used for all Enigma River SDK modules.");
        }
    }

    /*package-protected*/ static void sendAddDownload(DownloadRequest downloadRequest) {
        assertInitialized();
        DownloadService.sendAddDownload(
                initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                downloadRequest,
                false);
    }

    /*package-preotected*/ static void sendRemoveDownload(String contentId) {
        assertInitialized();
        DownloadService.sendRemoveDownload(initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                contentId,
                false);
    }

    /*package-protected*/ static void sendPauseDownload(String contentId) {
        assertInitialized();
        int stopReason = EnigmaExoPlayerDownloadService.STOP_REASON_PAUSED;
        ExoPlayerDownloadContext.getDownloadManager().setStopReason(contentId, stopReason);
        EnigmaExoPlayerDownloadService.sendSetStopReason(
                initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                contentId,
                stopReason,
                false);
    }

    /*package-protected*/ static void sendResumeDownload(String contentId) {
        assertInitialized();
        int stopReason = Download.STOP_REASON_NONE;
        ExoPlayerDownloadContext.getDownloadManager().setStopReason(contentId, stopReason);
        EnigmaExoPlayerDownloadService.sendSetStopReason(
                initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                contentId,
                stopReason,
                false);
    }

    /*package-protected*/ static IEnigmaAssetDownloadManager getEnigmaAssetDownloadManager() {
        assertInitialized();
        return initializedContext.enigmaAssetDownloadManager;
    }

    private static class InitializedContext {
        private final Application application;
        private final DataSource.Factory dataSourceFactory;
        private final RenderersFactory renderersFactory;
        private final IMediaFormatSupportSpec downloadSupportSpec;
        private final Cache downloadCache;
        private final DownloadManager downloadManager;
        private final IEnigmaAssetDownloadManager enigmaAssetDownloadManager;

        public InitializedContext(IModuleContextInitialization initialization) {
            this.application = initialization.getApplication();

            ExoDatabaseProvider databaseProvider = new ExoDatabaseProvider(application);
            WritableDownloadIndex downloadIndex = new DefaultDownloadIndex(databaseProvider);

            File downloadDirectory = application.getFilesDir();
            File downloadContentDirectory = new File(downloadDirectory, "downloads");

            downloadCache =
                    new SimpleCache(downloadContentDirectory, new NoOpCacheEvictor(), databaseProvider);

            HttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSourceFactory("enigma_river_download");

            DownloaderConstructorHelper downloaderConstructorHelper = new DownloaderConstructorHelper(downloadCache, httpDataSourceFactory);
            downloadManager = new DownloadManager(
                    application,
                    downloadIndex,
                    new DefaultDownloaderFactory(downloaderConstructorHelper));

            Initialization moduleSettings = initialization.getModuleSettings(MODULE_INFO);

            downloadSupportSpec = moduleSettings.downloadSupportSpec;

            dataSourceFactory = new DefaultDataSourceFactory(application, moduleSettings.userAgent);

            renderersFactory = new DefaultRenderersFactory(application);

            downloadManager.addListener(new DownloadManager.Listener() {
                @Override
                public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
                    String contentId = download.request.id;
                    EnigmaDownloadContext.getMetadataManager().clear(contentId);
                }

                @Override
                public void onDownloadChanged(DownloadManager downloadManager, Download download) {
                    if (download.state == Download.STATE_COMPLETED) {
                        DownloadedAssetMetaData info = DownloadedAssetMetaData.fromBytes(EnigmaDownloadContext.getMetadataManager().load(download.request.id));
                        new UpdateBookkeeperProcedure(info.getSession(), info.getAssetId()).begin();
                    }
                }
            });

            EnigmaAssetDownloadManager defaultDownloadManager = new EnigmaAssetDownloadManager(moduleSettings.downloadManagerRefreshRateMillis);
            downloadManager.addListener(defaultDownloadManager.createDownloadManagerListener());
            enigmaAssetDownloadManager = defaultDownloadManager;
        }
    }
}
