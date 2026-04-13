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
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG = "TxtNovelReader";
    private static final String GITHUB_REPO = "https://github.com/1968210376/txt-novel-reader";
    private static final String VERSION_URL = GITHUB_REPO + "/raw/main/version.json";
    private static final String APK_URL = GITHUB_REPO + "/releases/latest/download/app-release.apk";
    private static final int CURRENT_VERSION = 3;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isPlaying = false;
    private List<String> chapters = new ArrayList<>();
    private int currentChapter = 0;
    private String currentContent = "";
    private float speechRate = 1.0f;

    private TextView tvContent;
    private Button btnPlayPause, btnPrev, btnNext, btnUpdate;
    private Spinner spinnerChapter;
    private SeekBar seekBarSpeed;
    private TextView tvSpeed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initTTS();
        loadChapters();
        setupListeners();
    }

    private void initViews() {
        tvContent = findViewById(R.id.tvContent);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnUpdate = findViewById(R.id.btnUpdate);
        spinnerChapter = findViewById(R.id.spinnerChapter);
        seekBarSpeed = findViewById(R.id.seekBarSpeed);
        tvSpeed = findViewById(R.id.tvSpeed);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        Log.d(TAG, "TTS onInit status: " + status);
        if (status == TextToSpeech.SUCCESS) {
            // 尝试设置中文语音
            int result = tts.setLanguage(Locale.CHINESE);
            Log.d(TAG, "setLanguage result: " + result);
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // 尝试使用中国Locale
                result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                Log.d(TAG, "setLanguage SIMPLIFIED_CHINESE result: " + result);
            }
            
            // 列出可用的语音
            Set<Locale> languages = tts.getAvailableLanguages();
            Log.d(TAG, "Available languages: " + languages.size());
            for (Locale locale : languages) {
                if (locale.getLanguage().contains("zh") || locale.getLanguage().contains("cn")) {
                    Log.d(TAG, "Chinese locale found: " + locale);
                }
            }
            
            // 设置语速
            tts.setSpeechRate(speechRate);
            
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "TTS onStart: " + utteranceId);
                }
                
                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "TTS onDone: " + utteranceId);
                    runOnUiThread(() -> {
                        isPlaying = false;
                        btnPlayPause.setText(R.string.play);
                    });
                }
                
                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "TTS onError: " + utteranceId);
                    runOnUiThread(() -> {
                        isPlaying = false;
                        btnPlayPause.setText(R.string.play);
                        Toast.makeText(MainActivity.this, "朗读出错", Toast.LENGTH_SHORT).show();
                    });
                }
            });
            
            ttsReady = true;
            runOnUiThread(() -> Toast.makeText(this, "TTS已就绪", Toast.LENGTH_SHORT).show());
        } else {
            Log.e(TAG, "TTS init failed");
            runOnUiThread(() -> Toast.makeText(this, "TTS初始化失败，请检查系统语音设置", Toast.LENGTH_LONG).show());
        }
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
                
                if (!chapters.isEmpty()) {
                    loadChapter(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载章节失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadChapter(int index) {
        if (index < 0 || index >= chapters.size()) return;
        
        currentChapter = index;
        spinnerChapter.setSelection(index);
        
        try {
            InputStream is = getAssets().open("novels/" + chapters.get(index));
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            currentContent = sb.toString();
            tvContent.setText(currentContent);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "读取章节失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        
        btnPrev.setOnClickListener(v -> {
            stopSpeaking();
            if (currentChapter > 0) {
                loadChapter(currentChapter - 1);
            }
        });
        
        btnNext.setOnClickListener(v -> {
            stopSpeaking();
            if (currentChapter < chapters.size() - 1) {
                loadChapter(currentChapter + 1);
            }
        });
        
        btnUpdate.setOnClickListener(v -> checkForUpdate());
        
        spinnerChapter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentChapter) {
                    stopSpeaking();
                    loadChapter(position);
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
                if (tts != null) {
                    tts.setSpeechRate(speechRate);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void togglePlayPause() {
        Log.d(TAG, "togglePlayPause: isPlaying=" + isPlaying + ", ttsReady=" + ttsReady);
        if (isPlaying) {
            stopSpeaking();
        } else {
            startSpeaking();
        }
    }

    private void startSpeaking() {
        if (!ttsReady) {
            Toast.makeText(this, "TTS未就绪，请稍候重试", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (currentContent.isEmpty()) {
            Toast.makeText(this, "没有内容可朗读", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tts.setSpeechRate(speechRate);
        int result = tts.speak(currentContent, TextToSpeech.QUEUE_FLUSH, null, "tts1");
        Log.d(TAG, "speak result: " + result);
        
        if (result == TextToSpeech.SUCCESS) {
            isPlaying = true;
            btnPlayPause.setText(R.string.pause);
        } else {
            Toast.makeText(this, "朗读启动失败，请检查系统TTS设置", Toast.LENGTH_LONG).show();
        }
    }

    private void stopSpeaking() {
        if (tts != null) {
            tts.stop();
        }
        isPlaying = false;
        btnPlayPause.setText(R.string.play);
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
