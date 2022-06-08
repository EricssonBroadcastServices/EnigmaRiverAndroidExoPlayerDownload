package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.scheduler.Requirements;
import com.redbeemedia.enigma.core.task.HandlerTaskFactory;
import com.redbeemedia.enigma.core.task.Repeater;
import com.redbeemedia.enigma.download.assetdownload.IAssetDownload;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps a list of all ongoing asset downloads and syncs with exo's DownloadManager
 */
/*package-protected*/ class EnigmaAssetDownloadManager implements IEnigmaAssetDownloadManager {
    private final int refreshRateMillis;
    private final Map<String, Download> exoPlayerDownloads = new HashMap<>();
    private final Map<String, ExoPlayerAssetDownload> ongoingDownloads = new HashMap<>();


    public EnigmaAssetDownloadManager(int refreshRateMillis) {
        this.refreshRateMillis = refreshRateMillis;
    }

    /*package-protected*/ DownloadManager.Listener createDownloadManagerListener() {
        return new DownloadManager.Listener() {
            private Repeater repeater;

            @Override
            public void onInitialized(DownloadManager downloadManager) {
                // DownloadManager should only be accessed on creating thread
                // (see com.google.android.exoplayer2.offline.DownloadManager Javadoc)
                Looper looper = Looper.myLooper();
                if(looper == null) {
                    looper = Looper.getMainLooper();
                }
                Handler handler = new Handler(looper);
                this.repeater = new Repeater(new HandlerTaskFactory(handler), Math.max(refreshRateMillis, 1000/60), () -> {
                    try {
                        updateAll(downloadManager);
                    } catch (Exception e){
                        e.printStackTrace(); // Log and continue
                    }
                });

                updateAll(downloadManager);

                repeater.setEnabled(true);
            }

            @Override
            public void onDownloadChanged(DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
                if(finalException == null) {
                    synchronized (exoPlayerDownloads) {
                        exoPlayerDownloads.put(download.request.id, download);
                    }
                    sync();
                    repeater.setEnabled(true);
                } else {
                    finalException.printStackTrace();
                }
            }

            @Override
            public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
                // First check if any state changes has happened and sync
                synchronized (exoPlayerDownloads) {
                    exoPlayerDownloads.put(download.request.id, download);
                }
                sync();
                // Then remove and sync
                synchronized (exoPlayerDownloads) {
                    exoPlayerDownloads.remove(download.request.id);
                }
                sync();
            }

            @Override
            public void onIdle(DownloadManager downloadManager) {
                repeater.setEnabled(false);
                updateAll(downloadManager);
            }

            @Override
            public void onRequirementsStateChanged(DownloadManager downloadManager, Requirements requirements, int notMetRequirements) {
            }
        };
    }

    private void sync() {
        List<Download> newAssetDownloads = new ArrayList<>();
        List<ExoPlayerAssetDownload> removedAssetDownloads = new ArrayList<>();
        Map<Download, ExoPlayerAssetDownload> updates = new HashMap<>();
        synchronized (ongoingDownloads) {
            synchronized (exoPlayerDownloads) {
                for(Map.Entry<String, Download> entry : exoPlayerDownloads.entrySet()) {
                    if(!ongoingDownloads.containsKey(entry.getKey())) {
                        newAssetDownloads.add(entry.getValue());
                    } else {
                        updates.put(entry.getValue(), ongoingDownloads.get(entry.getKey()));
                    }
                }
                for(Map.Entry<String, ExoPlayerAssetDownload> entry : ongoingDownloads.entrySet()) {
                    if(!exoPlayerDownloads.containsKey(entry.getKey())) {
                        removedAssetDownloads.add(entry.getValue());
                    }
                }
            }
            for(Download download : newAssetDownloads) {
                ExoPlayerAssetDownload assetDownload = new ExoPlayerAssetDownload(download);
                if(!assetDownload.getState().isResolved()) {
                    ongoingDownloads.put(download.request.id, assetDownload);
                }
            }
        }
        for(Map.Entry<Download, ExoPlayerAssetDownload> update : updates.entrySet()) {
            ExoPlayerAssetDownload assetDownload = update.getValue();
            assetDownload.update(update.getKey());
            if(assetDownload.getState().isResolved()) {
                removedAssetDownloads.add(assetDownload);
            }
        }
        synchronized (ongoingDownloads) {
            for(ExoPlayerAssetDownload assetDownload : removedAssetDownloads) {
                ongoingDownloads.remove(assetDownload.getContentId());
            }
        }
        for(ExoPlayerAssetDownload assetDownload : removedAssetDownloads) {
            assetDownload.onRemoved();
        }
    }

    private void updateAll(DownloadManager downloadManager) {
        List<Download> exoDownloads = downloadManager.getCurrentDownloads();
        synchronized (exoPlayerDownloads) {
            exoPlayerDownloads.clear();
            for(Download download : exoDownloads) {
                exoPlayerDownloads.put(download.request.id, download);
            }
        }
        sync();
    }


    @Override
    public void getDownloadsInProgress(IResultHandler<List<IAssetDownload>> resultHandler) {
        List<IAssetDownload> snapshot;
        synchronized (ongoingDownloads) {
            snapshot = new ArrayList<>(ongoingDownloads.values());
        }
        resultHandler.onResult(snapshot);
    }

    @Override
    public void getDownloadsInProgress(String userId, IResultHandler<List<IAssetDownload>> resultHandler) {
        List<IAssetDownload> snapshot;
        synchronized (ongoingDownloads) {
            List<IAssetDownload> snapshotForUserId = new ArrayList<>();
            for (ExoPlayerAssetDownload assetDownload : ongoingDownloads.values()) {
                if (assetDownload.getMetaData().getSession().getUserId().equals(userId)) {
                    snapshotForUserId.add(assetDownload);
                }
            }
            snapshot = new ArrayList<>(snapshotForUserId);
        }
        resultHandler.onResult(snapshot);
    }
}
