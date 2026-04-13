package com.example.txtnovelreader;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "TxtNovelReader";
    private static final String GITHUB_REPO = "https://github.com/1968210376/txt-novel-reader";
    private static final String VERSION_URL = GITHUB_REPO + "/raw/main/version.json";
    private static final String APK_URL = GITHUB_REPO + "/releases/latest/download/app-release.apk";
    private static final int CURRENT_VERSION = 7;

    private Handler mainHandler;
    
    private List<String> chapters = new ArrayList<>();
    private int currentChapter = 0;

    private Button btnPlayPause, btnPrev, btnNext, btnUpdate;
    private Spinner spinnerChapter, spinnerVoice;
    private SeekBar seekBarSpeed;
    private TextView tvSpeed, tvContent;
    
    // TTS 相关
    private TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;
    private boolean isPlaying = false;
    private String currentContent = "";
    private int currentReadPosition = 0;
    private float speechRate = 1.0f;
    private List<TextToSpeech.EngineInfo> voiceList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());
        
        initTts();
        initViews();
        loadChapters();
        setupListeners();
    }
    
    private void initTts() {
        textToSpeech = new TextToSpeech(this, this);
    }
    
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // 设置中文语音
            int result = textToSpeech.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 如果中文不可用，使用默认语言
                Log.w(TAG, "Chinese language not supported, using default");
                textToSpeech.setLanguage(Locale.getDefault());
            }
            
            // 设置语速
            textToSpeech.setSpeechRate(speechRate);
            
            // 设置播放监听器
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    runOnUiThread(() -> {
                        isPlaying = true;
                        btnPlayPause.setText(R.string.pause);
                    });
                }
                
                @Override
                public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        btnPlayPause.setText(R.string.play);
                    });
                }
                
                @Override
                public void onError(String utteranceId) {
                    runOnUiThread(() -> {
                        isPlaying = false;
                        btnPlayPause.setText(R.string.play);
                        Toast.makeText(MainActivity.this, R.string.tts_error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
            
            isTtsInitialized = true;
            Log.d(TAG, "TTS initialized successfully");
        } else {
            Log.e(TAG, "TTS initialization failed");
            Toast.makeText(this, R.string.tts_init_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnUpdate = findViewById(R.id.btnUpdate);
        spinnerChapter = findViewById(R.id.spinnerChapter);
        spinnerVoice = findViewById(R.id.spinnerVoice);
        seekBarSpeed = findViewById(R.id.seekBarSpeed);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvContent = findViewById(R.id.tvContent);
        
        // 初始化语速显示
        tvSpeed.setText(String.format("%.1fx", speechRate));
        seekBarSpeed.setProgress((int) ((speechRate - 0.5f) / 0.1f));
        
        // 初始化语音选择
        initVoiceSpinner();
    }
    
    private void initVoiceSpinner() {
        // 获取可用的TTS引擎
        voiceList = textToSpeech.getEngines();
        List<String> voiceNames = new ArrayList<>();
        for (TextToSpeech.EngineInfo info : voiceList) {
            voiceNames.add(info.name);
        }
        if (voiceNames.isEmpty()) {
            voiceNames.add("默认");
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, voiceNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVoice.setAdapter(adapter);
    }

    private void loadChapters() {
        try {
            AssetManager am = getAssets();
            String[] files = am.list("novels");
            if (files != null) {
                Arrays.sort(files, Comparator.naturalOrder());
                chapters.addAll(Arrays.asList(files));
                
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                    android.R.layout.simple_spinner_item, chapters);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerChapter.setAdapter(adapter);
                
                // 加载第一章内容
                if (!chapters.isEmpty()) {
                    currentContent = loadChapterContent(0);
                    updateContentDisplay();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载章节失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String loadChapterContent(int index) {
        try {
            InputStream is = getAssets().open("novels/" + chapters.get(index));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        
        btnPrev.setOnClickListener(v -> prevChapter());
        
        btnNext.setOnClickListener(v -> nextChapter());
        
        btnUpdate.setOnClickListener(v -> checkForUpdate());
        
        spinnerChapter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentChapter) {
                    // 停止当前朗读
                    stopSpeaking();
                    currentChapter = position;
                    currentContent = loadChapterContent(position);
                    currentReadPosition = 0;
                    updateContentDisplay();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        spinnerVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isTtsInitialized && position < voiceList.size()) {
                    TextToSpeech.EngineInfo selectedEngine = voiceList.get(position);
                    // 切换TTS引擎
                    textToSpeech = new TextToSpeech(MainActivity.this, MainActivity.this, selectedEngine.name);
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                speechRate = 0.5f + progress * 0.1f;
                tvSpeed.setText(String.format("%.1fx", speechRate));
                if (isTtsInitialized && textToSpeech != null) {
                    textToSpeech.setSpeechRate(speechRate);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void togglePlayPause() {
        if (!isTtsInitialized) {
            Toast.makeText(this, R.string.tts_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (isPlaying) {
            stopSpeaking();
        } else {
            startSpeaking();
        }
    }
    
    private void startSpeaking() {
        if (textToSpeech == null || currentContent.isEmpty()) {
            return;
        }
        
        // 从当前位置开始朗读
        String textToRead = currentContent.substring(currentReadPosition);
        if (textToRead.isEmpty()) {
            currentReadPosition = 0;
            textToRead = currentContent;
        }
        
        textToSpeech.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance_id");
    }
    
    private void stopSpeaking() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        isPlaying = false;
        btnPlayPause.setText(R.string.play);
    }
    
    private void prevChapter() {
        if (currentChapter > 0) {
            stopSpeaking();
            currentChapter--;
            spinnerChapter.setSelection(currentChapter);
            currentContent = loadChapterContent(currentChapter);
            currentReadPosition = 0;
            updateContentDisplay();
        }
    }
    
    private void nextChapter() {
        if (currentChapter < chapters.size() - 1) {
            stopSpeaking();
            currentChapter++;
            spinnerChapter.setSelection(currentChapter);
            currentContent = loadChapterContent(currentChapter);
            currentReadPosition = 0;
            updateContentDisplay();
        }
    }
    
    private void updateContentDisplay() {
        if (tvContent != null && currentContent != null) {
            // 显示前500字符作为预览
            String preview = currentContent.length() > 500 ? 
                currentContent.substring(0, 500) + "..." : currentContent;
            tvContent.setText(preview);
        }
    }

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                    .url(VERSION_URL)
                    .build();
                
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        int latestVersion = parseVersion(body);
                        
                        runOnUiThread(() -> {
                            if (latestVersion > CURRENT_VERSION) {
                                showUpdateDialog(latestVersion);
                            } else {
                                Toast.makeText(this, R.string.no_update, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> 
                    Toast.makeText(this, getString(R.string.update_error, e.getMessage()), 
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private int parseVersion(String json) {
        try {
            int start = json.indexOf(":") + 1;
            int end = json.indexOf("}");
            return Integer.parseInt(json.substring(start, end).trim());
        } catch (Exception e) {
            return CURRENT_VERSION;
        }
    }

    private void showUpdateDialog(int newVersion) {
        new AlertDialog.Builder(this)
            .setTitle("更新提示")
            .setMessage(getString(R.string.new_version, "v" + newVersion))
            .setPositiveButton("更新", (dialog, which) -> downloadUpdate())
            .setNegativeButton("取消", null)
            .show();
    }

    private void downloadUpdate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ActivityCompat.checkSelfPermission(this, 
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, 
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                return;
            }
        }
        
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(APK_URL))
            .setTitle("小说朗读器更新")
            .setDescription("正在下载新版本...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "txt-novel-reader.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true);
        
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        long downloadId = dm.enqueue(request);
        
        Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show();
        
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                installApk();
                unregisterReceiver(this);
            }
        };
        
        registerReceiver(receiver, 
            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), 
            Context.RECEIVER_NOT_EXPORTED);
    }

    private void installApk() {
        File apkFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
            "txt-novel-reader.apk");
        
        if (apkFile.exists()) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(this, 
                getPackageName() + ".fileprovider", apkFile);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        // 释放 TTS 资源
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
        super.onDestroy();
    }
}
