// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.ime;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;
import local.mnishi.meltype.android.core.CompositionView;
import local.mnishi.meltype.android.core.InferenceStatus;
import local.mnishi.meltype.android.core.OfficialCoreConfig;
import local.mnishi.meltype.android.core.OfficialCoreHost;
import local.mnishi.meltype.android.core.OfficialNativeCallbacks;
import local.mnishi.meltype.android.input.EditorPolicy;
import local.mnishi.meltype.android.input.FieldPolicy;
import local.mnishi.meltype.android.input.KeyInput;
import local.mnishi.meltype.android.input.KeyNormalizer;
import local.mnishi.meltype.android.settings.MeltypePrefs;
import local.mnishi.meltype.android.settings.SettingsActivity;

/** Android lifecycle and scheduling only; composition and editor application are separate. */
public class MeltypeImeService extends InputMethodService {
    private final Handler main = new Handler(Looper.getMainLooper());
    private MeltypePrefs preferences;
    private OfficialImeController controller;
    private OfficialNativeCallbacks nativeCallbacks;
    private File coreDirectory;
    private HardwareCandidatePopup hardwarePopup;
    private boolean cursorMonitoring;
    private CandidateBarView bar;
    private XrKeyboardView keyboard;
    private Boolean keyboardOverride;
    private boolean hiddenByPhysicalTyping;
    private boolean allowPreview;
    private boolean runtimeFailed;
    private final Runnable refreshConversion = () -> {
        if (controller != null) controller.conversionFinished();
    };
    private final Runnable onConversionReady = () -> {
        main.removeCallbacks(refreshConversion);
        main.post(refreshConversion);
    };
    private final Consumer<String> statusObserver =
            message -> main.post(() -> { if (bar != null) bar.setRuntimeStatus(message, runtimeFailed); });
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (prefs, key) -> settingsChanged(key);

    @Override public void onCreate() {
        super.onCreate();
        preferences = new MeltypePrefs(this);
        coreDirectory = new File(getNoBackupFilesDir(), "meltype");
        nativeCallbacks = OfficialNativeCallbacks.get(this);
        nativeCallbacks.setThreadCount(preferences.inferenceThreads());
        nativeCallbacks.setReadyListener(onConversionReady);
        OfficialCoreHost host = null;
        try {
            OfficialCoreConfig.save(coreDirectory, preferences.inputOptions());
            host = new OfficialCoreHost(coreDirectory, nativeCallbacks);
            if (!nativeCallbacks.isCpuReady()) InferenceStatus.set("公式Coreが準備できました。ZenzaiはCPUで実行します。");
        } catch (IOException | RuntimeException | LinkageError failure) {
            reportCoreFailure(failure);
        }
        CandidateDisplay display = new CandidateDisplay() {
            @Override public void showComposition(CompositionView view) {
                presentComposition(view);
                if (keyboard != null) keyboard.setComposing(view != null);
            }
            @Override public void showMode(String mode) {
                if (bar != null) bar.showMode(mode);
                if (keyboard != null) keyboard.setLanguage("あ".equals(mode));
            }
        };
        controller = new OfficialImeController(host, new OfficialEditor(this::getCurrentInputConnection),
                new InputConnectionSink(this::getCurrentInputConnection, this::getCurrentInputEditorInfo),
                display, this::reportCoreFailure);
        controller.setSettings(preferences.ctrlSpaceToggles(), preferences.inputOptions().commitOnPunctuation);
        preferences.register(settingsListener);
        InferenceStatus.observe(statusObserver);
    }

    private void reportCoreFailure(Throwable failure) {
        runtimeFailed = true;
        Log.e("MeltypeCore", "Official Core could not handle Android input", failure);
        InferenceStatus.set("公式変換コアを利用できません。直接入力とIME切り替えを使えます。");
    }

