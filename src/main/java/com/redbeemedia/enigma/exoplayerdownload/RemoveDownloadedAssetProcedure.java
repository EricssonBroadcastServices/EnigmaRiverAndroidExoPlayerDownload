package com.redbeemedia.enigma.exoplayerdownload;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.error.InternalError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.task.ITask;
import com.redbeemedia.enigma.core.task.ITaskFactory;
import com.redbeemedia.enigma.core.task.TaskException;
import com.redbeemedia.enigma.core.util.AndroidThreadUtil;
import com.redbeemedia.enigma.core.util.RuntimeExceptionHandler;
import com.redbeemedia.enigma.download.DownloadedPlayable;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;
import com.redbeemedia.enigma.exoplayerintegration.ExoPlayerIntegrationContext;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/*package-protected*/ class RemoveDownloadedAssetProcedure {
    private final DownloadedPlayable.IInternalDownloadData downloadData;
    private final IResultHandler<Void> resultHandler;

    public RemoveDownloadedAssetProcedure(DownloadedPlayable.IInternalDownloadData downloadData, IResultHandler<Void> resultHandler) {
        this.downloadData = downloadData;
        this.resultHandler = resultHandler;
    }

    public void begin() {
        if(downloadData instanceof ExoPlayerDownloadData) {
            ExoPlayerDownloadData exoDownloadData = (ExoPlayerDownloadData) downloadData;
            AndroidThreadUtil.runOnUiThread(() -> {
                try {
                    sendRemoveRequest(exoDownloadData);
                } catch (Exception e) {
                    resultHandler.onError(new UnexpectedError(e));
                }
            });
        } else {
            resultHandler.onError(new InternalError("DownloadData of DownloadedPlayable did not come from ExoPlayerIntegration"));
            return;
        }
    }

    private void sendRemoveRequest(ExoPlayerDownloadData exoDownloadData) throws ProcedureException {
        new RemoveRequest(
                exoDownloadData.contentId,
                ExoPlayerDownloadContext.getDownloadManager(),
                EnigmaRiverContext.getTaskFactoryProvider().getTaskFactory(),
                resultHandler)
        .start();

    }

    private static class RemoveRequest {
        private final String contentIdToRemove;
        private final DownloadManager downloadManager;
        private final DownloadManager.Listener downloadManagerListener;
        private final ITask interruptTask;
        private final IResultHandler<Void> resultHandler;
        private volatile boolean completed = false;

        public RemoveRequest(final String contentIdToRemove, DownloadManager downloadManager, ITaskFactory taskFactory, IResultHandler<Void> resultHandler) {
            this.contentIdToRemove = contentIdToRemove;
            this.downloadManager = downloadManager;
            this.resultHandler = resultHandler;
            this.downloadManagerListener = new DownloadManager.Listener() {
                @Override
                public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
                    if(Objects.equals(download.request.id, contentIdToRemove)) {
                        RuntimeExceptionHandler exceptionHandler = new RuntimeExceptionHandler();
                        if(!completed) {
                            completed = true;
                            exceptionHandler.catchExceptions(() -> resultHandler.onResult(null));
                        }
                        exceptionHandler.catchExceptions(() -> downloadManager.removeListener(downloadManagerListener));
                        exceptionHandler.catchExceptions(() -> {
                            try {
                                interruptTask.cancel(50);
                            } catch (TaskException e) {
                                e.printStackTrace();
                            }
                        });
                        exceptionHandler.rethrowIfAnyExceptions();
                    }
                }
            };
            this.interruptTask = taskFactory.newTask(() -> {
                downloadManager.removeListener(downloadManagerListener);
                if(!completed) {
                    completed = true;
                    resultHandler.onError(new UnexpectedError(new TimeoutException("Failed to get download-removed-callback in time")));
                }
            });
        }

        public void start() {
            RuntimeExceptionHandler exceptionHandler = new RuntimeExceptionHandler();
            downloadManager.addListener(downloadManagerListener);
            exceptionHandler.catchExceptions(() -> {
                try {
                    interruptTask.startDelayed(5000);
                } catch (TaskException e) {
                    throw new RuntimeException(e);
                }
            });
            exceptionHandler.catchExceptions(() -> ExoPlayerDownloadContext.sendRemoveDownload(contentIdToRemove));
            exceptionHandler.catchExceptions(() -> EnigmaDownloadContext.getMetadataManager().clear(contentIdToRemove));
            exceptionHandler.rethrowIfAnyExceptions();
        }
    }
}
