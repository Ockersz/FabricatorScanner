package com.example.fabricatorscanner.ui.home;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
// OEM ScanManager (works only on scanner devices)
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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.fabricatorscanner.R;
import com.example.fabricatorscanner.databinding.FragmentHomeBinding;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import android.widget.ArrayAdapter;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private ScanManager mScanManager;
    private BroadcastReceiver mScanReceiver;
    private MediaPlayer mediaPlayer;

    private List<String> mattressList;
    private MattressAdapter mattressAdapter;

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

        // Disable manual typing only for fabricator
        disableManualInput(binding.textFabricator);

        // Mattress field editable
        binding.textMattress.setFocusable(true);
        binding.textMattress.setFocusableInTouchMode(true);
        binding.textMattress.setCursorVisible(true);

        // Force ALL CAPS keyboard (letters uppercase, numbers allowed)
        binding.textMattress.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        );

        // Setup Shift dropdown
        ArrayAdapter<String> shiftAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                new String[]{"D", "N"} // Only allow D or N
        );
        binding.dropdownShift.setAdapter(shiftAdapter);

        // Try to initialize ScanManager safely
        try {
            mScanManager = new ScanManager();
            mScanManager.openScanner();
            mScanManager.switchOutputMode(0); // intent broadcast mode
        } catch (Exception e) {
            e.printStackTrace();
            mScanManager = null; // gracefully ignore if not supported
        }

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

        // Setup RecyclerView for mattresses
        mattressList = new ArrayList<>();
        mattressAdapter = new MattressAdapter(mattressList);
        binding.recyclerMattresses.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerMattresses.setAdapter(mattressAdapter);

        // Add button click
        binding.buttonAdd.setOnClickListener(v -> {
            String code = binding.textMattress.getText() != null ? binding.textMattress.getText().toString().trim() : "";
            if (code.isEmpty()) {
                showSweetSnack("Please enter a mattress code", false);
                return;
            }

            if (code.startsWith("HORO") || code.startsWith("HOR") ||
                    code.startsWith("VERO") || code.startsWith("VER") ||
                    code.startsWith("LAMI")) {

                if (mattressList.contains(code)) {
                    showSweetSnack("This mattress is already added", false);
                } else {
                    mattressList.add(code);
                    mattressAdapter.notifyItemInserted(mattressList.size() - 1);
                    updateMattressCount();
                    binding.textMattress.setText(""); // clear field
                }
            } else {
                showSweetSnack("Invalid mattress code format", false);
            }
        });

        updateMattressCount();
