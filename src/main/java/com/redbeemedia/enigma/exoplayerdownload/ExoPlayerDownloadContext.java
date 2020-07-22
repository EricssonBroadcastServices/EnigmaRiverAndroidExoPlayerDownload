package com.redbeemedia.enigma.exoplayerdownload;

import android.app.Application;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.offline.DownloadService;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.context.IModuleContextInitialization;
import com.redbeemedia.enigma.core.context.exception.ModuleInitializationException;
import com.redbeemedia.enigma.download.EnigmaDownload;
import com.redbeemedia.enigma.download.IEnigmaDownloadImplementation;

import java.lang.reflect.Method;

public class ExoPlayerDownloadContext {
    private static final String NAME = ExoPlayerDownloadContext.class.getSimpleName();

    private static volatile InitializedContext initializedContext = null;

    private ExoPlayerDownloadContext() {} // Disable instantiation

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

    public static void sendAddDownload(DownloadRequest downloadRequest, boolean foreground) {
        assertInitialized();
        DownloadService.sendAddDownload(
                initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                downloadRequest,
                foreground);
    }

    public static void sendRemoveDownload(String contentId, boolean foreground) {
        assertInitialized();
        DownloadService.sendRemoveDownload(initializedContext.application,
                EnigmaExoPlayerDownloadService.class,
                contentId,
                foreground);
    }

    private static class InitializedContext {
        private final Application application;
        private final DataSource.Factory dataSourceFactory;
        private final RenderersFactory renderersFactory;

        public InitializedContext(IModuleContextInitialization initialization) {
            this.application = initialization.getApplication();

            dataSourceFactory = new DefaultDataSourceFactory(application,"enigma_river_downloader");

            renderersFactory = new DefaultRenderersFactory(application);
        }
    }
}
