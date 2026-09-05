package net.typeblog.shelter.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.preference.CheckBoxPreference;

/**
 * SECURITY-CRITICAL TEMPORARY ACTION PREFERENCE
 *
 * This preference behaves like a push/action button with a
 * temporary checked (pressed) visual state.
 *
 * SECURITY RULES:
 *
 * 1. The state is NEVER persisted.
 * 2. Every newly created instance starts unchecked.
 * 3. Pressing the key only marks the action as selected.
 * 4. Pressing the key MUST NOT execute the security operation.
 * 5. The actual operation is performed only by
 *    SecurityPolicyChangeManager after explicit confirmation
 *    and successful authentication.
 *
 * This class is intentionally reusable for any future
 * security action that needs the same behaviour.
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
         * This preference is transient.
         * Android Preference must never persist its checked state.
         */
        setPersistent(false);

        /*
         * Every new page starts with this action unpressed.
         */
        setChecked(false);
    }

    /**
     * Marks this action as selected.
     *
     * This does NOT execute the operation.
     */
    public void press() {
        setChecked(true);
    }

    /**
     * Clears the temporary state.
     */
    public void reset() {
        setChecked(false);
    }

    /**
     * Returns whether this action has been selected
     * in the current page/transaction.
     */
    public boolean isPressed() {
        return isChecked();
    }

    /**
     * Do not allow a second click to undo the action.
     *
     * The requested behaviour is:
     *
     * NOT PRESSED
     *      ↓
     * click
     *      ↓
     * PRESSED
     *
     * and it remains pressed until the current page/transaction
     * is discarded.
     */
    @Override
    protected void onClick() {

        if (!isChecked()) {
            setChecked(true);
        }
    }
}