//        binding.textFabricator.setText("FCEF-029");
        return root;
    }

    private void disableManualInput(View editText) {
        editText.setFocusable(false);
        editText.setFocusableInTouchMode(false);
        editText.setClickable(true);
        if (editText instanceof android.widget.EditText) {
            ((android.widget.EditText) editText).setInputType(InputType.TYPE_NULL);
        }
    }

    private void clearFocusFromFields() {
        if (binding != null) {
            binding.textFabricator.clearFocus();
            binding.textMattress.clearFocus();

            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            View view = requireActivity().getCurrentFocus();
            if (imm != null && view != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    private void handleScanResult(String scanResult) {
        if (mediaPlayer != null) {
            mediaPlayer.start();
        }

        if (scanResult.startsWith("FC")) {
            binding.textFabricator.setText(scanResult);
        } else if (scanResult.startsWith("HORO") ||
                scanResult.startsWith("HOR") ||
                scanResult.startsWith("VERO") ||
                scanResult.startsWith("VER") ||
                scanResult.startsWith("LAMI")) {

            if (mattressList.contains(scanResult)) {
                showSweetSnack("This mattress is already added", false);
                return;
            }

            mattressList.add(scanResult);
            mattressAdapter.notifyItemInserted(mattressList.size() - 1);
            updateMattressCount();
            binding.textMattress.setText("");
        } else {
            showSweetSnack("Invalid mattress code format", false);
        }
    }

    private void saveData() {
        String fabricator = binding.textFabricator.getText() != null
                ? binding.textFabricator.getText().toString().trim()
                : "";

        String shift = binding.dropdownShift.getText() != null
                ? binding.dropdownShift.getText().toString().trim()
                : "";

        // Must have a fabricator
        if (fabricator.isEmpty()) {
            showSweetSnack("Please scan a Fabricator", false);
            return;
        }

        // Must have at least one mattress
        if (mattressList == null || mattressList.isEmpty()) {
            showSweetSnack("Please add at least one Mattress", false);
            return;
        }

        // Must select shift (D or N only)
        if (!(shift.equals("D") || shift.equals("N"))) {
            showSweetSnack("Please select a valid Shift (D/N)", false);
            return;
        }

        binding.buttonSave.setEnabled(false);
        binding.buttonSave.setText(R.string.saving);
        setClearIconsEnabled(false);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        try {
            JSONObject json = new JSONObject();
            json.put("fabricator", fabricator);
            json.put("timestamp", timestamp);
            json.put("shift", shift);

            // Add mattress array
            org.json.JSONArray mattressArray = new org.json.JSONArray(mattressList);
            json.put("mattresses", mattressArray);

            MediaType JSON = MediaType.get("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(json.toString(), JSON);

            HttpUrl url = HttpUrl.parse("https://api.hexagonasia.com/newcommon/fabricator/scan");
//            HttpUrl url = HttpUrl.parse("http://192.168.1.22:5000/newcommon/fabricator/scan");
            if (url == null) throw new IllegalArgumentException("Invalid URL");

            Request request = new Request.Builder().url(url).post(body).build();

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

                    mainHandler.post(() -> {
                        resetButton();
                        setClearIconsEnabled(true);

                        if (code == 200 || code == 201) {
                            if (binding != null) {
                                binding.textFabricator.setText("");
                                binding.textMattress.setText("");
                            }
                            mattressList.clear();
                            mattressAdapter.notifyDataSetChanged();
                            updateMattressCount();
                            showSweetSnack("Saved successfully!", true);
                        } else if (code == 400) {
                            showSweetSnack("Error: Fabricator scan already exists or mattress not found", false);
                        } else {
                            showSweetSnack("Server error (" + code + ")", false);
                        }
                        clearFocusFromFields();
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

    @SuppressLint("SetTextI18n")
    private void updateMattressCount() {
        if (binding != null) {
            int count = mattressList != null ? mattressList.size() : 0;
            binding.textMattressCount.setText("Mattresses: " + count);
        }
    }

    private void resetButton() {
        if (binding != null) {
            binding.buttonSave.setEnabled(true);
            binding.buttonSave.setText(getString(R.string.save));
        }
    }

    private void showSweetSnack(String message, boolean success) {
        if (binding == null) return;
        Snackbar sb = Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_INDEFINITE);
        sb.setDuration(2000);
        int bg = success ? 0xFF2E7D32 : 0xFFC62828;
        int fg = 0xFFFFFFFF;
        sb.setBackgroundTint(bg);
        sb.setTextColor(fg);
        sb.setActionTextColor(fg);
        if (!success) sb.setAction("DISMISS", v -> sb.dismiss());
        sb.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupClearIcon(android.widget.EditText editText) {
        editText.setTag(true); // enabled by default

        editText.setOnTouchListener((v, event) -> {
            boolean enabled = (boolean) editText.getTag();
            if (!enabled) return false;

            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                android.graphics.drawable.Drawable rightDrawable = editText.getCompoundDrawables()[2];
                if (rightDrawable != null &&
                        event.getRawX() >= (editText.getRight() - rightDrawable.getBounds().width())) {
                    editText.setText("");
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
        if (mScanManager != null && mScanReceiver != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_DECODE);
            requireActivity().registerReceiver(mScanReceiver, filter, Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mScanManager != null && mScanReceiver != null) {
            requireActivity().unregisterReceiver(mScanReceiver);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (mScanManager != null) {
            try {
                mScanManager.closeScanner();
            } catch (Exception ignore) {}
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
