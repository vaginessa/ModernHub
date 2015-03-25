/*
 * Copyright 2012 GitHub Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pw.bastiaan.github.tests.gist;

import static android.view.KeyEvent.KEYCODE_DEL;
import android.view.View;
import android.widget.EditText;

import pw.bastiaan.github.tests.ActivityTest;
import pw.bastiaan.github.ui.gist.CreateCommentActivity;

import org.eclipse.egit.github.core.Gist;
import org.eclipse.egit.github.core.User;

/**
 * Tests of {@link CreateCommentActivity}
 */
public class CreateCommentActivityTest extends
    ActivityTest<CreateCommentActivity> {

    /**
     * Create navigation_drawer_header_background
     */
    public CreateCommentActivityTest() {
        super(CreateCommentActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        setActivityIntent(CreateCommentActivity.createIntent(new Gist().setId(
            "123").setUser(new User().setLogin("abc"))));
    }

    /**
     * Verify empty comment can't be created
     *
     * @throws Throwable
     */
    public void testEmptyCommentIsProhitibed() throws Throwable {
        View createMenu = view(pw.bastiaan.github.R.id.m_apply);
        assertFalse(createMenu.isEnabled());
        final EditText comment = editText(pw.bastiaan.github.R.id.et_comment);
        focus(comment);
        send("a");
        assertTrue(createMenu.isEnabled());
        sendKeys(KEYCODE_DEL);
        assertFalse(createMenu.isEnabled());
    }
}
