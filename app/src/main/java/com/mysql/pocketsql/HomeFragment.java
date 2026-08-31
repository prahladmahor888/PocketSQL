package com.mysql.pocketsql;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.widget.Toast;
import android.text.style.ForegroundColorSpan;
import android.text.style.ScaleXSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.text.Editable;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import org.json.JSONObject;
import org.json.JSONArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.mysql.pocketsql.engine.DatabaseEngine;
import com.mysql.pocketsql.engine.QueryResult;
import com.mysql.pocketsql.engine.SqlScriptRunner;
import com.mysql.pocketsql.BuildConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeFragment extends Fragment {

    private DatabaseEngine engine;
    private File pocketsqlDir;
    private SettingsManager settings;
    private Typeface jetbrainsMono;

    // UI elements
    private ScrollView scrollContainer;
    private LinearLayout layoutTerminalContainer;
    private LinearLayout specialKeysContainer;
    private TextView tvTerminalHistory, tvTerminalPrompt;
    private TextView btnHistoryUp, btnHistoryDown;
    private TextView btnTemplate, btnClearLine, btnClearScreen, btnSettings, btnHelp, btnCopyAll;
    private TextView btnApiKeys;
    private TextView btnConnections;
    private EditText etCommandInput;
    private HorizontalScrollView suggestionsBar;
    private LinearLayout suggestionsContainer;

    // API Server fields
    private com.mysql.pocketsql.engine.SqlApiKeyManager apiKeyManager;
    private com.mysql.pocketsql.engine.SqlApiServer apiServer;

    // State variables
    private final List<String> queryHistory = new ArrayList<>();
    private int historyIndex = -1;
    private final StringBuilder multiLineBuffer = new StringBuilder();
    private String currentDelimiter = ";";

    private String selectedExportFormat = "db";

    private final androidx.activity.result.ActivityResultLauncher<Intent> exportFileLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        performExport(uri);
                    }
                }
            });

    private final androidx.activity.result.ActivityResultLauncher<Intent> importFileLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                    android.net.Uri uri = result.getData().getData();
                    if (uri != null) {
                        performImport(uri);
                    }
                }
            });

    private AlertDialog activeSettingsDialog;

    private static final int LOGIN_STATE_USERNAME = 0;
    private static final int LOGIN_STATE_PASSWORD = 1;
    private static final int LOGIN_STATE_AUTHENTICATED = 2;
    private volatile int loginState = LOGIN_STATE_USERNAME;
    private String tempUsername = "";

    private static final int SETUP_STATE_USERNAME = 10;
    private static final int SETUP_STATE_HOST = 11;
    private static final int SETUP_STATE_PASSWORD = 12;
    private static final int SETUP_STATE_CONFIRM_PASSWORD = 13;
    
    private String setupUsername = com.mysql.pocketsql.engine.SecurityHelper.getDefaultUser();
    private String setupHost = com.mysql.pocketsql.engine.SecurityHelper.getDefaultHost();
    private String setupPassword = "";

    private String getWelcomeText() {
        String ver = "1.0.1";
        if (getContext() != null) {
            try {
                ver = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
            } catch (Exception ignored) {}
        }
        return
                "PocketSQL Monitor\n" +
                        "Server version: PocketSQL " + ver + " (PocketSQL Server)\n" +
                        "App Version: " + ver + "\n" +
                        "Connected to local PocketSQL engine.\n\n" +

                        "Ready for PocketSQL commands.\n" +
                        "Statements must end with ';' or '\\g'.\n\n" +

                        "Type 'help;' or '\\h' for help.\n" +
                        "Type '\\c' to clear the current input statement.\n\n";
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Database Engine
        File filesDir = requireContext().getFilesDir();
        pocketsqlDir = new File(filesDir, "PocketSQL");
        if (!pocketsqlDir.exists()) {
            pocketsqlDir.mkdirs();
        }
        // Initialize SQL API Helper, Engine, and Server
        com.mysql.pocketsql.engine.SqlApiHelper.init(requireContext());
        engine = com.mysql.pocketsql.engine.SqlApiHelper.getEngine();
        apiKeyManager = com.mysql.pocketsql.engine.SqlApiHelper.getApiKeyManager();
        apiServer = com.mysql.pocketsql.engine.SqlApiHelper.getApiServer();
        
        settings = new SettingsManager(requireContext());

        // Bind Views
        bindViews(view);

        // Load custom JetBrains Mono typeface
        try {
            jetbrainsMono = ResourcesCompat.getFont(requireContext(), R.font.jetbrains_mono);
        } catch (Exception e) {
            jetbrainsMono = Typeface.MONOSPACE;
        }

        // Apply saved settings to terminal immediately
        applySettings();

        // Setup login/setup prompt at startup
        if (!engine.hasUsersConfigured()) {
            engine.initializeDefaultRootUser();
        }

        // Auto-login if enabled and valid saved/default credentials exist
        boolean loggedIn = false;
        if (settings.isAutoLogin()) {
            String savedUser = settings.getLastUsername();
            String savedHost = settings.getLastHost();
            String savedPass = settings.getLastPassword();
            if (engine.authenticate(savedUser, savedPass)) {
                loginState = LOGIN_STATE_AUTHENTICATED;
                tvTerminalHistory.setText(getWelcomeText());
                refreshTerminalPrompt();
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
                loggedIn = true;
            }
        }

        if (!loggedIn) {
            loginState = LOGIN_STATE_USERNAME;
            tvTerminalHistory.setText("");
            tvTerminalPrompt.setText("Enter username: ");
            setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
        }

        // Setup SQL Terminal Screen Listener
        setupTerminal();
    }

    private void bindViews(View v) {
        scrollContainer = v.findViewById(R.id.scrollContainer);
        layoutTerminalContainer = v.findViewById(R.id.layoutTerminalContainer);
        specialKeysContainer = v.findViewById(R.id.specialKeysContainer);
        tvTerminalHistory = v.findViewById(R.id.tvTerminalHistory);
        tvTerminalHistory.setFocusable(false);
        tvTerminalHistory.setFocusableInTouchMode(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            tvTerminalHistory.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return true; }
                @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                @Override public void onDestroyActionMode(android.view.ActionMode mode) {
                    resetHistoryFocus();
                }
            });
        }
        tvTerminalPrompt = v.findViewById(R.id.tvTerminalPrompt);
        etCommandInput = v.findViewById(R.id.etCommandInput);
        btnHistoryUp   = v.findViewById(R.id.btnHistoryUp);
        btnHistoryDown = v.findViewById(R.id.btnHistoryDown);
        btnTemplate    = v.findViewById(R.id.btnTemplate);
        btnClearLine   = v.findViewById(R.id.btnClearLine);
        btnClearScreen = v.findViewById(R.id.btnClearScreen);
        btnSettings    = v.findViewById(R.id.btnSettings);
        btnHelp        = v.findViewById(R.id.btnHelp);
        btnCopyAll     = v.findViewById(R.id.btnCopyAll);
        btnApiKeys     = v.findViewById(R.id.btnApiKeys);
        btnConnections = v.findViewById(R.id.btnConnections);
        suggestionsBar = v.findViewById(R.id.suggestionsBar);
        suggestionsContainer = v.findViewById(R.id.suggestionsContainer);
    }

    private void resetHistoryFocus() {
        if (tvTerminalHistory != null) {
            tvTerminalHistory.setTextIsSelectable(false);
            tvTerminalHistory.setFocusable(false);
            tvTerminalHistory.setFocusableInTouchMode(false);
        }
        if (etCommandInput != null) {
            etCommandInput.setFocusable(true);
            etCommandInput.setFocusableInTouchMode(true);
            etCommandInput.setEnabled(true);
            etCommandInput.requestFocus();
            showKeyboard();
        }
    }

    public static class PromptLeadingMarginSpan implements android.text.style.LeadingMarginSpan.LeadingMarginSpan2 {
        private final int margin;

        public PromptLeadingMarginSpan(int margin) {
            this.margin = margin;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return first ? margin : 0;
        }

        @Override
        public void drawLeadingMargin(android.graphics.Canvas c, android.graphics.Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, android.text.Layout layout) {
        }

        @Override
        public int getLeadingMarginLineCount() {
            return 1;
        }
    }

    private boolean isApplyingPromptMargin = false;

    private void applyPromptMargin() {
        applyPromptMargin(null);
    }

    private void applyPromptMargin(Editable editable) {
        if (isApplyingPromptMargin || etCommandInput == null || tvTerminalPrompt == null) return;
        if (editable == null) editable = etCommandInput.getText();
        if (editable == null) return;

        isApplyingPromptMargin = true;
        try {
            int promptWidth = tvTerminalPrompt.getWidth();
            if (promptWidth <= 0) {
                tvTerminalPrompt.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                promptWidth = tvTerminalPrompt.getMeasuredWidth();
            }

            PromptLeadingMarginSpan[] spans = editable.getSpans(0, editable.length(), PromptLeadingMarginSpan.class);
            for (PromptLeadingMarginSpan span : spans) {
                editable.removeSpan(span);
            }

            if (promptWidth > 0) {
                editable.setSpan(new PromptLeadingMarginSpan(promptWidth), 0, editable.length(), android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            }
        } finally {
            isApplyingPromptMargin = false;
        }
    }

    private void refreshTerminalPrompt() {
        if (loginState != LOGIN_STATE_AUTHENTICATED) {
            return;
        }
        if (multiLineBuffer.length() > 0) {
            tvTerminalPrompt.setText("    -> ");
            tvTerminalPrompt.post(this::applyPromptMargin);
            return;
        }
        String prompt = settings.getPromptString();
        String active = engine.getActiveDatabase();
        if (active == null) {
            tvTerminalPrompt.setText(prompt);
        } else {
            // Insert db name before the > character
            String base = prompt.trim(); // e.g. "mysql>"
            tvTerminalPrompt.setText(base.replace(">", " [" + active + ">") + " ");
        }
        tvTerminalPrompt.post(this::applyPromptMargin);
    }

    private void setTerminalInputType(int inputType, boolean isPassword) {
        if (etCommandInput == null) return;
        etCommandInput.setFocusable(true);
        etCommandInput.setFocusableInTouchMode(true);
        etCommandInput.setEnabled(true);
        if (!isPassword) {
            // Do NOT add TYPE_TEXT_FLAG_MULTI_LINE — it overrides imeOptions and makes
            // the Enter key insert newlines instead of executing the query.
            inputType |= android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        }
        etCommandInput.setInputType(inputType);
        if (!isPassword) {
            etCommandInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
            etCommandInput.setSingleLine(false);
            etCommandInput.setHorizontallyScrolling(false);
        }
        if (getContext() != null && settings != null) {
            etCommandInput.setTypeface(settings.getTypeface(requireContext()));
        }
        if (isPassword) {
            android.view.ActionMode.Callback noOpCallback = new android.view.ActionMode.Callback() {
                @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
            };
            etCommandInput.setCustomSelectionActionModeCallback(noOpCallback);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                etCommandInput.setCustomInsertionActionModeCallback(noOpCallback);
            }
        } else {
            etCommandInput.setCustomSelectionActionModeCallback(null);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                etCommandInput.setCustomInsertionActionModeCallback(null);
            }
        }
    }

    private void showKeyboard() {
        if (etCommandInput == null || !isAdded()) return;
        etCommandInput.setFocusable(true);
        etCommandInput.setFocusableInTouchMode(true);
        etCommandInput.setEnabled(true);
        etCommandInput.requestFocus();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etCommandInput, InputMethodManager.SHOW_FORCED);
        }
    }

    private void showCopyDialog() {
        String historyText = tvTerminalHistory.getText().toString();
        if (historyText.isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_history_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        // Parent container (vertical LinearLayout with dark background)
        LinearLayout parent = new LinearLayout(requireContext());
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setBackgroundColor(Color.parseColor("#0A0A0A"));
        int dp16 = dpToPx(16);
        int dp8 = dpToPx(8);
        parent.setPadding(dp16, dp16, dp16, dp16);

        // Header title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("Copy Terminal Output");
        tvTitle.setTextColor(Color.parseColor("#00E5FF"));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        parent.addView(tvTitle);

        // Subtitle
        TextView tvSub = new TextView(requireContext());
        tvSub.setText("Select the text you want to copy by dragging, or click Copy All.");
        tvSub.setTextColor(Color.parseColor("#888888"));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp8, 0, dp16);
        tvSub.setLayoutParams(subLp);
        parent.addView(tvSub);

        // Scrollable, Selectable text field (EditText is used to allow native cursor, dragging selection with mouse, etc.)
        EditText etCopy = new EditText(requireContext());
        etCopy.setText(historyText);
        etCopy.setTextColor(Color.WHITE);
        etCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        if (jetbrainsMono != null) {
            etCopy.setTypeface(jetbrainsMono);
        } else {
            etCopy.setTypeface(Typeface.MONOSPACE);
        }
        etCopy.setLetterSpacing(0.0f);
        etCopy.setBackground(null); // No underline
        etCopy.setKeyListener(null); // Read-only!
        etCopy.setTextIsSelectable(true); // Allow selection!
        etCopy.setGravity(android.view.Gravity.TOP);
        etCopy.setHorizontallyScrolling(true);

        HorizontalScrollView hScroll = new HorizontalScrollView(requireContext());
        hScroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hScroll.addView(etCopy);

        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);
        scroll.addView(hScroll);
        parent.addView(scroll);

        // Buttons container (Horizontal LinearLayout)
        LinearLayout buttons = new LinearLayout(requireContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, dp16, 0, 0);
        buttons.setLayoutParams(btnLp);

        TextView btnClose = new TextView(requireContext());
        btnClose.setText("Close");
        btnClose.setTextColor(Color.parseColor("#AAAAAA"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnClose.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnClose.setPadding(dp16, dp8, dp16, dp8);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        buttons.addView(btnClose);

        TextView btnCopyAll = new TextView(requireContext());
        btnCopyAll.setText("Copy All");
        btnCopyAll.setTextColor(Color.parseColor("#00E5FF"));
        btnCopyAll.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnCopyAll.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnCopyAll.setPadding(dp16, dp8, dp16, dp8);
        btnCopyAll.setClickable(true);
        btnCopyAll.setFocusable(true);
        LinearLayout.LayoutParams copyAllLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        copyAllLp.setMarginStart(dp16);
        btnCopyAll.setLayoutParams(copyAllLp);
        buttons.addView(btnCopyAll);

        parent.addView(buttons);
        settings.applyFontToViewTree(parent);

        // Create Dialog
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(parent)
                .create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCopyAll.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PocketSQL Terminal History", historyText);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                android.os.PersistableBundle extras = new android.os.PersistableBundle();
                extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                clip.getDescription().setExtras(extras);
            }
            if (clipboard != null) {
                com.mysql.pocketsql.engine.AppIntegrityManager.setPrimaryClip(clipboard, clip);
                Toast.makeText(requireContext(), R.string.toast_history_copied, Toast.LENGTH_SHORT).show();
                
                // Delayed clear to prevent clipboard credential exposure
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        if (clipboard.hasPrimaryClip() && 
                            clipboard.getPrimaryClipDescription() != null && 
                            "PocketSQL Terminal History".equals(clipboard.getPrimaryClipDescription().getLabel())) {
                            com.mysql.pocketsql.engine.AppIntegrityManager.clearPrimaryClip(clipboard);
                        }
                    } catch (Exception ignored) {}
                }, 30000);
            }
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> showKeyboard());
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void showApiKeysDialog() {
        // Parent container (vertical LinearLayout with dark background)
        LinearLayout parent = new LinearLayout(requireContext());
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setBackgroundColor(Color.parseColor("#0A0A0A"));
        int dp16 = dpToPx(16);
        int dp8 = dpToPx(8);
        int dp12 = dpToPx(12);
        parent.setPadding(dp16, dp16, dp16, dp16);

        // Header Title
        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText("🔑 API Keys & Server");
        tvTitle.setTextColor(Color.parseColor("#00E5FF"));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        parent.addView(tvTitle);

        // Server Control Panel
        LinearLayout serverControlPanel = new LinearLayout(requireContext());
        serverControlPanel.setOrientation(LinearLayout.VERTICAL);
        serverControlPanel.setBackgroundColor(Color.parseColor("#151515"));
        serverControlPanel.setPadding(dp12, dp12, dp12, dp12);
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelLp.setMargins(0, dp8, 0, dp12);
        serverControlPanel.setLayoutParams(panelLp);

        LinearLayout statusRow = new LinearLayout(requireContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        TextView tvStatusLabel = new TextView(requireContext());
        tvStatusLabel.setText("Server Status: ");
        tvStatusLabel.setTextColor(Color.parseColor("#888888"));
        tvStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        statusRow.addView(tvStatusLabel);
        
        TextView tvStatusValue = new TextView(requireContext());
        tvStatusValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvStatusValue.setTypeface(Typeface.DEFAULT_BOLD);
        statusRow.addView(tvStatusValue);
        serverControlPanel.addView(statusRow);

        View spacer = new View(requireContext());
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(6));
        spacer.setLayoutParams(spacerLp);
        serverControlPanel.addView(spacer);

        LinearLayout localUrlRow = new LinearLayout(requireContext());
        localUrlRow.setOrientation(LinearLayout.HORIZONTAL);
        localUrlRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        TextView tvLocalUrl = new TextView(requireContext());
        tvLocalUrl.setTextColor(Color.WHITE);
        tvLocalUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvLocalUrl.setTypeface(Typeface.MONOSPACE);
        tvLocalUrl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        localUrlRow.addView(tvLocalUrl);
        
        TextView btnCopyLocal = new TextView(requireContext());
        btnCopyLocal.setText("Copy");
        btnCopyLocal.setTextColor(Color.parseColor("#00E5FF"));
        btnCopyLocal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCopyLocal.setPadding(dp8, dpToPx(4), dp8, dpToPx(4));
        btnCopyLocal.setClickable(true);
        btnCopyLocal.setFocusable(true);
        btnCopyLocal.setOnClickListener(v -> {
            int portVal = apiServer != null ? apiServer.getActivePort() : 8080;
            String copyUrl = "http://localhost:" + portVal + "/api/query";
            if (apiServer != null && apiServer.getBindErrorMessage() != null) {
                Toast.makeText(requireContext(), R.string.toast_copy_bind_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PocketSQL Local API URL", copyUrl);
            if (clipboard != null) {
                com.mysql.pocketsql.engine.AppIntegrityManager.setPrimaryClip(clipboard, clip);
                Toast.makeText(requireContext(), R.string.toast_local_url_copied, Toast.LENGTH_SHORT).show();
            }
        });
        localUrlRow.addView(btnCopyLocal);
        serverControlPanel.addView(localUrlRow);

        LinearLayout networkUrlRow = new LinearLayout(requireContext());
        networkUrlRow.setOrientation(LinearLayout.HORIZONTAL);
        networkUrlRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        TextView tvNetworkUrl = new TextView(requireContext());
        String hostAddress = com.mysql.pocketsql.engine.SqlApiHelper.getNetworkHostAddress();
        tvNetworkUrl.setTextColor(Color.WHITE);
        tvNetworkUrl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvNetworkUrl.setTypeface(Typeface.MONOSPACE);
        tvNetworkUrl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        networkUrlRow.addView(tvNetworkUrl);
        
        TextView btnCopyNetwork = new TextView(requireContext());
        btnCopyNetwork.setText("Copy");
        btnCopyNetwork.setTextColor(Color.parseColor("#00E5FF"));
        btnCopyNetwork.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btnCopyNetwork.setPadding(dp8, dpToPx(4), dp8, dpToPx(4));
        btnCopyNetwork.setClickable(true);
        btnCopyNetwork.setFocusable(true);
        btnCopyNetwork.setOnClickListener(v -> {
            int portVal = apiServer != null ? apiServer.getActivePort() : 8080;
            String copyUrl = "http://" + hostAddress + ":" + portVal + "/api/query";
            if (apiServer != null && apiServer.getBindErrorMessage() != null) {
                Toast.makeText(requireContext(), R.string.toast_copy_bind_failed, Toast.LENGTH_SHORT).show();
                return;
            }
            ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("PocketSQL Network API URL", copyUrl);
            if (clipboard != null) {
                com.mysql.pocketsql.engine.AppIntegrityManager.setPrimaryClip(clipboard, clip);
                Toast.makeText(requireContext(), R.string.toast_network_url_copied, Toast.LENGTH_SHORT).show();
            }
        });
        networkUrlRow.addView(btnCopyNetwork);
        serverControlPanel.addView(networkUrlRow);

        View spacer2 = new View(requireContext());
        spacer2.setLayoutParams(spacerLp);
        serverControlPanel.addView(spacer2);

        TextView btnToggleServer = new TextView(requireContext());
        btnToggleServer.setGravity(android.view.Gravity.CENTER);
        btnToggleServer.setPadding(dp16, dp8, dp16, dp8);
        btnToggleServer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnToggleServer.setTypeface(Typeface.DEFAULT_BOLD);
        btnToggleServer.setClickable(true);
        btnToggleServer.setFocusable(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnToggleServer.setLayoutParams(btnLp);
        serverControlPanel.addView(btnToggleServer);

        TextView tvGuide = new TextView(requireContext());

        Runnable refreshServerUI = new Runnable() {
            @Override
            public void run() {
                boolean running = apiServer != null && apiServer.isRunning();
                String bindErr = apiServer != null ? apiServer.getBindErrorMessage() : null;
                int portVal = apiServer != null ? apiServer.getActivePort() : 8080;
                
                if (bindErr != null) {
                    tvStatusValue.setText("BIND FAILED (" + bindErr + ")");
                    tvStatusValue.setTextColor(Color.parseColor("#FF5555"));
                    btnToggleServer.setText("START BACKGROUND SERVER");
                    btnToggleServer.setTextColor(Color.BLACK);
                    btnToggleServer.setBackgroundColor(Color.parseColor("#00E5FF"));
                    
                    tvLocalUrl.setText("Local URL: N/A");
                    tvNetworkUrl.setText("Network URL: N/A");
                } else if (running) {
                    tvStatusValue.setText("ACTIVE");
                    tvStatusValue.setTextColor(Color.parseColor("#50FA7B"));
                    btnToggleServer.setText("STOP BACKGROUND SERVER");
                    btnToggleServer.setTextColor(Color.WHITE);
                    btnToggleServer.setBackgroundColor(Color.parseColor("#FF5555"));
                    
                    tvLocalUrl.setText("Local URL: http://localhost:" + portVal + "/api/query");
                    tvNetworkUrl.setText("Network URL: http://" + hostAddress + ":" + portVal + "/api/query");
                } else {
                    tvStatusValue.setText("INACTIVE");
                    tvStatusValue.setTextColor(Color.parseColor("#FF5555"));
                    btnToggleServer.setText("START BACKGROUND SERVER");
                    btnToggleServer.setTextColor(Color.BLACK);
                    btnToggleServer.setBackgroundColor(Color.parseColor("#00E5FF"));
                    
                    tvLocalUrl.setText("Local URL: http://localhost:" + portVal + "/api/query");
                    tvNetworkUrl.setText("Network URL: http://" + hostAddress + ":" + portVal + "/api/query");
                }

                String guideIp = hostAddress.equals("localhost") ? "localhost" : hostAddress;
                String guideText = "To query the database from other platforms, send a HTTP POST request:\n\n" +
                        "End" + "point: ht" + "tp://" + guideIp + ":" + portVal + "/api/query\n" +
                        "Me" + "thod: PO" + "ST\n" +
                        "Headers:\n" +
                        "  Authorization: " + "Bea" + "rer <your_api_key>\n" +
                        "  Content-Type: application/json\n\n" +
                        "Body:\n" +
                        "  {\n" +
                        "    \"sql\": \"SELECT * FROM users;\",\n" +
                        "    \"database\": \"ecommerce\"\n" +
                        "  }\n\n" +
                        "Python Example:\n" +
                        "import requests\n" +
                        "res = requests.post(\n" +
                        "  'http://" + guideIp + ":" + portVal + "/api/query',\n" +
                        "  headers={'Authorization': '" + "Bea" + "rer <key>'},\n" +
                        "  json={'sql': 'SELECT * FROM products;', 'database': 'ecommerce'}\n" +
                        ")\n" +
                        "print(res.json())\n\n" +
                        "Java Example:\n" +
                        "var client = java.net.http.HttpClient.newHttpClient();\n" +
                        "var request = java.net.http.HttpRequest.newBuilder()\n" +
                        "  .uri(java.net.URI.create(\"http://" + guideIp + ":" + portVal + "/api/query\"))\n" +
                        "  .header(\"Authorization\", \"" + "Bea" + "rer <key>\")\n" +
                        "  .header(\"Content-Type\", \"application/json\")\n" +
                        "  .POST(java.net.http.HttpRequest.BodyPublishers.ofString(\n" +
                        "    \"{\\\"sql\\\":\\\"SELECT * FROM products;\\\",\\\"database\\\":\\\"ecommerce\\\"}\"\n" +
                        "  )).build();\n" +
                        "var response = client.send(request,\n" +
                        "  java.net.http.HttpResponse.BodyHandlers.ofString());\n" +
                        "System.out.println(response.body());";
                tvGuide.setText(guideText);
            }
        };
        refreshServerUI.run();

        btnToggleServer.setOnClickListener(v -> {
            boolean running = apiServer != null && apiServer.isRunning();
            if (running) {
                stopApiService();
                Toast.makeText(requireContext(), R.string.toast_api_stopped, Toast.LENGTH_SHORT).show();
            } else {
                startApiService();
                Toast.makeText(requireContext(), R.string.toast_api_started, Toast.LENGTH_SHORT).show();
            }
            btnToggleServer.postDelayed(refreshServerUI, 200);
            refreshServerUI.run();
        });

        parent.addView(serverControlPanel);

        // Subtitle/Instructions header
        TextView tvSectionTitle = new TextView(requireContext());
        tvSectionTitle.setText("Active API Keys");
        tvSectionTitle.setTextColor(Color.parseColor("#888888"));
        tvSectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSectionTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        parent.addView(tvSectionTitle);

        // Keys list container
        LinearLayout listContainer = new LinearLayout(requireContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp8, 0, dp8);

        // Add a ScrollView around the keys list and usage guide
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scroll.setLayoutParams(scrollLp);

        LinearLayout scrollChild = new LinearLayout(requireContext());
        scrollChild.setOrientation(LinearLayout.VERTICAL);
        scrollChild.addView(listContainer);

        // --- Add Key Form ---
        TextView tvAddKeyTitle = new TextView(requireContext());
        tvAddKeyTitle.setText("Generate New API Key");
        tvAddKeyTitle.setTextColor(Color.parseColor("#00E5FF"));
        tvAddKeyTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvAddKeyTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams formTitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        formTitleLp.setMargins(0, dp16, 0, dp8);
        tvAddKeyTitle.setLayoutParams(formTitleLp);
        scrollChild.addView(tvAddKeyTitle);

        EditText etKeyLabel = new EditText(requireContext());
        etKeyLabel.setHint("Key Description (e.g. Web Dashboard)");
        etKeyLabel.setHintTextColor(Color.parseColor("#555555"));
        etKeyLabel.setTextColor(Color.WHITE);
        etKeyLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        etKeyLabel.setBackgroundColor(Color.parseColor("#151515"));
        etKeyLabel.setPadding(dp12, dp8, dp12, dp8);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputLp.setMargins(0, 0, 0, dp8);
        etKeyLabel.setLayoutParams(inputLp);
        scrollChild.addView(etKeyLabel);

        TextView btnGenerate = new TextView(requireContext());
        btnGenerate.setText("Generate Key");
        btnGenerate.setTextColor(Color.BLACK);
        btnGenerate.setBackgroundColor(Color.parseColor("#00E5FF"));
        btnGenerate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btnGenerate.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnGenerate.setGravity(android.view.Gravity.CENTER);
        btnGenerate.setPadding(dp16, dp8, dp16, dp8);
        btnGenerate.setClickable(true);
        btnGenerate.setFocusable(true);
        LinearLayout.LayoutParams generateLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnGenerate.setLayoutParams(generateLp);
        scrollChild.addView(btnGenerate);

        // --- Usage Instructions ---
        TextView tvGuideTitle = new TextView(requireContext());
        tvGuideTitle.setText("API Integration Guide");
        tvGuideTitle.setTextColor(Color.parseColor("#888888"));
        tvGuideTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvGuideTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        LinearLayout.LayoutParams guideTitleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guideTitleLp.setMargins(0, dp16, 0, dp8);
        tvGuideTitle.setLayoutParams(guideTitleLp);
        scrollChild.addView(tvGuideTitle);

        tvGuide.setTextColor(Color.parseColor("#CCCCCC"));
        tvGuide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvGuide.setTypeface(Typeface.MONOSPACE);
        tvGuide.setBackgroundColor(Color.parseColor("#121212"));
        tvGuide.setPadding(dp12, dp12, dp12, dp12);
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guideLp.setMargins(0, 0, 0, dp16);
        tvGuide.setLayoutParams(guideLp);
        scrollChild.addView(tvGuide);

        scroll.addView(scrollChild);
        parent.addView(scroll);

        // --- Dialog creation ---
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(parent)
                .create();

        // Footer buttons
        LinearLayout footer = new LinearLayout(requireContext());
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(android.view.Gravity.END);
        footer.setPadding(0, dp12, 0, 0);

        TextView btnClose = new TextView(requireContext());
        btnClose.setText("Close");
        btnClose.setTextColor(Color.parseColor("#AAAAAA"));
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        btnClose.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        btnClose.setPadding(dp16, dp8, dp16, dp8);
        btnClose.setClickable(true);
        btnClose.setFocusable(true);
        footer.addView(btnClose);
        parent.addView(footer);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> showKeyboard());

        // Refresh/repopulate logic helper
        Runnable repopulate = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                JSONObject keys = apiKeyManager.getKeys();
                java.util.Iterator<String> keyIt = keys.keys();
                if (!keyIt.hasNext()) {
                    TextView tvEmpty = new TextView(requireContext());
                    tvEmpty.setText("No active API Keys. Generate one below.");
                    tvEmpty.setTextColor(Color.parseColor("#555555"));
                    tvEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    listContainer.addView(tvEmpty);
                    return;
                }
                while (keyIt.hasNext()) {
                    final String keyStr = keyIt.next();
                    JSONObject keyObj = keys.optJSONObject(keyStr);
                    if (keyObj == null) continue;
                    String label = keyObj.optString("name", "API Key");
                    String date = keyObj.optString("created_at", "");

                    // Item container (Card)
                    LinearLayout item = new LinearLayout(requireContext());
                    item.setOrientation(LinearLayout.VERTICAL);
                    item.setBackgroundColor(Color.parseColor("#151515"));
                    item.setPadding(dp12, dp8, dp12, dp8);
                    LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    itemLp.setMargins(0, 0, 0, dp8);
                    item.setLayoutParams(itemLp);

                    // Label and Date row
                    LinearLayout labelRow = new LinearLayout(requireContext());
                    labelRow.setOrientation(LinearLayout.HORIZONTAL);
                    labelRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

                    TextView tvLabel = new TextView(requireContext());
                    tvLabel.setText(label);
                    tvLabel.setTextColor(Color.WHITE);
                    tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    tvLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    labelRow.addView(tvLabel);

                    TextView tvDate = new TextView(requireContext());
                    tvDate.setText(date);
                    tvDate.setTextColor(Color.parseColor("#666666"));
                    tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
                    labelRow.addView(tvDate);
                    item.addView(labelRow);

                    // Key string row
                    LinearLayout keyRow = new LinearLayout(requireContext());
                    keyRow.setOrientation(LinearLayout.HORIZONTAL);
                    keyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    keyRow.setPadding(0, dp4(), 0, 0);

                    TextView tvKey = new TextView(requireContext());
                    // Obfuscate key to show only first 12 characters and last 4
                    String showKey = keyStr;
                    if (keyStr.length() > 20) {
                        showKey = keyStr.substring(0, 15) + "..." + keyStr.substring(keyStr.length() - 4);
                    }
                    tvKey.setText(showKey);
                    tvKey.setTextColor(Color.parseColor("#00E5FF"));
                    tvKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    tvKey.setTypeface(Typeface.MONOSPACE);
                    tvKey.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    keyRow.addView(tvKey);

                    // Copy action button
                    TextView btnCopy = new TextView(requireContext());
                    btnCopy.setText("Copy");
                    btnCopy.setTextColor(Color.parseColor("#50FA7B"));
                    btnCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    btnCopy.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    btnCopy.setPadding(dp8, dp4(), dp8, dp4());
                    btnCopy.setClickable(true);
                    btnCopy.setFocusable(true);
                    btnCopy.setOnClickListener(clickV -> {
                        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("PocketSQL API Key", keyStr);
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            android.os.PersistableBundle extras = new android.os.PersistableBundle();
                            extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
                            clip.getDescription().setExtras(extras);
                        }
                        if (clipboard != null) {
                            com.mysql.pocketsql.engine.AppIntegrityManager.setPrimaryClip(clipboard, clip);
                            Toast.makeText(requireContext(), R.string.toast_key_copied, Toast.LENGTH_SHORT).show();
                            
                            // Delayed clear to prevent clipboard credential exposure
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                try {
                                    if (clipboard.hasPrimaryClip() && 
                                        clipboard.getPrimaryClipDescription() != null && 
                                        "PocketSQL API Key".equals(clipboard.getPrimaryClipDescription().getLabel())) {
                                        com.mysql.pocketsql.engine.AppIntegrityManager.clearPrimaryClip(clipboard);
                                    }
                                } catch (Exception ignored) {}
                            }, 30000);
                        }
                    });
                    keyRow.addView(btnCopy);

                    // Delete action button
                    TextView btnDel = new TextView(requireContext());
                    btnDel.setText("Delete");
                    btnDel.setTextColor(Color.parseColor("#FF5555"));
                    btnDel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    btnDel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    btnDel.setPadding(dp8, dp4(), dp8, dp4());
                    btnDel.setClickable(true);
                    btnDel.setFocusable(true);
                    btnDel.setOnClickListener(clickV -> {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete API Key")
                                .setMessage("Are you sure you want to delete this API Key? Clients using this key will be blocked immediately.")
                                .setPositiveButton("Delete", (dialogInterface, which) -> {
                                    apiKeyManager.deleteKey(keyStr);
                                    Toast.makeText(requireContext(), R.string.toast_key_deleted, Toast.LENGTH_SHORT).show();
                                    run(); // Refresh list!
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                    keyRow.addView(btnDel);

                    item.addView(keyRow);
                    listContainer.addView(item);
                }
                settings.applyFontToViewTree(listContainer);
            }

            private int dp4() {
                return dpToPx(4);
            }
        };

        btnGenerate.setOnClickListener(clickV -> {
            String label = etKeyLabel.getText().toString().trim();
            if (label.isEmpty()) {
                label = "API Key";
            }
            apiKeyManager.generateKey(label);
            etKeyLabel.setText("");
            Toast.makeText(requireContext(), R.string.toast_key_generated, Toast.LENGTH_SHORT).show();
            repopulate.run(); // Refresh list!
        });

        // Initialize lists
        repopulate.run();
        settings.applyFontToViewTree(parent);

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void setupTerminal() {
        etCommandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE ||
                (actionId == EditorInfo.IME_NULL && event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                executeTerminalCommand();
                showKeyboard();
                return true;
            }
            return false;
        });

        // Click background or history to focus keyboard
        View.OnClickListener clickFocus = v -> resetHistoryFocus();
        scrollContainer.setOnClickListener(clickFocus);
        layoutTerminalContainer.setOnClickListener(clickFocus);
        tvTerminalPrompt.setOnClickListener(clickFocus);
        tvTerminalHistory.setOnClickListener(clickFocus);

        // Press and hold (Long Click) activates text selection dynamically in terminal!
        tvTerminalHistory.setLongClickable(true);
        tvTerminalHistory.setOnLongClickListener(v -> {
            tvTerminalHistory.setFocusable(true);
            tvTerminalHistory.setFocusableInTouchMode(true);
            tvTerminalHistory.setTextIsSelectable(true);
            return false; // Allow Android native selection handles directly on tvTerminalHistory
        });

        tvTerminalPrompt.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            applyPromptMargin();
        });

        // Keep input field scrolled into view above the keyboard on size/layout updates
        layoutTerminalContainer.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
        });

        // Auto-scroll when keyboard focuses
        etCommandInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                scrollContainer.postDelayed(() -> scrollContainer.fullScroll(View.FOCUS_DOWN), 100);
            }
        });

        // Focus keyboard on startup
        showKeyboard();

        // Build special keys toolbar
        setupSpecialKeys();

        // Wire action toolbar buttons
        setupActionToolbar();

        // Set up SQL Suggestions
        setupSuggestions();

    }

    private void setupActionToolbar() {
        // History UP: load older command
        btnHistoryUp.setOnClickListener(v -> {
            if (queryHistory.isEmpty()) return;
            if (historyIndex == -1) historyIndex = queryHistory.size();
            if (historyIndex > 0) {
                historyIndex--;
                etCommandInput.setText(queryHistory.get(historyIndex));
                etCommandInput.setSelection(etCommandInput.getText().length());
            }
        });

        // History DOWN: load newer command
        btnHistoryDown.setOnClickListener(v -> {
            if (queryHistory.isEmpty()) return;
            if (historyIndex == -1) return;
            if (historyIndex < queryHistory.size() - 1) {
                historyIndex++;
                etCommandInput.setText(queryHistory.get(historyIndex));
                etCommandInput.setSelection(etCommandInput.getText().length());
            } else {
                historyIndex = -1;
                etCommandInput.setText("");
            }
        });

        // Template picker
        btnTemplate.setOnClickListener(v -> showTemplatePicker());

        // Clear input line
        btnClearLine.setOnClickListener(v -> etCommandInput.setText(""));

        // Clear screen
        btnClearScreen.setOnClickListener(v -> clearConsoleScreen());

        // Settings
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // Help
        btnHelp.setOnClickListener(v -> printHelpMessage());

        // Copy All
        btnCopyAll.setOnClickListener(v -> showCopyDialog());

        // API Keys
        btnApiKeys.setOnClickListener(v -> showApiKeysDialog());
        
        // Connections
        btnConnections.setOnClickListener(v -> showConnectionsDialog());
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    /** Apply all saved settings to the terminal UI. */
    private void applySettings() {
        int textColor   = settings.getTextColor();
        int bgColor     = settings.getBackgroundColor();
        int promptColor = settings.getPromptColor();
        int fontSizeSp  = settings.getFontSizeSp();
        float lineSpacing = settings.getLineSpacingExtra();

        Typeface tf = settings.getTypeface(requireContext());

        // Background
        scrollContainer.setBackgroundColor(bgColor);
        layoutTerminalContainer.setBackgroundColor(bgColor);

        // Apply font to entire HomeFragment layout view tree!
        if (getView() != null) {
            settings.applyFontToViewTree(getView());
        }

        // Terminal history text
        tvTerminalHistory.setTextColor(textColor);
        tvTerminalHistory.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        tvTerminalHistory.setLineSpacing(lineSpacing, 1f);
        tvTerminalHistory.setTypeface(tf);
        tvTerminalHistory.setLetterSpacing(0.0f);
        tvTerminalHistory.setSingleLine(false);
        tvTerminalHistory.setMaxLines(Integer.MAX_VALUE);
        tvTerminalHistory.setHorizontallyScrolling(true);

        ViewGroup.LayoutParams lp = tvTerminalHistory.getLayoutParams();
        if (lp != null) {
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            tvTerminalHistory.setLayoutParams(lp);
        }

        // Prompt
        tvTerminalPrompt.setTextColor(promptColor);
        tvTerminalPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        try {
            tvTerminalPrompt.setTypeface(Typeface.create(tf, Typeface.BOLD));
        } catch (Exception e) {
            tvTerminalPrompt.setTypeface(tf, Typeface.BOLD);
        }
        tvTerminalPrompt.setLetterSpacing(0.0f);

        // Input field
        etCommandInput.setTextColor(textColor);
        etCommandInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        etCommandInput.setTypeface(tf);
        etCommandInput.setLetterSpacing(0.0f);

        // Update prompt string only when authenticated
        if (loginState == LOGIN_STATE_AUTHENTICATED) {
            refreshTerminalPrompt();
        }
    }

    private void showSettingsDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_settings, null);

        // ── Bind dialog views ────────────────────────────────────────────
        RadioGroup rgTheme        = dialogView.findViewById(R.id.rgTheme);
        Spinner spFontFamily      = dialogView.findViewById(R.id.spFontFamily);
        RadioGroup rgFontSize     = dialogView.findViewById(R.id.rgFontSize);
        RadioGroup rgLineSpacing  = dialogView.findViewById(R.id.rgLineSpacing);
        RadioGroup rgPrompt       = dialogView.findViewById(R.id.rgPrompt);
        SwitchCompat swAutoScroll = dialogView.findViewById(R.id.switchAutoScroll);
        TextView btnApply         = dialogView.findViewById(R.id.btnDialogApply);
        TextView btnCancel        = dialogView.findViewById(R.id.btnDialogCancel);
        TextView btnClose         = dialogView.findViewById(R.id.btnDialogClose);
        TextView btnExport        = dialogView.findViewById(R.id.btnExportDatabase);
        TextView btnImport        = dialogView.findViewById(R.id.btnImportDatabase);

        settings.applyFontToViewTree(dialogView);

        // ── Restore current values ───────────────────────────────────────
        int[] themeIds = {R.id.rbThemeClassic, R.id.rbThemeMatrix,
                          R.id.rbThemeOcean,   R.id.rbThemeDracula};
        rgTheme.check(themeIds[settings.getTheme()]);

        // Populate font family spinner
        List<SettingsManager.FontOption> fontOptions = settings.getAvailableFontOptions(requireContext());
        ArrayAdapter<SettingsManager.FontOption> fontAdapter =
            new ArrayAdapter<SettingsManager.FontOption>(requireContext(), android.R.layout.simple_spinner_item, fontOptions) {
                @NonNull
                @Override
                public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(Color.WHITE);
                        ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                    }
                    settings.applyFontToViewTree(v);
                    return v;
                }
                @Override
                public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                    View v = super.getDropDownView(position, convertView, parent);
                    if (v instanceof TextView) {
                        ((TextView) v).setTextColor(Color.WHITE);
                        ((TextView) v).setBackgroundColor(Color.parseColor("#12131C"));
                        ((TextView) v).setPadding(24, 20, 24, 20);
                    }
                    settings.applyFontToViewTree(v);
                    return v;
                }
            };
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFontFamily.setAdapter(fontAdapter);

        String currentFamily = settings.getFontFamily();
        for (int i = 0; i < fontOptions.size(); i++) {
            if (fontOptions.get(i).value.equals(currentFamily)) {
                spFontFamily.setSelection(i);
                break;
            }
        }

        int[] fontIds = {R.id.rbFont12, R.id.rbFont14, R.id.rbFont16, R.id.rbFont18};
        rgFontSize.check(fontIds[settings.getFontSizeIndex()]);

        int[] spacingIds = {R.id.rbSpacingCompact, R.id.rbSpacingNormal, R.id.rbSpacingRelaxed};
        rgLineSpacing.check(spacingIds[settings.getLineSpacing()]);

        int[] promptIds = {R.id.rbPromptMysql, R.id.rbPromptPocket,
                           R.id.rbPromptDollar, R.id.rbPromptAngle};
        rgPrompt.check(promptIds[settings.getPromptStyleIndex()]);

        swAutoScroll.setChecked(settings.isAutoScroll());

        // ── Build dialog (no default title/buttons) ──────────────────────
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create();
        activeSettingsDialog = dialog;

        // ── Wire custom buttons ──────────────────────────────────────────
        btnApply.setOnClickListener(v -> {
            // Save theme
            int checkedTheme = rgTheme.getCheckedRadioButtonId();
            for (int i = 0; i < themeIds.length; i++) {
                if (themeIds[i] == checkedTheme) { settings.setTheme(i); break; }
            }
            // Save font family
            SettingsManager.FontOption selectedFont = (SettingsManager.FontOption) spFontFamily.getSelectedItem();
            if (selectedFont != null) {
                settings.setFontFamily(selectedFont.value);
            }
            // Save font size
            int checkedFont = rgFontSize.getCheckedRadioButtonId();
            for (int i = 0; i < fontIds.length; i++) {
                if (fontIds[i] == checkedFont) { settings.setFontSizeIndex(i); break; }
            }
            // Save line spacing
            int checkedSpacing = rgLineSpacing.getCheckedRadioButtonId();
            for (int i = 0; i < spacingIds.length; i++) {
                if (spacingIds[i] == checkedSpacing) { settings.setLineSpacing(i); break; }
            }
            // Save prompt style
            int checkedPrompt = rgPrompt.getCheckedRadioButtonId();
            for (int i = 0; i < promptIds.length; i++) {
                if (promptIds[i] == checkedPrompt) { settings.setPromptStyleIndex(i); break; }
            }
            // Save auto-scroll
            settings.setAutoScroll(swAutoScroll.isChecked());
            // Apply and close
            applySettings();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnExport.setOnClickListener(v -> {
            String activeDb = engine.getActiveDatabase();
            if (activeDb == null || activeDb.isEmpty()) {
                Toast.makeText(requireContext(), R.string.toast_select_db_first, Toast.LENGTH_SHORT).show();
                return;
            }
            
            String[] formats = {".db  (Native Backup)", ".sql  (SQL Script)", ".xlsx (SpreadsheetML)", ".csv  (Zipped CSVs)"};
            String[] exts = {"db", "sql", "xlsx", "csv"};
            
            new AlertDialog.Builder(requireContext())
                .setTitle("Choose Export Format")
                .setItems(formats, (dialogInterface, index) -> {
                    selectedExportFormat = exts[index];
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_TITLE, activeDb + "." + selectedExportFormat);
                    exportFileLauncher.launch(intent);
                    dialog.dismiss();
                })
                .show();
        });

        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            importFileLauncher.launch(intent);
            dialog.dismiss();
        });

        // ── Show full-screen ─────────────────────────────────────────────
        dialog.setOnDismissListener(d -> showKeyboard());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private void showTemplatePicker() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_templates, null);
        settings.applyFontToViewTree(dialogView);

        TextView btnClose = dialogView.findViewById(R.id.btnTemplateClose);
        EditText etSearch = dialogView.findViewById(R.id.etTemplateSearch);
        LinearLayout layoutChips = dialogView.findViewById(R.id.layoutCategoryChips);
        LinearLayout container = dialogView.findViewById(R.id.layoutTemplatesContainer);

        final AlertDialog[] dialogRef = new AlertDialog[1];
        final List<TemplateCategory> categories = getCategorizedTemplates();

        final String[] selectedCat = {"ALL"};
        final String[] queryText = {""};

        final String[] chipLabels = {"ALL", "BASIC", "JOINS", "ALTER", "FUNCTIONS", "ADMIN"};

        // Re-render templates list helper
        final Runnable renderList = new Runnable() {
            @Override
            public void run() {
                container.removeAllViews();
                
                int dp4 = dpToPx(4);
                int dp8 = dpToPx(8);
                int dp12 = dpToPx(12);
                int dp14 = dpToPx(14);
                int dp16 = dpToPx(16);

                String currentCat = selectedCat[0];
                String currentQuery = queryText[0];

                for (TemplateCategory cat : categories) {
                    if (!matchesCategory(currentCat, cat.name)) {
                        continue;
                    }

                    // Filter items
                    List<TemplateItem> matchingItems = new ArrayList<>();
                    for (TemplateItem item : cat.items) {
                        if (currentQuery.isEmpty() || 
                            item.title.toLowerCase().contains(currentQuery.toLowerCase()) || 
                            item.code.toLowerCase().contains(currentQuery.toLowerCase())) {
                            matchingItems.add(item);
                        }
                    }

                    if (matchingItems.isEmpty()) {
                        continue;
                    }

                    // Category Header
                    TextView tvCat = new TextView(requireContext());
                    tvCat.setText(cat.name);
                    tvCat.setTextColor(Color.parseColor("#00E5FF")); // Cyan accent
                    tvCat.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    tvCat.setLetterSpacing(0.12f);
                    
                    LinearLayout.LayoutParams lpCat = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lpCat.setMargins(0, dp16, 0, dp8);
                    tvCat.setLayoutParams(lpCat);
                    container.addView(tvCat);

                    // Add cards
                    for (TemplateItem item : matchingItems) {
                        LinearLayout textLayout = new LinearLayout(requireContext());
                        textLayout.setOrientation(LinearLayout.VERTICAL);
                        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        textLayout.setLayoutParams(textLp);

                        // Title
                        TextView tvTitle = new TextView(requireContext());
                        tvTitle.setText(item.title);
                        tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
                        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                        textLayout.addView(tvTitle);

                        // Code
                        TextView tvCode = new TextView(requireContext());
                        tvCode.setText(item.code);
                        tvCode.setTextColor(Color.parseColor("#8ECAE6")); // Soft blue code
                        tvCode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                        tvCode.setLetterSpacing(0.04f);
                        LinearLayout.LayoutParams codeLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        codeLp.setMargins(0, dp4, 0, 0);
                        tvCode.setLayoutParams(codeLp);
                        textLayout.addView(tvCode);

                        // Right side insert arrow symbol (↳)
                        TextView tvArrow = new TextView(requireContext());
                        tvArrow.setText("↳");
                        tvArrow.setTextColor(Color.parseColor("#00E5FF")); // Cyan accent
                        tvArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        arrowLp.gravity = android.view.Gravity.CENTER_VERTICAL;
                        arrowLp.setMarginStart(dp12);
                        tvArrow.setLayoutParams(arrowLp);

                        // Card layout (horizontal container)
                        LinearLayout card = new LinearLayout(requireContext());
                        card.setOrientation(LinearLayout.HORIZONTAL);
                        card.setPadding(dp14, dp12, dp14, dp12);
                        card.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        card.setClickable(true);
                        card.setFocusable(true);
                        card.setBackgroundResource(R.drawable.template_card_bg); // Ripple background!

                        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        lpCard.setMargins(0, 0, 0, dp8);
                        card.setLayoutParams(lpCard);

                        card.addView(textLayout);
                        card.addView(tvArrow);

                        // Click listener
                        card.setOnClickListener(v -> {
                            etCommandInput.setText(item.code);
                            etCommandInput.setSelection(item.code.length());
                            showKeyboard();
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                        });

                        container.addView(card);
                    }
                }

                // Empty state view
                if (container.getChildCount() == 0) {
                    TextView tvEmpty = new TextView(requireContext());
                    tvEmpty.setText("No templates found matching \"" + currentQuery + "\"");
                    tvEmpty.setTextColor(Color.parseColor("#888888"));
                    tvEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tvEmpty.setGravity(android.view.Gravity.CENTER);
                    
                    LinearLayout.LayoutParams lpEmpty = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lpEmpty.setMargins(0, dpToPx(40), 0, 0);
                    tvEmpty.setLayoutParams(lpEmpty);
                    container.addView(tvEmpty);
                }

                settings.applyFontToViewTree(container);
            }
        };

        // Render chips helper
        final Runnable renderChips = new Runnable() {
            @Override
            public void run() {
                layoutChips.removeAllViews();
                int dp6 = dpToPx(6);
                int dp12 = dpToPx(12);
                int dp8 = dpToPx(8);

                for (String label : chipLabels) {
                    TextView chip = new TextView(requireContext());
                    chip.setText(label);
                    boolean isActive = label.equalsIgnoreCase(selectedCat[0]);
                    
                    chip.setTextColor(Color.parseColor(isActive ? "#121212" : "#AAAAAA"));
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                    chip.setPadding(dp12, dp6, dp12, dp6);
                    chip.setGravity(android.view.Gravity.CENTER);
                    
                    // Chip Background (programmatic GradientDrawable)
                    android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                    gd.setColor(Color.parseColor(isActive ? "#00E5FF" : "#222222"));
                    gd.setCornerRadius(dpToPx(16));
                    chip.setBackground(gd);
                    
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.setMarginEnd(dp8);
                    chip.setLayoutParams(lp);

                    chip.setOnClickListener(v -> {
                        selectedCat[0] = label;
                        run(); // Re-render chips to show active state
                        renderList.run(); // Re-render template list
                    });

                    layoutChips.addView(chip);
                }

                settings.applyFontToViewTree(layoutChips);
            }
        };

        // Initialize lists and chips
        renderChips.run();
        renderList.run();

        // Search text watcher
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                queryText[0] = s.toString();
                renderList.run();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
            
        dialogRef[0] = dialog;
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        dialog.setOnDismissListener(d -> showKeyboard());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private boolean matchesCategory(String selection, String catName) {
        if ("ALL".equalsIgnoreCase(selection)) return true;
        if ("BASIC".equalsIgnoreCase(selection) && catName.contains("BASIC")) return true;
        if ("JOINS".equalsIgnoreCase(selection) && catName.contains("JOINS")) return true;
        if ("ALTER".equalsIgnoreCase(selection) && catName.contains("ALTER")) return true;
        if ("FUNCTIONS".equalsIgnoreCase(selection) && catName.contains("FUNCTIONS")) return true;
        if ("ADMIN".equalsIgnoreCase(selection) && catName.contains("ADMIN")) return true;
        return false;
    }

    private void setupSuggestions() {
        etCommandInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSuggestions(s.toString(), etCommandInput.getSelectionStart());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                applyPromptMargin(s);
            }
        });
    }

    private static boolean matchInitials(String tableName, String alias) {
        if (tableName == null || alias == null || alias.isEmpty()) {
            return false;
        }
        StringBuilder initials = new StringBuilder();
        String[] parts = tableName.split("_");
        for (String part : parts) {
            if (!part.isEmpty()) {
                initials.append(part.charAt(0));
            }
        }
        return initials.toString().toLowerCase().equals(alias.toLowerCase());
    }

    private void updateSuggestions(String text, int pos) {
        try {
            if (loginState != LOGIN_STATE_AUTHENTICATED) {
                suggestionsBar.setVisibility(View.GONE);
                return;
            }

            String word = getCurrentWord(text, pos);
            if (word.isEmpty()) {
                suggestionsBar.setVisibility(View.GONE);
                return;
            }

            List<String> matches = new ArrayList<>();
            String wordLower = word.toLowerCase();

            if (word.contains(".")) {
                int dotIndex = word.indexOf('.');
                String tableNameOrAlias = word.substring(0, dotIndex);
                String colPrefix = word.substring(dotIndex + 1).toLowerCase();

                // Retrieve full SQL context by combining multiLineBuffer and current text
                String fullSqlContext = multiLineBuffer.toString();
                if (fullSqlContext.length() > 0) {
                    fullSqlContext += "\n";
                }
                fullSqlContext += text;

                java.util.Map<String, String> aliases = com.mysql.pocketsql.engine.SqlAliasExtractor.extractAliases(fullSqlContext);
                String realTableName = tableNameOrAlias;
                List<String> allTables = engine.getTablesList();

                if (aliases.containsKey(tableNameOrAlias.toLowerCase())) {
                    realTableName = aliases.get(tableNameOrAlias.toLowerCase());
                } else {
                    // Fallback: search for a table name that matches tableNameOrAlias
                    String lowerAlias = tableNameOrAlias.toLowerCase();
                    String bestMatch = null;
                    for (String t : allTables) {
                        if (t.toLowerCase().equals(lowerAlias)) {
                            bestMatch = t;
                            break;
                        }
                    }
                    if (bestMatch == null) {
                        for (String t : allTables) {
                            if (t.toLowerCase().startsWith(lowerAlias)) {
                                bestMatch = t;
                                break;
                            }
                        }
                    }
                    if (bestMatch == null) {
                        for (String t : allTables) {
                            if (matchInitials(t, lowerAlias)) {
                                bestMatch = t;
                                break;
                            }
                        }
                    }
                    if (bestMatch != null) {
                        realTableName = bestMatch;
                    }
                }

                boolean tableExists = false;
                String exactTableName = realTableName;
                for (String t : allTables) {
                    if (t.equalsIgnoreCase(realTableName)) {
                        tableExists = true;
                        exactTableName = t;
                        break;
                    }
                }

                if (tableExists) {
                    List<String> cols = engine.getColumnsList(exactTableName);
                    for (String col : cols) {
                        if (col.toLowerCase().startsWith(colPrefix)) {
                            matches.add(tableNameOrAlias + "." + col);
                        }
                    }
                } else {
                    // Fallback: if table is not resolved (alias not defined yet and doesn't match any table prefix/initials),
                    // suggest columns from ALL tables in the database.
                    for (String t : allTables) {
                        List<String> cols = engine.getColumnsList(t);
                        for (String col : cols) {
                            if (col.toLowerCase().startsWith(colPrefix)) {
                                String suggestion = tableNameOrAlias + "." + col;
                                if (!matches.contains(suggestion)) {
                                    matches.add(suggestion);
                                }
                            }
                        }
                    }
                }
            } else {
                // Keywords
                matches.addAll(com.mysql.pocketsql.engine.SqlKeywordSuggester.suggest(wordLower));

                // Tables
                List<String> tables = engine.getTablesList();
                for (String t : tables) {
                    if (t.toLowerCase().startsWith(wordLower)) {
                        matches.add(t);
                    }
                }

                // Columns (all columns from active tables matching prefix)
                for (String t : tables) {
                    List<String> cols = engine.getColumnsList(t);
                    for (String col : cols) {
                        if (col.toLowerCase().startsWith(wordLower) && !matches.contains(col)) {
                            matches.add(col);
                        }
                    }
                }

                // Databases
                List<String> dbs = engine.getDatabasesList();
                for (String db : dbs) {
                    if (db.toLowerCase().startsWith(wordLower) && !matches.contains(db)) {
                        matches.add(db);
                    }
                }

                // Procedures
                List<String> procs = engine.getProceduresList();
                for (String p : procs) {
                    if (p.toLowerCase().startsWith(wordLower) && !matches.contains(p)) {
                        matches.add(p);
                    }
                }

                // Triggers
                List<String> triggers = engine.getTriggersList();
                for (String tg : triggers) {
                    if (tg.toLowerCase().startsWith(wordLower) && !matches.contains(tg)) {
                        matches.add(tg);
                    }
                }

                // Events
                List<String> events = engine.getEventsList();
                for (String ev : events) {
                    if (ev.toLowerCase().startsWith(wordLower) && !matches.contains(ev)) {
                        matches.add(ev);
                    }
                }

                // Custom Functions
                List<String> fns = engine.getCustomFunctionsList();
                for (String fn : fns) {
                    String fnWithParens = fn + "()";
                    if (fnWithParens.toLowerCase().startsWith(wordLower) && !matches.contains(fnWithParens)) {
                        matches.add(fnWithParens);
                    }
                }

                // Constraints
                List<String> constraints = engine.getConstraintsList();
                for (String cn : constraints) {
                    if (cn.toLowerCase().startsWith(wordLower) && !matches.contains(cn)) {
                        matches.add(cn);
                    }
                }

                // Indexes
                List<String> indexes = engine.getIndexesList();
                for (String idx : indexes) {
                    if (idx.toLowerCase().startsWith(wordLower) && !matches.contains(idx)) {
                        matches.add(idx);
                    }
                }
            }

            suggestionsContainer.removeAllViews();
            if (matches.isEmpty()) {
                suggestionsBar.setVisibility(View.GONE);
                return;
            }

            int dp6 = dpToPx(6);
            int dp12 = dpToPx(12);

            for (final String match : matches) {
                TextView chip = new TextView(requireContext());
                chip.setText(match);
                chip.setTextColor(Color.parseColor("#00E5FF")); // Cyan text
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                chip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                chip.setPadding(dp12, dp6, dp12, dp6);
                chip.setBackgroundResource(R.drawable.suggestion_chip_bg);
                chip.setClickable(true);
                chip.setFocusable(true);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                lp.setMarginEnd(dp12);
                chip.setLayoutParams(lp);

                final String wordToReplace = word;
                chip.setOnClickListener(v -> applySuggestion(wordToReplace, match));

                suggestionsContainer.addView(chip);
            }

            settings.applyFontToViewTree(suggestionsContainer);
            suggestionsBar.setVisibility(View.VISIBLE);
        } catch (Throwable t) {
            com.mysql.pocketsql.engine.SqlLog.e("PocketSQL", "Error in updateSuggestions", t);
            try {
                suggestionsBar.setVisibility(View.GONE);
            } catch (Throwable ignored) {}
        }
    }

    private String getCurrentWord(String text, int pos) {
        if (text == null || pos <= 0 || pos > text.length()) {
            return "";
        }
        int start = pos - 1;
        while (start >= 0) {
            char c = text.charAt(start);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
                break;
            }
            start--;
        }
        return text.substring(start + 1, pos);
    }

    private void applySuggestion(String wordToReplace, String suggestion) {
        try {
            int pos = etCommandInput.getSelectionStart();
            if (pos < 0) return;

            int start = pos - wordToReplace.length();
            if (start < 0) start = 0;

            String replacement;
            int cursorOffset;
            if (suggestion.endsWith("()")) {
                replacement = suggestion;
                cursorOffset = suggestion.length() - 1;
            } else {
                replacement = suggestion + " ";
                cursorOffset = replacement.length();
            }

            Editable editable = etCommandInput.getText();
            editable.replace(start, pos, replacement);

            etCommandInput.setSelection(start + cursorOffset);
            showKeyboard();

            suggestionsBar.setVisibility(View.GONE);
        } catch (Throwable t) {
            com.mysql.pocketsql.engine.SqlLog.e("PocketSQL", "Error in applySuggestion", t);
        }
    }

    private void setupSpecialKeys() {
        String[] keys = { ";", ",", "(", ")", "'", "\"", "<", ">", "=", "-", "+", "*", "/", "_", "%", "@", ".", "!", "{", "}" };

        int dp6  = dpToPx(6);
        int dp10 = dpToPx(10);
        int dp32 = dpToPx(32);

        for (String key : keys) {
            TextView btn = new TextView(requireContext());
            btn.setText(key);
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            btn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            btn.setBackgroundColor(Color.parseColor("#1E1E1E"));
            btn.setPadding(dp10, 0, dp10, 0);
            btn.setGravity(android.view.Gravity.CENTER);
            btn.setMinWidth(dp32);
            btn.setMinHeight(dp32);

            // Separator between keys
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT);
            lp.setMarginEnd(dp6);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> insertSpecialKey(key));

            specialKeysContainer.addView(btn);
        }
        settings.applyFontToViewTree(specialKeysContainer);
    }

    private void insertSpecialKey(String key) {
        int start = Math.max(0, etCommandInput.getSelectionStart());
        int end   = Math.max(0, etCommandInput.getSelectionEnd());
        etCommandInput.getText().replace(Math.min(start, end), Math.max(start, end), key);
        // Move cursor after inserted character
        etCommandInput.setSelection(Math.min(start, end) + key.length());
        showKeyboard();
    }

    private int dpToPx(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void clearConsoleScreen() {
        tvTerminalHistory.setText("");
        showKeyboard();
    }

    private void executeTerminalCommand() {
        String rawInput = etCommandInput.getText().toString();
        String input = rawInput.trim();
        etCommandInput.setText("");

        // Handle empty input behavior
        if (loginState == LOGIN_STATE_USERNAME && input.isEmpty()) {
            return;
        }

        if (loginState == LOGIN_STATE_AUTHENTICATED && rawInput.isEmpty()) {
            String promptTxt = tvTerminalPrompt.getText().toString();
            if (multiLineBuffer.length() == 0) {
                tvTerminalHistory.append(promptTxt + "\n");
            } else {
                multiLineBuffer.append("\n");
                tvTerminalHistory.append(promptTxt + "\n");
            }
            refreshTerminalPrompt();
            if (settings.isAutoScroll()) {
                scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            }
            return;
        }

        if (loginState == SETUP_STATE_USERNAME) {
            String val = input.trim();
            if (val.isEmpty()) {
                setupUsername = com.mysql.pocketsql.engine.SecurityHelper.getDefaultUser();
            } else {
                setupUsername = val;
            }
            tvTerminalHistory.append("Enter admin username [root]: " + setupUsername + "\n");
            
            loginState = SETUP_STATE_HOST;
            tvTerminalPrompt.setText("Enter admin host [localhost]: ");
            setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else if (loginState == SETUP_STATE_HOST) {
            String val = input.trim();
            if (val.isEmpty()) {
                setupHost = com.mysql.pocketsql.engine.SecurityHelper.getDefaultHost();
            } else {
                setupHost = val;
            }
            tvTerminalHistory.append("Enter admin host [localhost]: " + setupHost + "\n");
            
            loginState = SETUP_STATE_PASSWORD;
            tvTerminalPrompt.setText("Enter admin password: ");
            setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else if (loginState == SETUP_STATE_PASSWORD) {
            setupPassword = input;
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < setupPassword.length(); i++) stars.append("*");
            tvTerminalHistory.append("Enter admin password: " + stars.toString() + "\n");
            
            loginState = SETUP_STATE_CONFIRM_PASSWORD;
            tvTerminalPrompt.setText("Confirm admin password: ");
            setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else if (loginState == SETUP_STATE_CONFIRM_PASSWORD) {
            String confirmPassword = input;
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < confirmPassword.length(); i++) stars.append("*");
            tvTerminalHistory.append("Confirm admin password: " + stars.toString() + "\n");
            
            if (setupPassword.equals(confirmPassword)) {
                try {
                    engine.initializeAdminUser(setupUsername, setupHost, setupPassword);
                    tvTerminalHistory.append("\nPocketSQL configured successfully! Please log in.\n\n");
                    promptSaveConnection(setupUsername, setupHost, setupPassword);
                } catch (Exception e) {
                    tvTerminalHistory.append("\nError configuring credentials: " + e.getMessage() + "\n\n");
                }
                loginState = LOGIN_STATE_USERNAME;
                tvTerminalPrompt.setText("Enter username: ");
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
            } else {
                tvTerminalHistory.append("Passwords do not match. Please restart password configuration.\n\n");
                loginState = SETUP_STATE_PASSWORD;
                tvTerminalPrompt.setText("Enter admin password: ");
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
            }
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else if (loginState == LOGIN_STATE_USERNAME) {
            tempUsername = input;
            tvTerminalHistory.append("Enter username: " + tempUsername + "\n");
            loginState = LOGIN_STATE_PASSWORD;
            tvTerminalPrompt.setText("Enter password: ");
            setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, true);
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else if (loginState == LOGIN_STATE_PASSWORD) {
            String password = input;
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < password.length(); i++) stars.append("*");
            tvTerminalHistory.append("Enter password: " + stars.toString() + "\n");
            
            if (engine.authenticate(tempUsername, password)) {
                loginState = LOGIN_STATE_AUTHENTICATED;
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
                tvTerminalHistory.append(getWelcomeText());
                refreshTerminalPrompt();

                // Save credentials for auto-login
                settings.setLastUsername(tempUsername);
                settings.setLastHost(engine.getCurrentHost());
                settings.setLastPassword(password);
                settings.setAutoLogin(true);
                
                promptSaveConnection(tempUsername, engine.getCurrentHost(), password);
            } else {
                tvTerminalHistory.append("Access denied for user '" + tempUsername + "'@'localhost' (using password: " + (password.isEmpty() ? "NO" : "YES") + ")\n\n");
                loginState = LOGIN_STATE_USERNAME;
                tvTerminalPrompt.setText("Enter username: ");
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
            }
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            
        } else {
            // ── Standard query execution (AUTHENTICATED) ─────────────────────
            etCommandInput.setEnabled(false);
            
            final String promptTxt = tvTerminalPrompt.getText().toString();
            final String[] lines = rawInput.split("\n", -1);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    processQueriesBackground(lines, promptTxt);
                }
            }).start();
        }
    }

    private String getExpectedPromptText() {
        if (loginState != LOGIN_STATE_AUTHENTICATED) {
            return tvTerminalPrompt.getText().toString();
        }
        if (multiLineBuffer.length() > 0) {
            return "    -> ";
        }
        String prompt = settings.getPromptString();
        String active = engine.getActiveDatabase();
        if (active == null) {
            return prompt;
        } else {
            String base = prompt.trim(); // e.g. "mysql>"
            return base.replace(">", " [" + active + ">") + " ";
        }
    }

    private void processQueriesBackground(final String[] lines, final String initialPromptTxt) {
        String promptTxt = initialPromptTxt;
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            String trimmedLine = line.trim();

            if (i == lines.length - 1 && line.isEmpty() && multiLineBuffer.length() == 0) {
                continue;
            }

            if (multiLineBuffer.toString().trim().isEmpty()) {
                multiLineBuffer.setLength(0);
            }

            // Handle cancellation
            if (trimmedLine.equalsIgnoreCase("\\c")) {
                final String currentPrompt = promptTxt;
                final String currentLine = line;
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        tvTerminalHistory.append(currentPrompt + currentLine + "\n");
                        multiLineBuffer.setLength(0);
                        refreshTerminalPrompt();
                        etCommandInput.setEnabled(true);
                        showKeyboard();
                    });
                }
                break;
            }

            // Handle session exit
            if (multiLineBuffer.length() == 0 && (trimmedLine.equalsIgnoreCase("exit") || trimmedLine.equalsIgnoreCase("quit") || trimmedLine.equalsIgnoreCase("\\q"))) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        settings.setAutoLogin(false);
                        engine.setCurrentUser(null, null);
                        stopApiService();
                        loginState = LOGIN_STATE_USERNAME;
                        tvTerminalPrompt.setText("Enter username: ");
                        tvTerminalHistory.setText("");
                        setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
                        tvTerminalHistory.append("Connection to PocketSQL server closed.\n\n");
                        scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
                        multiLineBuffer.setLength(0);
                        etCommandInput.setEnabled(true);
                        showKeyboard();
                    });
                }
                return;
            }

            // Handle screen clearing
            if (multiLineBuffer.length() == 0 && (trimmedLine.equalsIgnoreCase("clear") || trimmedLine.equalsIgnoreCase("\\c"))) {
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        clearConsoleScreen();
                        etCommandInput.setEnabled(true);
                        showKeyboard();
                    });
                }
                continue;
            }

            // Handle delimiter command immediately
            if (multiLineBuffer.length() == 0 && trimmedLine.toLowerCase().startsWith("delimiter ")) {
                String newDelim = trimmedLine.substring("delimiter ".length()).trim();
                if (!newDelim.isEmpty()) {
                    currentDelimiter = newDelim;
                    final String currentPrompt = promptTxt;
                    final String currentLine = line;
                    if (getActivity() != null && isAdded()) {
                        getActivity().runOnUiThread(() -> {
                            tvTerminalHistory.append(currentPrompt + currentLine + "\n");
                            refreshTerminalPrompt();
                            etCommandInput.setEnabled(true);
                            showKeyboard();
                        });
                    }
                    promptTxt = getExpectedPromptText();
                }
                continue;
            }

            // Handle help immediately
            boolean isHelpCmd = false;
            String helpTopic = null;
            if (multiLineBuffer.length() == 0) {
                if (trimmedLine.equalsIgnoreCase("help") || trimmedLine.equalsIgnoreCase("\\h") || "?".equals(trimmedLine)) {
                    isHelpCmd = true;
                } else if (trimmedLine.toLowerCase().startsWith("help ")) {
                    isHelpCmd = true;
                    helpTopic = trimmedLine.substring(5).trim();
                }
            }

            if (isHelpCmd) {
                final String currentPrompt = promptTxt;
                final String currentLine = line;
                final String topicVal = helpTopic;
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        tvTerminalHistory.append(currentPrompt + currentLine + "\n");
                        printHelpMessage(topicVal);
                        etCommandInput.setEnabled(true);
                        showKeyboard();
                    });
                }
                promptTxt = getExpectedPromptText();
                continue;
            }

            // Append line to buffer
            multiLineBuffer.append(line).append("\n");

            // Check if complete
            String accumulatedSql = multiLineBuffer.toString().trim();
            boolean isComplete = false;
            String cleanSql = accumulatedSql;

            if (currentDelimiter.equals(";")) {
                if (accumulatedSql.endsWith(";")) {
                    isComplete = true;
                    cleanSql = accumulatedSql.substring(0, accumulatedSql.length() - 1).trim();
                } else if (accumulatedSql.toLowerCase().endsWith("\\g")) {
                    isComplete = true;
                    cleanSql = accumulatedSql.substring(0, accumulatedSql.length() - 2).trim();
                }
            } else {
                if (accumulatedSql.endsWith(currentDelimiter)) {
                    isComplete = true;
                    cleanSql = accumulatedSql.substring(0, accumulatedSql.length() - currentDelimiter.length()).trim();
                }
            }

            if (!isComplete) {
                final String currentPrompt = promptTxt;
                final String currentLine = line;
                if (getActivity() != null && isAdded()) {
                    getActivity().runOnUiThread(() -> {
                        tvTerminalHistory.append(currentPrompt + currentLine + "\n");
                        refreshTerminalPrompt();
                        etCommandInput.setEnabled(true);
                        showKeyboard();
                    });
                }
                promptTxt = getExpectedPromptText();
                continue;
            }

            // Echo the final line of the statement
            final String finalPromptTxt = promptTxt;
            final String finalLine = line;
            final String finalCleanSql = cleanSql;

            // Save to query history
            String historySql = accumulatedSql.replace('\n', ' ').replaceAll("\\s+", " ").trim();
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    queryHistory.remove(historySql);
                    queryHistory.add(historySql);
                    historyIndex = -1;
                });
            }

            multiLineBuffer.setLength(0);

            // Execute the accumulated query on the background thread
            final QueryResult res = engine.execute(finalCleanSql);

            // Post result handling to main thread
            final Object lock = new Object();
            final boolean[] done = new boolean[1];
            if (getActivity() != null && isAdded()) {
                getActivity().runOnUiThread(() -> {
                    handleQueryResult(res, finalCleanSql, finalPromptTxt, finalLine, () -> {
                        synchronized (lock) {
                            done[0] = true;
                            lock.notifyAll();
                        }
                    });
                });
            }

            // Wait for handling of this query to finish before executing the next in the script
            synchronized (lock) {
                while (!done[0]) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            promptTxt = getExpectedPromptText();
        }
    }

    private void handleQueryResult(final QueryResult res, final String cleanSql, final String promptTxt, final String line, final Runnable onComplete) {
        if (getActivity() == null || !isAdded()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        if (res.success) {
            if (res.columns != null && res.rows != null) {
                // Check for empty result set - show "Empty set" like real MySQL
                if (res.rows.isEmpty()) {
                    SpannableStringBuilder sb = new SpannableStringBuilder();
                    sb.append(promptTxt).append(line).append("\n");
                    
                    String timeStr = String.format("%.2f", res.executionTimeMs / 1000.0);
                    sb.append("Empty set (").append(timeStr).append(" sec)\n\n");
                    
                    tvTerminalHistory.append(sb);
                    etCommandInput.setEnabled(true);
                    refreshTerminalPrompt();
                    showKeyboard();
                    if (settings.isAutoScroll()) {
                        scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
                    }
                    if (onComplete != null) onComplete.run();
                } else {
                    // Fetch query - animate output!
                    android.text.TextPaint paint = (tvTerminalHistory != null) ? tvTerminalHistory.getPaint() : new android.text.TextPaint();
                    float spacePx = (paint != null) ? paint.measureText(" ") : 8f;
                    if (spacePx <= 0) spacePx = 8f;
                    float dashPx = (paint != null) ? paint.measureText("-") : 10f;
                    if (dashPx <= 0) dashPx = 10f;

                    float[] targetContentPx = new float[res.columns.size()];
                    int[] colDashCounts = new int[res.columns.size()];

                    for (int i = 0; i < res.columns.size(); i++) {
                        float maxContentPx = paint.measureText(res.columns.get(i));
                        for (Map<String, Object> row : res.rows) {
                            Object val = row.get(res.columns.get(i));
                            String str = (val == null) ? "NULL" : val.toString().replace("\r", "").replace("\t", "    ");
                            String[] lines = str.split("\n", -1);
                            for (String lineStr : lines) {
                                float w = paint.measureText(lineStr);
                                if (w > maxContentPx) {
                                    maxContentPx = w;
                                }
                            }
                        }
                        int dashCount = Math.max(1, Math.round((maxContentPx + (2 * spacePx)) / dashPx));
                        colDashCounts[i] = dashCount;
                        targetContentPx[i] = (dashCount * dashPx) - (2 * spacePx);
                        if (targetContentPx[i] < maxContentPx) {
                            targetContentPx[i] = maxContentPx;
                        }
                    }

                    StringBuilder borderSb = new StringBuilder("+");
                    for (int count : colDashCounts) {
                        for (int d = 0; d < count; d++) {
                            borderSb.append("-");
                        }
                        borderSb.append("+");
                    }
                    String border = borderSb.toString();
                    double executionTimeSec = res.executionTimeMs / 1000.0;
                    
                    animateTableOutput(res.columns, res.rows, targetContentPx, border, paint, promptTxt, line, res.rows.size(), executionTimeSec, onComplete);
                }
            } else {
                // DML / DDL Output
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(promptTxt).append(line).append("\n");
                
                String timeStr = String.format("%.2f", res.executionTimeMs / 1000.0);
                SpannableString okSpan = new SpannableString(res.message + " (" + timeStr + " sec)\n\n");
                okSpan.setSpan(new ForegroundColorSpan(settings.getSuccessColor()),
                        0, okSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(okSpan);
                
                tvTerminalHistory.append(sb);
                etCommandInput.setEnabled(true);
                refreshTerminalPrompt();
                showKeyboard();
                if (settings.isAutoScroll()) {
                    scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
                }
                if (onComplete != null) onComplete.run();
            }
        } else {
            // Error Output
            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(promptTxt).append(line).append("\n");
            
            String errMsg = (res.message != null) ? res.message : "Unknown error";
            if (!errMsg.startsWith("ERROR ") && !errMsg.startsWith("Error:") && !errMsg.startsWith("ERROR:")) {
                errMsg = "ERROR: " + errMsg;
            }
            SpannableString errSpan = new SpannableString(errMsg + "\n\n");
            errSpan.setSpan(new ForegroundColorSpan(settings.getErrorColor()),
                    0, errSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(errSpan);
            
            tvTerminalHistory.append(sb);
            etCommandInput.setEnabled(true);
            refreshTerminalPrompt();
            showKeyboard();
            if (settings.isAutoScroll()) {
                scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
            }
            if (onComplete != null) onComplete.run();
        }
    }

    private void appendTableGridText(CharSequence text) {
        tvTerminalHistory.append(text);
    }

    private String padCellForFont(String s, float targetContentPx, android.text.TextPaint paint) {
        if (s == null) s = "";
        if (paint == null) return s;
        float currentPx = paint.measureText(s);
        if (currentPx >= targetContentPx) return s;
        float spacePx = paint.measureText(" ");
        if (spacePx <= 0) spacePx = 8f;
        int spacesNeeded = Math.max(0, Math.round((targetContentPx - currentPx) / spacePx));
        StringBuilder sb = new StringBuilder(s);
        for (int k = 0; k < spacesNeeded; k++) {
            sb.append(" ");
        }
        return sb.toString();
    }

    private void animateTableOutput(final List<String> columns, final List<Map<String, Object>> rows, final float[] targetContentPx, final String border, final android.text.TextPaint paint, String promptTxt, String line, final int rowCount, final double executionTimeSec, final Runnable onComplete) {
        final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        
        // 1. Print prompt + query line using user's font
        tvTerminalHistory.append(promptTxt + line + "\n");

        // 2. Print header table grid
        StringBuilder headerSb = new StringBuilder();
        headerSb.append(border).append("\n");
        headerSb.append("|");
        for (int i = 0; i < columns.size(); i++) {
            headerSb.append(" ").append(padCellForFont(columns.get(i), targetContentPx[i], paint)).append(" |");
        }
        headerSb.append("\n").append(border).append("\n");
        appendTableGridText(headerSb.toString());
        
        if (settings.isAutoScroll()) {
            scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN));
        }

        // 3. Animate rows
        final int delayMs = 15; // Animation speed: 15ms per row
        for (int i = 0; i < rows.size(); i++) {
            final int rowIndex = i;
            final Map<String, Object> row = rows.get(rowIndex);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null || !isAdded()) {
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    // Split cells of this row into lines
                    List<String[]> cellLines = new ArrayList<>();
                    int maxLines = 1;
                    for (int j = 0; j < columns.size(); j++) {
                        Object val = row.get(columns.get(j));
                        String str = (val == null) ? "NULL" : val.toString().replace("\r", "").replace("\t", "    ");
                        String[] lines = str.split("\n", -1);
                        cellLines.add(lines);
                        if (lines.length > maxLines) {
                            maxLines = lines.length;
                        }
                    }
                    
                    StringBuilder rowSb = new StringBuilder();
                    for (int l = 0; l < maxLines; l++) {
                        rowSb.append("|");
                        for (int j = 0; j < columns.size(); j++) {
                            String[] lines = cellLines.get(j);
                            String lineText = (l < lines.length) ? lines[l] : "";
                            rowSb.append(" ").append(padCellForFont(lineText, targetContentPx[j], paint)).append(" |");
                        }
                        rowSb.append("\n");
                    }
                    appendTableGridText(rowSb.toString());
                    
                    if (settings.isAutoScroll()) {
                        scrollContainer.fullScroll(View.FOCUS_DOWN);
                    }
                    
                    // Finalize animation on the last row
                    if (rowIndex == rows.size() - 1) {
                        appendTableGridText(border + "\n");

                        String timeStr = String.format("%.2f", executionTimeSec);
                        String footerMsg = String.valueOf(rowCount)
                                + (rowCount == 1 ? " row in set (" : " rows in set (")
                                + timeStr + " sec)\n\n";
                        tvTerminalHistory.append(footerMsg);
                        
                        etCommandInput.setEnabled(true);
                        refreshTerminalPrompt();
                        showKeyboard();
                        
                        if (settings.isAutoScroll()) {
                            scrollContainer.fullScroll(View.FOCUS_DOWN);
                        }
                        
                        if (onComplete != null) onComplete.run();
                    }
                }
            }, (long) (i + 1) * delayMs);
        }

        // Handle empty row set (safety fallback)
        if (rows.isEmpty()) {
            String timeStr = String.format("%.2f", executionTimeSec);
            tvTerminalHistory.append("Empty set (" + timeStr + " sec)\n\n");
            
            etCommandInput.setEnabled(true);
            refreshTerminalPrompt();
            showKeyboard();
            
            if (settings.isAutoScroll()) {
                scrollContainer.post(() -> scrollContainer.post(() -> scrollContainer.fullScroll(View.FOCUS_DOWN)));
            }
            
            if (onComplete != null) onComplete.run();
        }
    }

    private void printHelpMessage() {
        printHelpMessage(null);
    }

    private void printHelpMessage(String topic) {
        try {
            String query = (topic == null || topic.trim().isEmpty()) ? "HELP" : "HELP " + topic;
            QueryResult res = engine.execute(query);
            handleQueryResult(res, query, "", "", null);
        } catch (Exception e) {
            com.mysql.pocketsql.engine.SqlLog.printStackTrace(e);
            tvTerminalHistory.append("Error displaying help: " + e.getMessage() + "\n");
        }
    }

    // ASCII layout utility
    private String formatAsciiTable(List<String> columns, List<Map<String, Object>> rows) {
        if (columns == null || columns.isEmpty()) return "";
        android.text.TextPaint paint = (tvTerminalHistory != null) ? tvTerminalHistory.getPaint() : new android.text.TextPaint();
        float spacePx = (paint != null) ? paint.measureText(" ") : 8f;
        if (spacePx <= 0) spacePx = 8f;
        float dashPx = (paint != null) ? paint.measureText("-") : 10f;
        if (dashPx <= 0) dashPx = 10f;

        float[] targetContentPx = new float[columns.size()];
        int[] colDashCounts = new int[columns.size()];

        for (int i = 0; i < columns.size(); i++) {
            float maxContentPx = paint.measureText(columns.get(i));
            for (Map<String, Object> row : rows) {
                Object val = row.get(columns.get(i));
                String str = (val == null) ? "NULL" : val.toString().replace("\r", "").replace("\t", "    ");
                String[] lines = str.split("\n", -1);
                for (String lineStr : lines) {
                    float w = paint.measureText(lineStr);
                    if (w > maxContentPx) {
                        maxContentPx = w;
                    }
                }
            }
            int dashCount = Math.max(1, Math.round((maxContentPx + (2 * spacePx)) / dashPx));
            colDashCounts[i] = dashCount;
            targetContentPx[i] = (dashCount * dashPx) - (2 * spacePx);
            if (targetContentPx[i] < maxContentPx) {
                targetContentPx[i] = maxContentPx;
            }
        }

        StringBuilder borderSb = new StringBuilder("+");
        for (int count : colDashCounts) {
            for (int d = 0; d < count; d++) {
                borderSb.append("-");
            }
            borderSb.append("+");
        }
        String border = borderSb.toString();

        StringBuilder sb = new StringBuilder();
        sb.append(border).append("\n");

        sb.append("|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(" ").append(padCellForFont(columns.get(i), targetContentPx[i], paint)).append(" |");
        }
        sb.append("\n").append(border).append("\n");

        for (Map<String, Object> row : rows) {
            List<String[]> cellLines = new ArrayList<>();
            int maxLines = 1;
            for (int j = 0; j < columns.size(); j++) {
                Object val = row.get(columns.get(j));
                String str = (val == null) ? "NULL" : val.toString().replace("\r", "").replace("\t", "    ");
                String[] lines = str.split("\n", -1);
                cellLines.add(lines);
                if (lines.length > maxLines) {
                    maxLines = lines.length;
                }
            }

            for (int l = 0; l < maxLines; l++) {
                sb.append("|");
                for (int j = 0; j < columns.size(); j++) {
                    String[] lines = cellLines.get(j);
                    String lineText = (l < lines.length) ? lines[l] : "";
                    sb.append(" ").append(padCellForFont(lineText, targetContentPx[j], paint)).append(" |");
                }
                sb.append("\n");
            }
        }
        sb.append(border).append("\n");

        return sb.toString();
    }

    // ── Categorized SQL Templates Helpers ─────────────────────────────────────

    private static class TemplateCategory {
        final String name;
        final List<TemplateItem> items;
        TemplateCategory(String name, List<TemplateItem> items) {
            this.name = name;
            this.items = items;
        }
    }

    private static class TemplateItem {
        final String title;
        final String code;
        TemplateItem(String title, String code) {
            this.title = title;
            this.code = code;
        }
    }

    private List<TemplateCategory> getCategorizedTemplates() {
        List<TemplateCategory> list = new ArrayList<>();

        // 1. DML / Basic Queries
        List<TemplateItem> basic = new ArrayList<>();
        basic.add(new TemplateItem("Select All Rows", "SELECT * FROM <table>;"));
        basic.add(new TemplateItem("Select with Filter", "SELECT <col> FROM <table> WHERE <col> = '<val>';"));
        basic.add(new TemplateItem("Select Distinct values", "SELECT DISTINCT <col> FROM <table>;"));
        basic.add(new TemplateItem("Select from Another Database", "SELECT * FROM <db_name>.<table>;"));
        basic.add(new TemplateItem("Insert New Row", "INSERT INTO <table> (<col1>, <col2>) VALUES ('<v1>', '<v2>');"));
        basic.add(new TemplateItem("Update Row Values", "UPDATE <table> SET <col> = '<val>' WHERE <col> = '<val>';"));
        basic.add(new TemplateItem("Delete Rows", "DELETE FROM <table> WHERE <col> = '<val>';"));
        list.add(new TemplateCategory("DML / BASIC QUERIES", basic));

        // 2. Joins & Aggregates
        List<TemplateItem> joins = new ArrayList<>();
        joins.add(new TemplateItem("Inner Join (Matching rows)", "SELECT <col1>, <col2> FROM <table1> INNER JOIN <table2> ON <table1>.<id> = <table2>.<fk_id>;"));
        joins.add(new TemplateItem("Left Join (All left rows)", "SELECT <col1>, <col2> FROM <table1> LEFT JOIN <table2> ON <table1>.<id> = <table2>.<fk_id>;"));
        joins.add(new TemplateItem("Group By & Aggregates", "SELECT COUNT(*), SUM(<col>), AVG(<col>) FROM <table> GROUP BY <col>;"));
        joins.add(new TemplateItem("Group By with HAVING Filter", "SELECT <category_col>, COUNT(*) FROM <table> GROUP BY <category_col> HAVING COUNT(*) > 1;"));
        joins.add(new TemplateItem("Group By WITH ROLLUP Summary", "SELECT <category_col>, SUM(<amount_col>) FROM <table> GROUP BY <category_col> WITH ROLLUP;"));
        joins.add(new TemplateItem("Monthly Sales Summary (Group By Alias & Order By Functions)", "SELECT CONCAT(MONTHNAME(order_date), ' ', YEAR(order_date)) AS sales_month, COUNT(DISTINCT order_id) AS total_orders, SUM(quantity * unit_price) AS total_revenue FROM orders INNER JOIN order_items ON orders.order_id = order_items.order_id GROUP BY YEAR(order_date), MONTH(order_date), sales_month ORDER BY YEAR(order_date), MONTH(order_date);"));
        list.add(new TemplateCategory("JOINS & AGGREGATES", joins));

        // 3. CTE & Window Functions
        List<TemplateItem> cteWindow = new ArrayList<>();
        cteWindow.add(new TemplateItem("CTE (Common Table Expression)", "WITH RankedProducts AS (\n    SELECT p.category, p.product_id, p.product_name, SUM(oi.quantity * oi.unit_price) AS total_revenue,\n           DENSE_RANK() OVER (PARTITION BY p.category ORDER BY SUM(oi.quantity * oi.unit_price) DESC) AS `rank_in_category` \n    FROM products p INNER JOIN order_items oi ON p.product_id = oi.product_id\n    GROUP BY p.category, p.product_id, p.product_name\n)\nSELECT category, `rank_in_category` AS `rank`, product_id, product_name, total_revenue FROM RankedProducts WHERE `rank_in_category` <= 2 ORDER BY category, `rank_in_category`;"));
        cteWindow.add(new TemplateItem("DENSE_RANK() Window Function", "SELECT employee_id, department_id, salary, DENSE_RANK() OVER (PARTITION BY department_id ORDER BY salary DESC) AS `rank` FROM employees;"));
        cteWindow.add(new TemplateItem("ROW_NUMBER() Window Function", "SELECT id, name, ROW_NUMBER() OVER (ORDER BY created_at DESC) AS row_num FROM users;"));
        cteWindow.add(new TemplateItem("RANK() Window Function", "SELECT student_id, score, RANK() OVER (ORDER BY score DESC) AS rank_pos FROM exam_results;"));
        list.add(new TemplateCategory("CTE & WINDOW FUNCTIONS", cteWindow));

        // 4. ALTER TABLE Operations
        List<TemplateItem> alter = new ArrayList<>();
        alter.add(new TemplateItem("Add New Column", "ALTER TABLE <table> ADD COLUMN <col> <type>;"));
        alter.add(new TemplateItem("Modify Column Type", "ALTER TABLE <table> MODIFY COLUMN <col> <new_type>;"));
        alter.add(new TemplateItem("Change Column Name & Type", "ALTER TABLE <table> CHANGE COLUMN <old_col> <new_col> <type>;"));
        alter.add(new TemplateItem("Rename Column Name", "ALTER TABLE <table> RENAME COLUMN <old_col> TO <new_col>;"));
        alter.add(new TemplateItem("Drop Column", "ALTER TABLE <table> DROP COLUMN <col>;"));
        alter.add(new TemplateItem("Rename Table", "ALTER TABLE <table> RENAME TO <new_table_name>;"));
        alter.add(new TemplateItem("Add Unique Constraint", "ALTER TABLE <table> ADD CONSTRAINT <name> UNIQUE (<col>);"));
        alter.add(new TemplateItem("Add Check Constraint", "ALTER TABLE <table> ADD CONSTRAINT <name> CHECK (<col> > <val>);"));
        alter.add(new TemplateItem("Set ON UPDATE Constraint", "ALTER TABLE <table> ALTER COLUMN <col> SET ON UPDATE CURRENT_TIMESTAMP;"));
        alter.add(new TemplateItem("Drop ON UPDATE Constraint", "ALTER TABLE <table> ALTER COLUMN <col> DROP ON UPDATE;"));
        list.add(new TemplateCategory("ALTER TABLE OPERATIONS", alter));

        // 5. TRANSACTION CONTROL
        List<TemplateItem> tx = new ArrayList<>();
        tx.add(new TemplateItem("Start Transaction", "START TRANSACTION;"));
        tx.add(new TemplateItem("Commit Transaction", "COMMIT;"));
        tx.add(new TemplateItem("Rollback Transaction", "ROLLBACK;"));
        tx.add(new TemplateItem("Create Savepoint", "SAVEPOINT <savepoint_name>;"));
        tx.add(new TemplateItem("Rollback to Savepoint", "ROLLBACK TO <savepoint_name>;"));
        list.add(new TemplateCategory("TRANSACTION CONTROL", tx));

        // 6. SQL Functions
        List<TemplateItem> funcs = new ArrayList<>();
        funcs.add(new TemplateItem("Concat Strings", "SELECT CONCAT(<col1>, ' ', <col2>) AS full_val FROM <table>;"));
        funcs.add(new TemplateItem("Month Name, Year & Month", "SELECT MONTHNAME(order_date), YEAR(order_date), MONTH(order_date) FROM orders;"));
        funcs.add(new TemplateItem("Date Formatting (DATE_FORMAT)", "SELECT DATE_FORMAT(order_date, '%Y-%m') AS sales_month FROM orders;"));
        funcs.add(new TemplateItem("Upper/Lower Case & Length", "SELECT UPPER(<col>), LOWER(<col>), LENGTH(<col>) FROM <table>;"));
        funcs.add(new TemplateItem("Date & Time (Now, Curdate)", "SELECT NOW(), CURDATE(), CURTIME(), DATE_ADD(NOW(), INTERVAL 7 DAY);"));
        funcs.add(new TemplateItem("Conditionals (If, Ifnull)", "SELECT IF(<col> > <val>, 'Yes', 'No'), IFNULL(<col>, 'Default'), COALESCE(<col1>, <col2>);"));
        funcs.add(new TemplateItem("Case Expression", "SELECT CASE WHEN <col> = '<v1>' THEN 'A' WHEN <col> = '<v2>' THEN 'B' ELSE 'C' END FROM <table>;"));
        funcs.add(new TemplateItem("Backtick Identifier Escaping", "SELECT `rank_in_category` AS `rank` FROM `products`;"));
        funcs.add(new TemplateItem("Create JSON Object", "SELECT JSON_OBJECT('id', id, 'name', name) FROM <table>;"));
        funcs.add(new TemplateItem("Extract JSON Key", "SELECT JSON_EXTRACT(json_col, '$.key') FROM <table>;"));
        list.add(new TemplateCategory("SQL FUNCTIONS", funcs));

        // 7. Database & User Admin
        List<TemplateItem> admin = new ArrayList<>();
        admin.add(new TemplateItem("Create Table", "CREATE TABLE <table> (id INT, name TEXT, value DOUBLE);"));
        admin.add(new TemplateItem("Create Table with Unsigned", "CREATE TABLE <table> (id INT UNSIGNED PRIMARY KEY, name VARCHAR(50));"));
        admin.add(new TemplateItem("Create Database", "CREATE DATABASE <db_name>;"));
        admin.add(new TemplateItem("Use Database", "USE <db_name>;"));
        admin.add(new TemplateItem("Show Databases list", "SHOW DATABASES;"));
        admin.add(new TemplateItem("Show Tables list", "SHOW TABLES;"));
        admin.add(new TemplateItem("Show Tables from Database", "SHOW TABLES FROM <db_name>;"));
        admin.add(new TemplateItem("Describe Table schema", "DESCRIBE <table>;"));
        admin.add(new TemplateItem("Describe from Another Database", "DESCRIBE <db_name>.<table>;"));
        admin.add(new TemplateItem("Create View", "CREATE VIEW <view_name> AS SELECT <col> FROM <table>;"));
        admin.add(new TemplateItem("Interactive Help Index", "HELP;"));
        admin.add(new TemplateItem("Help for Keyword", "HELP <keyword>;"));
        admin.add(new TemplateItem("Drop Table", "DROP TABLE <table>;"));
        admin.add(new TemplateItem("Drop Table IF EXISTS", "DROP TABLE IF EXISTS <table>;"));
        admin.add(new TemplateItem("Drop Database", "DROP DATABASE <db_name>;"));
        admin.add(new TemplateItem("Create User Credentials", "CREATE USER '<user>'@'localhost' IDENTIFIED BY '<password>';"));
        admin.add(new TemplateItem("Grant User Privileges", "GRANT SELECT, INSERT, UPDATE, DELETE ON <db>.* TO '<user>'@'localhost';"));
        admin.add(new TemplateItem("Flush Admin Privileges", "FLUSH PRIVILEGES;"));
        list.add(new TemplateCategory("DATABASE & USER ADMIN", admin));

        // 8. EXPORT & IMPORT
        List<TemplateItem> backup = new ArrayList<>();
        backup.add(new TemplateItem("Export Database (SQL format)", "EXPORT DATABASE <db_name> TO '/sdcard/Download/<db_name>.sql';"));
        backup.add(new TemplateItem("Export Database (SQLite .db format)", "EXPORT DATABASE <db_name> TO '/sdcard/Download/<db_name>.db';"));
        backup.add(new TemplateItem("Export Database (Excel format)", "EXPORT DATABASE <db_name> TO '/sdcard/Download/<db_name>.xlsx';"));
        backup.add(new TemplateItem("Export Database (CSV format)", "EXPORT DATABASE <db_name> TO '/sdcard/Download/<db_name>.csv';"));
        backup.add(new TemplateItem("Import Database (SQL/DB/XLSX/CSV)", "IMPORT DATABASE <db_name> FROM '/sdcard/Download/<backup_file>';"));
        list.add(new TemplateCategory("EXPORT & IMPORT", backup));

        return list;
    }



    private void startApiService() {
        if (getActivity() == null) return;
        android.content.Context context = requireContext();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        android.content.Intent intent = new android.content.Intent(context, SqlApiService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O 
                && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private void stopApiService() {
        if (getActivity() == null) return;
        android.content.Context context = requireContext();
        android.content.Intent intent = new android.content.Intent(context, SqlApiService.class);
        context.stopService(intent);
    }

    private void performExport(android.net.Uri uri) {
        String activeDb = engine.getActiveDatabase();
        if (activeDb == null || activeDb.isEmpty()) {
            Toast.makeText(requireContext(), R.string.toast_no_db_export, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(requireContext(), R.string.toast_exporting_db, Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                try (android.os.ParcelFileDescriptor pfd = requireContext().getContentResolver().openFileDescriptor(uri, "w");
                     java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor())) {
                    com.mysql.pocketsql.engine.DatabaseExporter.exportDatabase(engine, activeDb, fos, selectedExportFormat);
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_export_success, activeDb), Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_export_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private void performImport(android.net.Uri uri) {
        String filename = getDisplayName(uri);
        String defaultDbName = "imported_db";
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            if (dot > 0) {
                defaultDbName = filename.substring(0, dot);
            }
        }
        defaultDbName = defaultDbName.replaceAll("[^a-zA-Z0-9_]", "");
        if (defaultDbName.isEmpty()) {
            defaultDbName = "imported_db";
        }

        final String finalDefaultDbName = defaultDbName;
        if (getActivity() == null) return;

        EditText etInput = new EditText(requireContext());
        etInput.setText(finalDefaultDbName);
        etInput.setSingleLine(true);
        etInput.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(requireContext())
            .setTitle("Import Database")
            .setMessage("Enter the database name to import into:")
            .setView(etInput)
            .setPositiveButton("Import", (dialog, which) -> {
                String dbName = etInput.getText().toString().trim().replaceAll("[^a-zA-Z0-9_]", "");
                if (dbName.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.toast_invalid_db_name, Toast.LENGTH_SHORT).show();
                    return;
                }
                startImportTask(uri, dbName);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startImportTask(android.net.Uri uri, String dbName) {
        Toast.makeText(requireContext(), R.string.toast_importing_db, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                try (android.os.ParcelFileDescriptor pfd = requireContext().getContentResolver().openFileDescriptor(uri, "r");
                     java.io.FileInputStream fis = new java.io.FileInputStream(pfd.getFileDescriptor())) {
                    com.mysql.pocketsql.engine.DatabaseExporter.importDatabase(engine, dbName, fis, null);
                }
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_import_success, dbName), Toast.LENGTH_LONG).show();
                        refreshTerminalPrompt();
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), getString(R.string.toast_import_failed, e.getMessage()), Toast.LENGTH_LONG).show();
                    });
                }
            }
        }).start();
    }

    private String getDisplayName(android.net.Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (android.database.Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
    private void showConnectionsDialog() {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_connections, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        LinearLayout container = dialogView.findViewById(R.id.connectionsListContainer);
        android.widget.Button btnClose = dialogView.findViewById(R.id.btnCloseConnections);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        
        android.widget.EditText etUsername = dialogView.findViewById(R.id.etConnUsername);
        android.widget.EditText etPassword = dialogView.findViewById(R.id.etConnPassword);
        android.widget.Button btnConnectManual = dialogView.findViewById(R.id.btnConnectManual);
        
        btnConnectManual.setOnClickListener(v -> {
            String u = etUsername.getText().toString().trim();
            String p = etPassword.getText().toString();
            String h = "localhost"; // default host
            if (u.isEmpty()) {
                Toast.makeText(requireContext(), R.string.toast_username_required, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            if (engine.authenticate(u, p)) {
                loginState = LOGIN_STATE_AUTHENTICATED;
                setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
                tvTerminalHistory.append("\n" + getWelcomeText());
                refreshTerminalPrompt();
                
                settings.setLastUsername(u);
                settings.setLastHost(h);
                settings.setLastPassword(p);
                settings.setAutoLogin(true);
                settings.saveConnection(u, h, p);
            } else {
                Toast.makeText(requireContext(), R.string.toast_auth_failed, Toast.LENGTH_SHORT).show();
            }
        });

        org.json.JSONArray connections = settings.getSavedConnections();
        for (int i = 0; i < connections.length(); i++) {
            try {
                org.json.JSONObject conn = connections.getJSONObject(i);
                String user = conn.optString("username");
                String host = conn.optString("host");
                String passEnc = conn.optString("password");
                String pass = "";
                if (!passEnc.isEmpty()) {
                    pass = com.mysql.pocketsql.engine.SecurityHelper.decrypt(passEnc);
                }
                
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, dpToPx(8), 0, dpToPx(8));
                
                TextView tvUser = new TextView(requireContext());
                tvUser.setText(user + "@" + host);
                tvUser.setTextColor(Color.WHITE);
                tvUser.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                lp.gravity = android.view.Gravity.CENTER_VERTICAL;
                tvUser.setLayoutParams(lp);
                row.addView(tvUser);
                
                android.widget.Button btnConnect = new android.widget.Button(requireContext());
                btnConnect.setText("Connect");
                String finalPass = pass;
                btnConnect.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (engine.authenticate(user, finalPass)) {
                        loginState = LOGIN_STATE_AUTHENTICATED;
                        setTerminalInputType(android.text.InputType.TYPE_CLASS_TEXT, false);
                        tvTerminalHistory.append("\n" + getWelcomeText());
                        refreshTerminalPrompt();
                        
                        settings.setLastUsername(user);
                        settings.setLastHost(host);
                        settings.setLastPassword(finalPass);
                        settings.setAutoLogin(true);
                    } else {
                        Toast.makeText(requireContext(), R.string.toast_saved_auth_failed, Toast.LENGTH_SHORT).show();
                    }
                });
                row.addView(btnConnect);
                
                android.widget.Button btnDelete = new android.widget.Button(requireContext());
                btnDelete.setText("X");
                btnDelete.setTextColor(Color.RED);
                btnDelete.setOnClickListener(v -> {
                    settings.removeConnection(user, host);
                    dialog.dismiss();
                    showConnectionsDialog(); // refresh
                });
                row.addView(btnDelete);
                
                container.addView(row);
            } catch (Exception e) {}
        }

        settings.applyFontToViewTree(dialogView);
        
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void promptSaveConnection(String username, String host, String password) {
        org.json.JSONArray connections = settings.getSavedConnections();
        boolean exists = false;
        for (int i = 0; i < connections.length(); i++) {
            org.json.JSONObject obj = connections.optJSONObject(i);
            if (obj != null && obj.optString("username").equals(username) && obj.optString("host").equals(host)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            new AlertDialog.Builder(requireContext())
                .setTitle("Save Connection")
                .setMessage("Do you want to save this connection for quick access?")
                .setPositiveButton("Save", (d, w) -> {
                    settings.saveConnection(username, host, password);
                    Toast.makeText(requireContext(), R.string.toast_conn_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("No", null)
                .show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
