package de.tu_darmstadt.seemoo.nfcgate.gui.fragment;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import de.tu_darmstadt.seemoo.nfcgate.R;
import de.tu_darmstadt.seemoo.nfcgate.db.NfcCommEntry;
import de.tu_darmstadt.seemoo.nfcgate.db.SessionLog;
import de.tu_darmstadt.seemoo.nfcgate.db.SessionLogJoin;
import de.tu_darmstadt.seemoo.nfcgate.db.model.SessionLogEntryViewModel;
import de.tu_darmstadt.seemoo.nfcgate.db.model.SessionLogEntryViewModelFactory;
import de.tu_darmstadt.seemoo.nfcgate.db.worker.LogInserter;
import de.tu_darmstadt.seemoo.nfcgate.gui.log.LoggingFragment;
import de.tu_darmstadt.seemoo.nfcgate.gui.log.SessionLogEntryFragment;
import de.tu_darmstadt.seemoo.nfcgate.network.NetworkManager;
import de.tu_darmstadt.seemoo.nfcgate.network.data.NetworkStatus;
import de.tu_darmstadt.seemoo.nfcgate.nfc.NfcLogReplayer;
import de.tu_darmstadt.seemoo.nfcgate.nfc.modes.RelayMode;
import de.tu_darmstadt.seemoo.nfcgate.util.NfcComm;

public class ReplayFragment extends BaseNetworkFragment implements LoggingFragment.LogItemSelectedCallback, SessionLogEntryFragment.LogSelectedCallback {
    private static final int MENU_TOGGLE_TRANSPORT = R.id.action_toggle_transport;

    // session selection reference
    final LoggingFragment mLoggingFragment = new LoggingFragment();
    SessionLogEntryFragment mDetailFragment = null;

    // replay data
    List<NfcCommEntry> mSessionLog = null;
    boolean mOfflineReplay = true;
    String mReplayMode;
    UIReplayer mReplayer;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        // set relay action text
        v.<TextView>findViewById(R.id.txt_action).setText(getString(R.string.replay_action));

        // setup log item callback
        mLoggingFragment.setLogItemSelectedCallback(this);

