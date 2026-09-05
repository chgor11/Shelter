package net.typeblog.shelter.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.CheckBoxPreference;

/**
 * SECURITY-CRITICAL TEMPORARY ACTION PREFERENCE
 *
 * This preference is a UI-only transaction selector.
 *
 * SECURITY RULES:
 *
 * 1. The checked state is NEVER persisted.
 * 2. Every newly created instance starts unchecked.
 * 3. A click only selects the action.
 * 4. A click MUST NOT execute the security operation.
 * 5. The actual operation is performed exclusively by
 *    SecurityPolicyChangeManager after explicit confirmation
 *    and successful fresh authentication.
 *
 * State machine:
 *
 *      NOT PRESSED
 *           |
 *         click
 *           |
 *           v
 *       PRESSED
 *
 * A second click cannot undo the selection.
 */
public class TemporaryActionPreference
        extends CheckBoxPreference {

    public TemporaryActionPreference(Context context) {
        super(context);
        initialize();
    }

    public TemporaryActionPreference(
            Context context,
            AttributeSet attrs) {

        super(context, attrs);
        initialize();
    }

    public TemporaryActionPreference(
            Context context,
            AttributeSet attrs,
            int defStyleAttr) {

        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {

        /*
         * SECURITY-CRITICAL:
         *
         * This preference is transient and MUST NOT
         * write its checked state to persistent storage.
         */
        setPersistent(false);

        /*
         * Every newly created page starts unpressed.
         */
        setChecked(false);
    }

    /**
     * Selects the action.
     *
     * This method performs NO security operation.
     */
    public void press() {
        setChecked(true);
    }

    /**
     * Clears the temporary UI state.
     */
    public void reset() {
        setChecked(false);
    }

    /**
     * Returns whether the action is currently selected.
     */
    public boolean isPressed() {
        return isChecked();
    }

    /**
     * Prevent a second click from toggling the action off.
     *
     * The preference therefore behaves as a temporary
     * action button rather than a normal toggle switch.
     */
    @Override
    protected void onClick() {

        if (!isChecked()) {
            setChecked(true);
        }
    }
}
