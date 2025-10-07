package com.example.fabricatorscanner.ui.home;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.device.ScanManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.fabricatorscanner.R;
import com.example.fabricatorscanner.databinding.FragmentHomeBinding;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ScanManager mScanManager;
    private BroadcastReceiver mScanReceiver;
    private MediaPlayer mediaPlayer;

    private static final String ACTION_DECODE = ScanManager.ACTION_DECODE;
    private static final String BARCODE_STRING_TAG = ScanManager.BARCODE_STRING_TAG;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        new ViewModelProvider(this).get(HomeViewModel.class);
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Disable manual typing & keyboard
        disableManualInput(binding.textFabricator);
        disableManualInput(binding.textMattress);

        // Initialize ScanManager
        mScanManager = new ScanManager();
        mScanManager.openScanner();
        mScanManager.switchOutputMode(0); // intent broadcast mode

        // Load custom beep sound from res/raw/scanner_beep.mp3
        mediaPlayer = MediaPlayer.create(requireContext(), R.raw.scanner_beep);

        // BroadcastReceiver for scan results
        mScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_DECODE.equals(intent.getAction())) {
                    String barcodeStr = intent.getStringExtra(BARCODE_STRING_TAG);
                    if (barcodeStr != null && binding != null) {
                        handleScanResult(barcodeStr.trim());
                    }
                }
            }
        };

        // Save button click handler
        binding.buttonSave.setOnClickListener(v -> saveData());
        setupClearIcon(binding.textFabricator);
        setupClearIcon(binding.textMattress);


        return root;
    }

    private void disableManualInput(View editText) {
        editText.setFocusable(false);
        editText.setFocusableInTouchMode(false);
        editText.setClickable(true);
        // Prevent keyboard popup
        if (editText instanceof android.widget.EditText) {
            ((android.widget.EditText) editText).setInputType(InputType.TYPE_NULL);
        }
    }

    private void clearFocusFromFields() {
        if (binding != null) {
            binding.textFabricator.clearFocus();
            binding.textMattress.clearFocus();

            // Hide soft keyboard if somehow visible
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = requireActivity().getCurrentFocus();
            if (imm != null && view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void handleScanResult(String scanResult) {
        // Play custom beep once
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }

        // Route scanned code into the correct field
        if (scanResult.startsWith("FC")) {
            binding.textFabricator.setText(scanResult);
        } else if (scanResult.startsWith("HORO") ||
                scanResult.startsWith("HOR")  ||
                scanResult.startsWith("VERO") ||
                scanResult.startsWith("VER")  ||
                scanResult.startsWith("LAMI")) {
            binding.textMattress.setText(scanResult);
        }
        // ignore others
    }

    private void saveData() {
        String fabricator = binding.textFabricator.getText() != null ? binding.textFabricator.getText().toString().trim() : "";
        String mattress   = binding.textMattress.getText() != null   ? binding.textMattress.getText().toString().trim()   : "";

        if (fabricator.isEmpty() || mattress.isEmpty()) {
            showSweetSnack("Please scan both Fabricator and Mattress", false);
            return;
        }

        // Disable button + show loading
        binding.buttonSave.setEnabled(false);
        binding.buttonSave.setText(R.string.saving);
        setClearIconsEnabled(false);

        // yyyy-MM-dd HH:mm:ss
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        try {
            JSONObject json = new JSONObject();
            json.put("fabricator", fabricator);
            json.put("mattress", mattress);
            json.put("timestamp", timestamp);

            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(json.toString(), JSON);

            HttpUrl url = HttpUrl.parse("https://api.hexagonasia.com/newcommon/fabricator/scan");

            if (url == null) {
                throw new IllegalArgumentException("Invalid URL");
            }

            Request request = new Request.Builder()
                    .url(url)   // safe HttpUrl, properly encoded
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mainHandler.post(() -> {
                        resetButton();
                        clearFocusFromFields();
                        setClearIconsEnabled(true);
                        showSweetSnack("Network error: " + e.getMessage(), false);
                    });
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    final int code = response.code();
                    final String resStr = response.body() != null ? response.body().string() : "";

                    mainHandler.post(() -> {
                        resetButton();
                        setClearIconsEnabled(true);

                        // Clear fields & remove focus
                        if (binding != null) {
                            binding.textFabricator.setText("");
                            binding.textMattress.setText("");
                        }
                        clearFocusFromFields();

                        if (code == 200 || code == 201) {
                            showSweetSnack("Saved successfully!", true);
                        } else if (code == 400) {
                            showSweetSnack("Error: Fabricator scan already exists or mattress not found", false);
                        } else {
                            showSweetSnack("Server error (" + code + ")", false);
                        }
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            resetButton();
            clearFocusFromFields();
            setClearIconsEnabled(true);
            showSweetSnack("Error creating request", false);
        }
    }

    private void resetButton() {
        if (binding != null) {
            binding.buttonSave.setEnabled(true);
            binding.buttonSave.setText(getString(R.string.save));
        }
    }

    /** Sweet-alert style Snackbar with colors and 2s duration */
    private void showSweetSnack(String message, boolean success) {
        if (binding == null) return;

        Snackbar sb = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_INDEFINITE);
        sb.setDuration(2000);
        int bg = success ? 0xFF2E7D32 : 0xFFC62828;
        int fg = 0xFFFFFFFF;
        sb.setBackgroundTint(bg);
        sb.setTextColor(fg);
        sb.setActionTextColor(fg);
        if (!success) {
            sb.setAction("DISMISS", v -> sb.dismiss());
        }
        sb.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupClearIcon(android.widget.EditText editText) {
        editText.setTag(true); // enabled by default

        editText.setOnTouchListener((v, event) -> {
            boolean enabled = (boolean) editText.getTag();
            if (!enabled) return false; // ignore touches if disabled

            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                if (event.getRawX() >= (editText.getRight() - editText.getCompoundDrawables()[2].getBounds().width())) {
                    editText.setText(""); // Clear text
                    return true;
                }
            }
            return false;
        });
    }

    private void setClearIconsEnabled(boolean enabled) {
        if (binding != null) {
            binding.textFabricator.setTag(enabled);
            binding.textMattress.setTag(enabled);
        }
    }



    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onResume() {
        super.onResume();
        if (mScanReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_DECODE);
            requireActivity().registerReceiver(mScanReceiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mScanReceiver != null) {
            requireActivity().unregisterReceiver(mScanReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (mScanManager != null) {
            mScanManager.closeScanner();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