        // show logging fragment
        setSessionSelectionVisible(true);
        return v;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.toolbar_replay, menu);
        updateTransportToggleTitle(menu.findItem(MENU_TOGGLE_TRANSPORT));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_TOGGLE_TRANSPORT) {
            setRemoteReplayEnabled(mOfflineReplay, true);
            reset();
            updateTransportToggleTitle(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        if (mLoggingFragment != null)
            setSessionSelectionVisible(false);

        if (mDetailFragment != null)
            setSessionChooserVisible(false, 0);

        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // database setup
        mLogInserter = new LogInserter(getActivity(), SessionLog.SessionType.REPLAY, this);

        // get preference data
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mOfflineReplay = !prefs.getBoolean("network", false);
        mReplayMode = prefs.getString("mode", "index");
        mStatusBanner.setVisibility(!mOfflineReplay);
    }

    @Override
    public void onLogItemSelected(int sessionId) {
        // hide session selection, set subtitle
        setSessionSelectionVisible(false);

        // show session detail chooser
        setSessionChooserVisible(true, sessionId);
    }

    @Override
    public void onLogSelected(long sessionId) {
        // set subtitle
        getMainActivity().getSupportActionBar().setSubtitle(getString(R.string.replay_session, sessionId));

        // hide details chooser
        setSessionChooserVisible(false, -1);

        // load session data
        ViewModelProviders.of(this, new SessionLogEntryViewModelFactory(getActivity().getApplication(), sessionId))
                .get(SessionLogEntryViewModel.class)
                .getSession()
                .observe(this, new Observer<SessionLogJoin>() {
                    boolean mOnce = true;

                    @Override
                    public void onChanged(@Nullable SessionLogJoin sessionLogJoin) {
                        if (sessionLogJoin != null && mSessionLog == null && mOnce) {
                            mOnce = false;
                            mSessionLog = sessionLogJoin.getNfcCommEntries();
                            getActivity().runOnUiThread(() -> {
                                // show reader/tag selector after session data is loaded
                                setSelectorVisible(true);
                            });
                        }
                    }
                });
    }

    @Override
    protected void reset() {
        super.reset();

        // hide banner in offline case
        mStatusBanner.setVisibility(!mOfflineReplay);

        // clear saved session data
        mSessionLog = null;

        // show session selector, hide selector and tag wait indicator
        setSessionSelectionVisible(true);
        setSelectorVisible(false);
        setTagWaitVisible(false, false);

        // reset replayer network
        if (mReplayer != null)
            mReplayer.reset();

        // clear subtitle
        getMainActivity().getSupportActionBar().setSubtitle(getString(R.string.replay_session_select));
    }

    protected void onSelect(boolean reader) {
        // quit if network check fails
        if (!mOfflineReplay && !checkNetwork())
            return;

        // print network status
        if (!mOfflineReplay)
            handleStatus(NetworkStatus.CONNECTING);

        // hide selector, show tag wait indicator
        setSelectorVisible(false);
        setTagWaitVisible(true, !reader);

        // init replayer and mode
        mReplayer = new UIReplayer(reader);
        getNfc().startMode(new UIReplayMode(reader));

        // initial tickle required for tag replay
        tickleReplayer();
    }

    void setSessionSelectionVisible(boolean visible) {
        FragmentTransaction transaction = getMainActivity().getSupportFragmentManager().beginTransaction();
        if (visible)
            transaction.replace(R.id.lay_content, mLoggingFragment).commit();
        else
            transaction.remove(mLoggingFragment).commitAllowingStateLoss();
    }

    void setSessionChooserVisible(boolean visible, int sessionId) {
        FragmentTransaction transaction = getMainActivity().getSupportFragmentManager().beginTransaction();
        if (visible) {
            mDetailFragment = SessionLogEntryFragment.newInstance(sessionId, SessionLogEntryFragment.Type.SELECT, this);
            transaction.replace(R.id.lay_content, mDetailFragment).commit();
        }
        else if (mDetailFragment != null)
            transaction.remove(mDetailFragment).commitAllowingStateLoss();
    }

    void tickleReplayer() {
        getActivity().runOnUiThread(() -> mReplayer.onReceive(null));
    }

    private void updateTransportToggleTitle(MenuItem item) {
        if (item == null)
            return;

        item.setTitle(mOfflineReplay
                ? getString(R.string.replay_switch_to_remote)
                : getString(R.string.replay_switch_to_local));
    }

    private void setRemoteReplayEnabled(boolean enabled, boolean persist) {
        mOfflineReplay = !enabled;
        mStatusBanner.setVisibility(!mOfflineReplay);

        if (persist && getActivity() != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            prefs.edit().putBoolean("network", enabled).apply();
        }
    }

    private boolean isNetworkFailure(NetworkStatus status) {
        return status == NetworkStatus.ERROR
                || status == NetworkStatus.ERROR_TLS
                || status == NetworkStatus.ERROR_TLS_CERT_UNKNOWN
                || status == NetworkStatus.ERROR_TLS_CERT_UNTRUSTED
                || status == NetworkStatus.PARTNER_LEFT;
    }

    /**
     * Offline replay mode
     */
    class UIReplayMode extends RelayMode {
        UIReplayMode(boolean reader) {
            super(reader);

            // prevent network connect in offline mode
            mOnline = !mOfflineReplay;
        }

        void runOnUI(Runnable r) {
            FragmentActivity activity = getActivity();
            if (activity != null)
                activity.runOnUiThread(r);
        }

        @Override
        public void onData(boolean isForeign, NfcComm data) {
            // log to database and UI
            mLogInserter.log(data);

            // hide wait indicator
            runOnUI(() -> setTagWaitVisible(false, false));

            // forward data to NFC or network
            super.onData(isForeign, data);
        }

        @Override
        protected void toNetwork(final NfcComm data) {
            if (!mOfflineReplay)
                // send to actual network
                super.toNetwork(data);
            else
                // simulate network send
                runOnUI(() -> mReplayer.onReceive(data));
        }

        @Override
        public void onNetworkStatus(final NetworkStatus status) {
            super.onNetworkStatus(status);

            runOnUI(() -> {
                // auto fallback to offline replay if remote forwarding is unavailable
                if (!mOfflineReplay && isNetworkFailure(status)) {
                    setRemoteReplayEnabled(false, true);
                    if (mReplayer != null)
                        mReplayer.enableOfflineFallback();

                    handleStatus(status);
                    getMainActivity().showInfo(getString(R.string.replay_fallback_local));
                    getMainActivity().supportInvalidateOptionsMenu();
                    return;
                }

                // report status
                handleStatus(status);
            });
        }
    }

    class UIReplayer implements NetworkManager.Callback {
        final NfcLogReplayer mReplayer;
        NetworkManager mReplayNetwork = null;

        UIReplayer(boolean reader) {
            mReplayer = new NfcLogReplayer(reader, mReplayMode, mSessionLog);

            if (!mOfflineReplay) {
                mReplayNetwork = new NetworkManager(getMainActivity(), this);
                mReplayNetwork.connect();
            }
        }

        void reset() {
            if (mReplayNetwork != null)
                mReplayNetwork.disconnect();
        }

        void enableOfflineFallback() {
            if (mReplayNetwork != null) {
                mReplayNetwork.disconnect();
                mReplayNetwork = null;
            }
        }

        @Override
        public void onReceive(NfcComm data) {
            // get response
            NfcComm response = mReplayer.getResponse(data);

            if (response != null && mOfflineReplay)
                // simulate network receive
                getNfc().handleData(true, response);
            else if (response != null)
                // actual network receive
                mReplayNetwork.send(response);

            // if we need to take action, schedule a tickle
            if (!mReplayer.shouldWait())
                tickleReplayer();
        }

        @Override
        public void onNetworkStatus(final NetworkStatus status) {
            // report status
            final FragmentActivity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    if (!mOfflineReplay && isNetworkFailure(status)) {
                        setRemoteReplayEnabled(false, true);
                        enableOfflineFallback();
                        handleStatus(status);
                        getMainActivity().showInfo(getString(R.string.replay_fallback_local));
                        getMainActivity().supportInvalidateOptionsMenu();
                        return;
                    }

                    handleStatus(status);
                });
            }
        }
    }
}