    @Override public View onCreateInputView() {
        if (keyboard != null) keyboard.cancelGestures();
        bar = new CandidateBarView(this, new CandidateBarView.Listener() {
            @Override public void onCandidateSelected(int index) { controller.selectCandidate(index); }
            @Override public void onToggleDirect() { controller.toggleDirect(); }
            @Override public void onConvert(int vk) { controller.convert(vk); }
            @Override public void onSwitchIme() { showOtherImePicker(); }
            @Override public void onToggleKeyboard() {
                hiddenByPhysicalTyping = false;
                if (bar.isExpanded()) {
                    keyboardOverride = true;
                    bar.setExpanded(false);
                } else keyboardOverride = keyboard.getVisibility() != View.VISIBLE;
                updateKeyboardVisibility();
            }
            @Override public void onCandidateListChanged() { updateKeyboardVisibility(); }
            @Override public void onSettings() {
                controller.prepareImeSwitch();
                startActivity(new Intent(MeltypeImeService.this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        });
        keyboard = new XrKeyboardView(this, new XrKeyboardView.Listener() {
            @Override public void onText(String text) { controller.softText(text); }
            @Override public void onFlickText(String text) { controller.softFlickText(text); }
            @Override public void onSpace() { controller.softSpace(); }
            @Override public boolean onCursorMove(int keyCode) { return controller.softMoveCursor(keyCode); }
            @Override public void onDelete() { controller.softDelete(); }
            @Override public void onEnter() {
                EditorInfo info = getCurrentInputEditorInfo();
                if (info != null) controller.softEnter(info.imeOptions, info.actionId, info.actionLabel != null);
            }
        });
        keyboard.setDragOptions(preferences.spaceDragMovesCursor(), preferences.backspaceDragRepeatsDeletion());
        keyboard.setEditor(getCurrentInputEditorInfo());
        keyboard.setComposing(controller.view() != null);
        keyboard.setLanguage("あ".equals(controller.modeLabel()));
        bar.showMode(controller.modeLabel());
        bar.setRuntimeStatus(InferenceStatus.get(), runtimeFailed);
        updateKeyboardVisibility();
        ImePanel panel = new ImePanel(this, bar, keyboard);
        if (hardwarePopup != null) hardwarePopup.reset();
        hardwarePopup = new HardwareCandidatePopup(panel, controller::selectCandidate, () -> {
            if (bar != null && hardwarePopup != null) bar.setHardwareCandidatesVisible(hardwarePopup.isShowing());
        });
        presentComposition(controller.view());
        return panel;
    }

    private void updateKeyboardVisibility() {
        if (keyboard == null) return;
        boolean visible = keyboardOverride != null ? keyboardOverride : keyboardVisibleByDefault();
        visible &= !hiddenByPhysicalTyping && !bar.isExpanded();
        keyboard.setVisibility(visible ? View.VISIBLE : View.GONE);
        bar.setKeyboardVisible(visible, allowPreview);
    }

    private boolean keyboardVisibleByDefault() {
        // An XR compute puck can report a hardware input device even during gesture typing.
        if (getPackageManager().hasSystemFeature("android.software.xr.api.spatial")
                || getPackageManager().hasSystemFeature("android.software.xr.api.openxr")) return true;
        Configuration config = getResources().getConfiguration();
        return config.keyboard != Configuration.KEYBOARD_QWERTY
                || config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    @Override public boolean onEvaluateInputViewShown() {
        super.onEvaluateInputViewShown();
        // Keep the candidate bar and keyboard toggle reachable with a physical keyboard attached.
        return true;
    }

    @Override public boolean onEvaluateFullscreenMode() { return false; }

    @Override public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (keyboard != null) keyboard.cancelGestures();
        cursorMonitoring = false;
        if (hardwarePopup != null) hardwarePopup.reset();
        FieldPolicy policy = EditorPolicy.of(info == null ? 0 : info.inputType, info == null ? 0 : info.imeOptions);
        allowPreview = !policy.bypass && !policy.privateInput;
        controller.startInput(policy, info);
        if (keyboard != null) keyboard.setEditor(info);
        updateKeyboardVisibility();
        updateInputViewShown();
        if (info != null && info.inputType != InputType.TYPE_NULL) requestShowSelf(0);
    }

    @Override public void onFinishInput() {
        if (keyboard != null) keyboard.cancelGestures();
        stopCursorMonitoring();
        if (hardwarePopup != null) hardwarePopup.reset();
        if (controller != null) controller.finishInput();
        super.onFinishInput();
    }

    @Override public void onUpdateSelection(int oldStart, int oldEnd, int start, int end, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, start, end, candidatesStart, candidatesEnd);
        if (controller != null) controller.selectionChanged(start, end, candidatesStart, candidatesEnd);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        InputDevice device = event.getDevice();
        KeyInput key = new KeyInput(event.getDeviceId(), keyCode, event.getUnicodeChar(), event.getUnicodeChar(0),
                event.getMetaState(), event.getRepeatCount(), event.getDownTime(), device != null && !device.isVirtual());
        if (!hiddenByPhysicalTyping && getCurrentInputEditorInfo() != null && KeyNormalizer.isPhysicalTyping(key)
                && preferences.hideKeyboardOnPhysicalTyping()) {
            hiddenByPhysicalTyping = true;
            updateKeyboardVisibility();
        }
        if (controller.keyDown(key)) {
            if (!isInputViewShown()) requestShowSelf(0);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        return controller.keyUp(event.getDeviceId(), keyCode, event.getDownTime()) || super.onKeyUp(keyCode, event);
    }

    private void showOtherImePicker() {
        if (keyboard != null) keyboard.cancelGestures();
        controller.prepareImeSwitch();
        getSystemService(InputMethodManager.class).showInputMethodPicker();
    }

    private void presentComposition(CompositionView composition) {
        if (bar != null) bar.showComposition(composition);
        boolean physical = controller != null && controller.physicalPresentation()
                && composition != null && composition.converting && !composition.candidates.isEmpty();
        if (hardwarePopup != null) {
            hardwarePopup.show(physical ? composition : null, physical ? composition.clauseStart : -1);
        }
        if (physical && !cursorMonitoring) {
            cursorMonitoring = requestCursorUpdates(InputConnection.CURSOR_UPDATE_IMMEDIATE
                    | InputConnection.CURSOR_UPDATE_MONITOR);
        } else if (!physical && cursorMonitoring) {
            stopCursorMonitoring();
        }
    }

    @Override public void onUpdateCursorAnchorInfo(CursorAnchorInfo info) {
        super.onUpdateCursorAnchorInfo(info);
        if (hardwarePopup != null && cursorMonitoring) hardwarePopup.onCursorAnchorInfo(info);
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        if (keyboard != null) keyboard.cancelGestures();
        stopCursorMonitoring();
        if (hardwarePopup != null) hardwarePopup.reset();
        // The same editor can keep receiving physical keys with the IME view hidden.
        // The default implementation finishes its composing span independently of Core.
    }

    private void stopCursorMonitoring() {
        if (!cursorMonitoring) return;
        cursorMonitoring = false;
        requestCursorUpdates(0);
    }

    private boolean requestCursorUpdates(int mode) {
        InputConnection input = getCurrentInputConnection();
        try {
            return input != null && input.requestCursorUpdates(mode);
        } catch (RuntimeException unsupported) {
            return false;
        }
    }

    private void settingsChanged(String key) {
        if (MeltypePrefs.isKeyboardSetting(key)) {
            if (!preferences.hideKeyboardOnPhysicalTyping()) hiddenByPhysicalTyping = false;
            if (keyboard != null) {
                keyboard.setDragOptions(preferences.spaceDragMovesCursor(), preferences.backspaceDragRepeatsDeletion());
            }
            updateKeyboardVisibility();
        }
        controller.setSettings(preferences.ctrlSpaceToggles(), preferences.inputOptions().commitOnPunctuation);
        if (MeltypePrefs.isInferenceSetting(key)) nativeCallbacks.setThreadCount(preferences.inferenceThreads());
        if (MeltypePrefs.isInputSetting(key)) {
            try {
                OfficialCoreConfig.save(coreDirectory, preferences.inputOptions());
            } catch (IOException failure) {
                Log.w("MeltypeCore", "Could not update official Core configuration", failure);
            }
        }
    }

    @Override public void onDestroy() {
        if (keyboard != null) keyboard.cancelGestures();
        if (preferences != null) preferences.unregister(settingsListener);
        InferenceStatus.remove(statusObserver);
        if (nativeCallbacks != null) nativeCallbacks.clearReadyListener(onConversionReady);
        if (hardwarePopup != null) hardwarePopup.reset();
        if (controller != null) controller.close();
        controller = null;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
