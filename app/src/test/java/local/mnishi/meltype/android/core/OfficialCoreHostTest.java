// SPDX-License-Identifier: GPL-3.0-or-later
package local.mnishi.meltype.android.core;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OfficialCoreHostTest {
    @Test public void parsesOfficialClauseAndNumberedChoicesWithUtf16Offsets() throws JSONException {
        var response = OfficialCoreHost.parse(new JSONObject("""
                {"version":1,"state":{"languageMode":"Japanese"},
                 "result":{"consumed":true,"commits":[],"view":{
                 "text":"🙂日本語変換","converting":true,"clauses":["🙂日本語","変換"],
                 "selectedClause":1,"typedKeys":null,
                 "candidates":{"items":["返還","変換"],"selected":1,"numbered":true}}}}
                """));
        assertTrue(response.consumed());
        assertTrue(response.japanese());
        assertTrue(response.view().numbered);
        assertEquals(5, response.view().clauseStart);
        assertEquals(7, response.view().clauseEnd);
        assertEquals("変換", response.view().candidates.get(response.view().selectedIndex));
        assertEquals("", response.view().raw);
    }

    @Test public void preservesOfficialCorrectionAndCursorMovement() throws JSONException {
        var response = OfficialCoreHost.parse(new JSONObject("""
                {"version":1,"state":{"languageMode":"Alphanumeric"},
                 "result":{"consumed":false,"commits":[
                 {"text":"（）","removeBefore":"(", "moveCaretBack":1}],"view":null}}
                """));
        assertFalse(response.consumed());
        assertFalse(response.japanese());
        assertNull(response.view());
        assertEquals(new OfficialCoreHost.Edit("（）", "(", 1), response.edits().get(0));
    }

    @Test(expected = JSONException.class) public void rejectsUnexpectedVersion() throws JSONException {
        OfficialCoreHost.parse(new JSONObject("{\"version\":2}"));
    }
}
