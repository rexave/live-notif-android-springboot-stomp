package demo.notification.mobile.app;

import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import demo.notification.mobile.app.dto.MessagePub;
import io.reactivex.CompletableTransformer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;

import static demo.notification.mobile.app.RestClient.ANDROID_EMULATOR_LOCALHOST;


public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_MESSAGE = "EXTRA_MESSAGE";
    private static final String TAG = "MainActivity";

    private SimpleAdapter mAdapter;
    private List<String> mDataSet = new ArrayList<>();
    private StompClient mStompClient;
    private Disposable mRestPingDisposable;
    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private RecyclerView mRecyclerView;
    private Gson mGson = new GsonBuilder().create();
    private CompositeDisposable compositeDisposable;

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mRecyclerView = findViewById(R.id.recycler_view);
        mAdapter = new SimpleAdapter(mDataSet);
        mAdapter.setHasStableIds(true);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, true));

        String uri = "wss://" + ANDROID_EMULATOR_LOCALHOST + ":" + RestClient.SERVER_PORT + "/websocket-vanilla";
        Map<String, String> parametres = Map.of("Cookie", "JSESSIONID=DFE9711EDD07BBDF5016284435BB78D1");
        mStompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, uri, parametres);

        resetSubscriptions();
    }


    public void disconnectStomp(View view) {
        mStompClient.disconnect();
    }

    public static final String LOGIN = "login";

    public static final String PASSCODE = "passcode";

    public void connectStomp(View view) {

        List<StompHeader> headers = new ArrayList<>();

        mStompClient.withClientHeartbeat(10000).withServerHeartbeat(10000);

        resetSubscriptions();

        Disposable dispLifecycle = mStompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(lifecycleEvent -> {
                    switch (lifecycleEvent.getType()) {
                        case OPENED:
                            toast("Stomp connection opened");
                            break;
                        case ERROR:
                            Log.e(TAG, "Stomp connection error", lifecycleEvent.getException());
                            toast("Stomp connection error");
                            break;
                        case CLOSED:
                            toast("Stomp connection closed");
                            resetSubscriptions();
                            break;
                        case FAILED_SERVER_HEARTBEAT:
                            toast("Stomp failed server heartbeat");
                            break;
                    }
                });

        compositeDisposable.add(dispLifecycle);

        // Receive greetings
        Disposable dispTopic = mStompClient.topic("/topic/prive")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(topicMessage -> {
                    Log.d(TAG, "Received " + topicMessage.getPayload());
                    addItem(mGson.fromJson(topicMessage.getPayload(), MessagePub.class));
                }, throwable -> {
                    Log.e(TAG, "Error on subscribe topic", throwable);
                });

        compositeDisposable.add(dispTopic);

        mStompClient.connect(headers);
    }

    public void sendEchoViaStomp(View v) {
//        if (!mStompClient.isConnected()) return;
        compositeDisposable.add(mStompClient.send("/topic/hello-msg-mapping", "Echo STOMP " + mTimeFormat.format(new Date()))
                .compose(applySchedulers())
                .subscribe(() -> {
                    Log.d(TAG, "STOMP echo send successfully");
                }, throwable -> {
                    Log.e(TAG, "Error send STOMP echo", throwable);
                    toast(throwable.getMessage());
                }));
    }

    public void sendEchoViaRest(View v) {
        mRestPingDisposable = RestClient.getInstance().getExampleRepository()
                .sendRestEcho("Echo REST " + mTimeFormat.format(new Date()))
                .compose(applySchedulers())
                .subscribe(() -> {
                    Log.d(TAG, "REST echo send successfully");
                }, throwable -> {
                    Log.e(TAG, "Error send REST echo", throwable);
                    toast(throwable.getMessage());
                });
    }

    private void addItem(MessagePub messagePub) {
        mDataSet.add(messagePub.getContenu() + " - " + mTimeFormat.format(new Date()));
        mAdapter.notifyDataSetChanged();
        mRecyclerView.smoothScrollToPosition(mDataSet.size() - 1);
    }

    private void toast(String text) {
        Log.i(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    protected CompletableTransformer applySchedulers() {
        return upstream -> upstream
                .unsubscribeOn(Schedulers.newThread())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private void resetSubscriptions() {
        if (compositeDisposable != null) {
            compositeDisposable.dispose();
        }
        compositeDisposable = new CompositeDisposable();
    }

    @Override
    protected void onDestroy() {
        mStompClient.disconnect();

        if (mRestPingDisposable != null) mRestPingDisposable.dispose();
        if (compositeDisposable != null) compositeDisposable.dispose();
        super.onDestroy();
    }
}