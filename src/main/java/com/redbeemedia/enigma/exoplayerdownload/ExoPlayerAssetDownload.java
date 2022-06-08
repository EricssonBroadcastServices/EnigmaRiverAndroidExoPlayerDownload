package com.redbeemedia.enigma.exoplayerdownload;

import android.os.Handler;
import android.util.Log;

import com.google.android.exoplayer2.offline.Download;
import com.redbeemedia.enigma.core.error.EnigmaError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.player.RejectReason;
import com.redbeemedia.enigma.core.player.controls.IControlResultHandler;
import com.redbeemedia.enigma.core.time.Duration;
import com.redbeemedia.enigma.core.util.Collector;
import com.redbeemedia.enigma.core.util.HandlerWrapper;
import com.redbeemedia.enigma.core.util.IStateMachine;
import com.redbeemedia.enigma.core.util.OpenContainer;
import com.redbeemedia.enigma.core.util.OpenContainerUtil;
import com.redbeemedia.enigma.core.util.Timeouter;
import com.redbeemedia.enigma.download.EnigmaDownloadContext;
import com.redbeemedia.enigma.download.assetdownload.AssetDownloadState;
import com.redbeemedia.enigma.download.assetdownload.AssetDownloadStateMachineFactory;
import com.redbeemedia.enigma.download.assetdownload.IAssetDownload;
import com.redbeemedia.enigma.download.listener.BaseAssetDownloadListener;
import com.redbeemedia.enigma.download.listener.IAssetDownloadListener;
import com.redbeemedia.enigma.download.resulthandler.BaseResultHandler;

import java.util.concurrent.TimeoutException;

