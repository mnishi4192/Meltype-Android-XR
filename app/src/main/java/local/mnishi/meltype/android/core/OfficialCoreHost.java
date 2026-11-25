// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import io.github.ninjin0802.keyboard.app.core.NativeApi;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Typed binding for the upstream ImeHost version 1 JSON protocol. */
public final class OfficialCoreHost implements AutoCloseable {
    public record Field(String before, String after, String selection, boolean composing, boolean canRemove,
                        String app, List<String> traits) {}
    public record Edit(String text, String removeBefore, int moveCaretBack) {}
    public record Output(boolean consumed, boolean japanese, List<Edit> edits, CompositionView view) {}
    private static final int VERSION = 1;
    private long handle;

    public OfficialCoreHost(File dataDirectory, OfficialNativeCallbacks callbacks) {
        if (!dataDirectory.isDirectory() && !dataDirectory.mkdirs())
            throw new IllegalStateException("Cannot create official Core data directory");
        if (!NativeApi.init(utf8(dataDirectory.getAbsolutePath()), callbacks))
            throw new IllegalStateException("Official Core initialization failed");
        handle = NativeApi.create();
        if (handle == 0) throw new IllegalStateException("Official Core returned no IME host");
    }

    public Output key(Field field, int vk, int ch, int modifiers, boolean repeat) {
        return send("KeyPressed", field, "key", json("vk", vk, "ch", ch,
                "shift", (modifiers & 1) != 0, "ctrl", (modifiers & 2) != 0,
                "alt", (modifiers & 4) != 0, "command", (modifiers & 8) != 0, "repeat", repeat));
    }

    public Output event(String event, Field field) { return send(event, field, null, null); }

    public Output caret(String event, Field field, boolean focusEntered, boolean focusLeft) {
        return send(event, field, "caret", json("moved", true, "passedKey", 0,
                "focusEntered", focusEntered, "focusLeft", focusLeft));
    }

    public Output select(Field field, int index) { return send("CandidateClicked", field, "index", index); }

    private Output send(String event, Field field, String argument, Object value) {
        JSONObject message = json("version", VERSION, "instance", "android", "type", event);
        try {
            if (field != null) message.put("field", json("before", field.before, "after", field.after,
                    "selection", field.selection, "composing", field.composing, "canRemove", field.canRemove,
                    "app", field.app, "traits", new JSONArray(field.traits)));
            if (argument != null) message.put(argument, value);
            byte[] response = NativeApi.handle(handle, utf8(message.toString()));
            if (response == null) throw new IllegalStateException("Official Core returned no response");
            return parse(new JSONObject(new String(response, StandardCharsets.UTF_8)));
        } catch (JSONException badProtocol) {
            throw new IllegalStateException("Invalid official Core response", badProtocol);
        }
    }

    static Output parse(JSONObject output) throws JSONException {
        if (output.getInt("version") != VERSION) throw new JSONException("Unexpected IME protocol version");
        JSONObject result = output.getJSONObject("result");
        boolean japanese = output.getJSONObject("state").getString("languageMode").equals("Japanese");
        JSONArray commits = result.getJSONArray("commits");
        List<Edit> edits = new ArrayList<>();
        for (int i = 0; i < commits.length(); i++) {
            JSONObject edit = commits.getJSONObject(i);
            edits.add(new Edit(edit.getString("text"), edit.isNull("removeBefore") ? null : edit.getString("removeBefore"),
                    edit.getInt("moveCaretBack")));
        }
        JSONObject view = result.optJSONObject("view");
        return new Output(result.getBoolean("consumed"), japanese, edits, view == null ? null : parseView(view));
    }

    private static CompositionView parseView(JSONObject view) throws JSONException {
        String text = view.getString("text");
        if (text.isEmpty()) return null;
        boolean converting = view.getBoolean("converting");
        JSONArray clauses = view.getJSONArray("clauses");
        int active = view.getInt("selectedClause");
        int clauseStart = -1;
        int clauseEnd = -1;
        if (converting && active >= 0 && active < clauses.length()) {
            int offset = 0;
            for (int i = 0; i < active; i++) offset += clauses.getString(i).length();
            int end = offset + clauses.getString(active).length();
            if (end <= text.length()) {
                clauseStart = offset;
                clauseEnd = end;
            }
        }
        JSONObject list = view.optJSONObject("candidates");
        List<String> candidates = new ArrayList<>();
        int selected = -1;
        boolean numbered = false;
        if (list != null) {
            JSONArray items = list.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) candidates.add(items.getString(i));
            selected = list.getInt("selected");
            numbered = list.optBoolean("numbered");
        }
        return new CompositionView(text, view.isNull("typedKeys") ? "" : view.getString("typedKeys"), candidates, selected,
                converting, numbered, clauseStart, clauseEnd);
    }

    private static byte[] utf8(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    private static JSONObject json(Object... fields) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < fields.length; i += 2) {
                if (fields[i + 1] != null) result.put((String) fields[i], fields[i + 1]);
            }
        } catch (JSONException impossible) {
            throw new IllegalArgumentException("Invalid IME event value", impossible);
        }
        return result;
    }

    @Override public void close() {
        long previous = handle;
        handle = 0;
        if (previous != 0) NativeApi.destroy(previous);
    }
}
