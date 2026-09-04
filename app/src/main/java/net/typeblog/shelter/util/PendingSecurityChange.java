package net.typeblog.shelter.util;

/*
 * SECURITY POLICY ADMINISTRATION RULE
 *
 * This class only represents a pending security change.
 *
 * No security policy may be executed from this class.
 *
 * All changes must pass through:
 *
 * Pending Change
 *      ->
 * Review Summary
 *      ->
 * User Confirmation
 *      ->
 * Fresh Authentication
 *      ->
 * Apply Policy
 */

public class PendingSecurityChange {

    private final String policyName;
    private final String oldValue;
    private final String newValue;


    public PendingSecurityChange(
            String policyName,
            String oldValue,
            String newValue) {

        this.policyName = policyName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }


    public String getPolicyName() {
        return policyName;
    }


    public String getOldValue() {
        return oldValue;
    }


    public String getNewValue() {
        return newValue;
    }
}