/*package-protected*/ class ExoPlayerAssetDownload implements IAssetDownload {
    private static final String TAG = "ExoDownload";
    private static final Duration CONTROL_REQUEST_TIMEOUT = Duration.seconds(1);

    private final String contentId;
    private final DownloadedAssetMetaData metaData;
    private final ListenerCollector collector = new ListenerCollector();

    private final OpenContainer<Float> progress = new OpenContainer<>(null);
    private final StateCalculator stateCalculator;
    private final IStateMachine<AssetDownloadState> stateMachine;


    public ExoPlayerAssetDownload(Download download) {
        this.contentId = download.request.id;
        this.metaData = DownloadedAssetMetaData.fromBytes(EnigmaDownloadContext.getMetadataManager().load(contentId));

        this.stateCalculator = new StateCalculator(download, false);
        this.stateMachine = AssetDownloadStateMachineFactory.create(stateCalculator.calculateState());
        this.stateMachine.addListener((from, to) -> collector.onStateChanged(from, to));

        update(download);
    }

    /*package-protected*/ String getContentId() {
        return contentId;
    }

    @Override
    public String getAssetId() {
        return metaData != null ? metaData.getAssetId() : "N/A";
    }

    @Override
    public float getProgress() {
        return OpenContainerUtil.getValueSynchronized(progress);
    }

    public void onRemoved() {
        stateCalculator.removed = true;
        stateMachine.setState(stateCalculator.calculateState());
    }

    public void update(Download download) {
        if(download == null) {
            return;
        }
        stateCalculator.download = download;
        if(download.state == Download.STATE_COMPLETED) {
            stateCalculator.completed = true;
        }
        stateMachine.setState(stateCalculator.calculateState());

        float newProgress;
        if(stateMachine.getState() == AssetDownloadState.DONE) {
            newProgress = 1f;
        } else {
            newProgress = download.getPercentDownloaded() / 100f;
        }
        OpenContainerUtil.setValueSynchronized(progress, newProgress, (oldValue, newValue) -> {
            if(oldValue != null) { //Don't send progress changed event when going from null
                collector.onProgressChanged(oldValue, newValue);
            }
        });
    }

    @Override
    public AssetDownloadState getState() {
        return stateMachine.getState();
    }

    public DownloadedAssetMetaData getMetaData() {
        return metaData;
    }

    @Override
    public void pauseDownload(IControlResultHandler controlResultHandler) {
        AssetDownloadState state = stateMachine.getState();
        if(state != AssetDownloadState.IN_PROGRESS) {
            controlResultHandler.onRejected(RejectReason.incorrectState("Can only pause when state is "+AssetDownloadState.IN_PROGRESS+", but state was "+state));
            return;
        }

        sendRequestAndWaitForState(
                () -> ExoPlayerDownloadContext.sendPauseDownload(contentId),
                AssetDownloadState.PAUSED,
                controlResultHandler);
    }

    @Override
    public void pauseDownload() {
        pauseDownload(new DefaultControlResultHandler("Pause"));
    }

    @Override
    public void resumeDownload(IControlResultHandler controlResultHandler) {
        AssetDownloadState state  = stateMachine.getState();
        if(state.isResolved()) {
            controlResultHandler.onRejected(RejectReason.incorrectState("Download has resolved state ("+state+")"));
            return;
        }
        if(state == AssetDownloadState.IN_PROGRESS) {
            // If download is already in progress we still ask ExoPlayer to resume the download.
            // This is to ensure resumeDownload restarts any stuck downloads.
            try {
                ExoPlayerDownloadContext.sendResumeDownload(contentId);
            } catch (RuntimeException e) {
                controlResultHandler.onError(new UnexpectedError(e));
                return;
            }
            controlResultHandler.onDone();
            return;
        }

        sendRequestAndWaitForState(
                () -> ExoPlayerDownloadContext.sendResumeDownload(contentId),
                AssetDownloadState.IN_PROGRESS,
                controlResultHandler);
    }

    @Override
    public void resumeDownload() {
        resumeDownload(new DefaultControlResultHandler("Resume"));
    }

    private void sendRequestAndWaitForState(Runnable sendRequest, AssetDownloadState waitForState, IControlResultHandler controlResultHandler) {
        Timeouter timeouter = new Timeouter(CONTROL_REQUEST_TIMEOUT);
        IAssetDownloadListener listener = new BaseAssetDownloadListener() {
            @Override
            public void onStateChanged(AssetDownloadState oldState, AssetDownloadState newState) {
                if(newState == waitForState) {
                    timeouter.cancel();
                    controlResultHandler.onDone();
                }
            }
        };
        timeouter.setOnResolve(() -> removeListener(listener));
        timeouter.setOnTimeout(() -> {
            controlResultHandler.onError(new UnexpectedError(new TimeoutException("Timeout!")));
        });
        timeouter.start();
        try {
            addListener(listener);
            sendRequest.run();
        } catch (RuntimeException e) {
            timeouter.cancel();
            controlResultHandler.onError(new UnexpectedError(e));
            return;
        }
    }

    @Override
    public void cancelDownload(IControlResultHandler controlResultHandler) {
        AssetDownloadState state = stateMachine.getState();
        if(state.isResolved()) {
            controlResultHandler.onRejected(RejectReason.incorrectState("Download has resolved state ("+state+")"));
            return;
        }
        try {
            new RemoveDownloadedAssetProcedure(new ExoPlayerDownloadData(contentId, metaData), new BaseResultHandler<Void>() {
                @Override
                public void onResult(Void result) {
                    controlResultHandler.onDone();
                }

                @Override
                public void onError(EnigmaError error) {
                    controlResultHandler.onError(error);
                }
            }).begin();
        } catch (Exception e) {
            controlResultHandler.onError(new UnexpectedError(e));
        }
    }

    @Override
    public void cancelDownload() {
        cancelDownload(new DefaultControlResultHandler("Cancel-request"));
    }


    @Override
    public boolean addListener(IAssetDownloadListener listener) {
        return collector.addListener(listener);
    }

    @Override
    public boolean addListener(IAssetDownloadListener listener, Handler handler) {
        return collector.addListener(listener, new HandlerWrapper(handler));
    }

    @Override
    public boolean removeListener(IAssetDownloadListener listener) {
        return collector.removeListener(listener);
    }

    private static class DefaultControlResultHandler implements IControlResultHandler {
        private final String operation;

        public DefaultControlResultHandler(String operation) {
            this.operation = operation;
        }

        @Override
        public void onRejected(IRejectReason reason) {
            Log.d(TAG, operation+" rejected: "+reason.getDetails());
        }

        @Override
        public void onCancelled() {
            Log.d(TAG, operation+" cancelled");
        }

        @Override
        public void onError(EnigmaError error) {
            error.logStackTrace(TAG);
        }

        @Override
        public void onDone() {
        }
    }

    private static class StateCalculator {
        private Download download;
        private boolean completed;
        private boolean removed;

        public StateCalculator(Download download, boolean removed) {
            this.download = download;
            this.removed = removed;
            this.completed = download.state == Download.STATE_COMPLETED;
        }

        public AssetDownloadState calculateState() {
            if(completed) {
                return AssetDownloadState.DONE;
            } else if(removed) {
                return AssetDownloadState.CANCELLED;
            }

            switch (download.state) {
                case Download.STATE_STOPPED:
                    return AssetDownloadState.PAUSED;

                case Download.STATE_COMPLETED:
                    return AssetDownloadState.DONE;

                case Download.STATE_QUEUED:
                case Download.STATE_DOWNLOADING:
                case Download.STATE_RESTARTING:
                    return AssetDownloadState.IN_PROGRESS;

                case Download.STATE_FAILED:
                    return AssetDownloadState.FAILED;

                case Download.STATE_REMOVING:
                    return AssetDownloadState.CANCELLED;

                default:
                    throw new RuntimeException("Unknown ExoPlayer Download-state: "+download.state);
            }
        }
    }

    private static class ListenerCollector extends Collector<IAssetDownloadListener> implements IAssetDownloadListener {
        public ListenerCollector() {
            super(IAssetDownloadListener.class);
        }

        @Override
        public void _dont_implement_IAssetDownloadListener___instead_extend_BaseAssetDownloadListener_() {
            // We want to get compile time errors if interface is changed.
        }

        @Override
        public void onProgressChanged(float oldProgress, float newProgress) {
            forEach(listener -> listener.onProgressChanged(oldProgress, newProgress));
        }

        @Override
        public void onStateChanged(AssetDownloadState oldState, AssetDownloadState newState) {
            forEach(listener -> listener.onStateChanged(oldState, newState));
        }
    }
}
