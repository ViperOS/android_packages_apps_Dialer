package com.android.incallui;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.android.services.telephony.common.Call;

import java.util.Arrays;

/**
 * TODO: Insert description here. (generated by yorkelee)
 */
public class CallerInfoUtils {

    private static final String TAG = CallerInfoUtils.class.getSimpleName();

    /** Define for not a special CNAP string */
    private static final int CNAP_SPECIAL_CASE_NO = -1;

    public CallerInfoUtils() {
    }

    private static final int QUERY_TOKEN = -1;

    /**
     * This is called to get caller info for a call. For outgoing calls, uri should not be null
     * because we know which contact uri the user selected to make the outgoing call. This
     * will return a CallerInfo object immediately based off information in the call, but
     * more information is returned to the OnQueryCompleteListener (which contains
     * information about the phone number label, user's name, etc).
     */
    public static CallerInfo getCallerInfoForCall(Context context, Call call, Uri uri,
            CallerInfoAsyncQuery.OnQueryCompleteListener listener) {
        CallerInfo info = new CallerInfo();
        String number = call.getNumber();

        // Store CNAP information retrieved from the Connection (we want to do this
        // here regardless of whether the number is empty or not).
        info.cnapName = call.getCnapName();
        info.name = info.cnapName;
        info.numberPresentation = call.getNumberPresentation();
        info.namePresentation = call.getCnapNamePresentation();

        if (uri != null) {
            // Have an URI, so pass it to startQuery
            CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context, uri, listener, call);
        } else {
            if (!TextUtils.isEmpty(number)) {
                number = modifyForSpecialCnapCases(context, info, number, info.numberPresentation);
                info.phoneNumber = number;

                // For scenarios where we may receive a valid number from the network but a
                // restricted/unavailable presentation, we do not want to perform a contact query,
                // so just return the existing caller info.
                if (info.numberPresentation != Call.PRESENTATION_ALLOWED) {
                    return info;
                } else {
                    // Start the query with the number provided from the call.
                    Logger.d(TAG, "==> Actually starting CallerInfoAsyncQuery.startQuery()...");
                    CallerInfoAsyncQuery.startQuery(QUERY_TOKEN, context, number, listener, call);
                }
            } else {
                // The number is null or empty (Blocked caller id or empty). Just return the
                // caller info object as is, without starting a query.
                return info;
            }
        }

        return info;
    }

    /**
     * Handles certain "corner cases" for CNAP. When we receive weird phone numbers
     * from the network to indicate different number presentations, convert them to
     * expected number and presentation values within the CallerInfo object.
     * @param number number we use to verify if we are in a corner case
     * @param presentation presentation value used to verify if we are in a corner case
     * @return the new String that should be used for the phone number
     */
    /* package */static String modifyForSpecialCnapCases(Context context, CallerInfo ci,
            String number, int presentation) {
        // Obviously we return number if ci == null, but still return number if
        // number == null, because in these cases the correct string will still be
        // displayed/logged after this function returns based on the presentation value.
        if (ci == null || number == null) return number;

        Logger.d(TAG, "modifyForSpecialCnapCases: initially, number="
                + toLogSafePhoneNumber(number)
                + ", presentation=" + presentation + " ci " + ci);

        // "ABSENT NUMBER" is a possible value we could get from the network as the
        // phone number, so if this happens, change it to "Unknown" in the CallerInfo
        // and fix the presentation to be the same.
        final String[] absentNumberValues =
                context.getResources().getStringArray(R.array.absent_num);
        if (Arrays.asList(absentNumberValues).contains(number)
                && presentation == Call.PRESENTATION_ALLOWED) {
            number = context.getString(R.string.unknown);
            ci.numberPresentation = Call.PRESENTATION_UNKNOWN;
        }

        // Check for other special "corner cases" for CNAP and fix them similarly. Corner
        // cases only apply if we received an allowed presentation from the network, so check
        // if we think we have an allowed presentation, or if the CallerInfo presentation doesn't
        // match the presentation passed in for verification (meaning we changed it previously
        // because it's a corner case and we're being called from a different entry point).
        if (ci.numberPresentation == Call.PRESENTATION_ALLOWED
                || (ci.numberPresentation != presentation
                        && presentation == Call.PRESENTATION_ALLOWED)) {
            int cnapSpecialCase = checkCnapSpecialCases(number);
            if (cnapSpecialCase != CNAP_SPECIAL_CASE_NO) {
                // For all special strings, change number & numberPresentation.
                if (cnapSpecialCase == Call.PRESENTATION_RESTRICTED) {
                    number = context.getString(R.string.private_num);
                } else if (cnapSpecialCase == Call.PRESENTATION_UNKNOWN) {
                    number = context.getString(R.string.unknown);
                }
                Logger.d(TAG, "SpecialCnap: number=" + toLogSafePhoneNumber(number)
                        + "; presentation now=" + cnapSpecialCase);
                ci.numberPresentation = cnapSpecialCase;
            }
        }
        Logger.d(TAG, "modifyForSpecialCnapCases: returning number string="
                + toLogSafePhoneNumber(number));
        return number;
    }

    /**
     * Based on the input CNAP number string,
     * @return _RESTRICTED or _UNKNOWN for all the special CNAP strings.
     * Otherwise, return CNAP_SPECIAL_CASE_NO.
     */
    private static int checkCnapSpecialCases(String n) {
        if (n.equals("PRIVATE") ||
                n.equals("P") ||
                n.equals("RES")) {
            Logger.d(TAG, "checkCnapSpecialCases, PRIVATE string: " + n);
            return Call.PRESENTATION_RESTRICTED;
        } else if (n.equals("UNAVAILABLE") ||
                n.equals("UNKNOWN") ||
                n.equals("UNA") ||
                n.equals("U")) {
            Logger.d(TAG, "checkCnapSpecialCases, UNKNOWN string: " + n);
            return Call.PRESENTATION_UNKNOWN;
        } else {
            Logger.d(TAG, "checkCnapSpecialCases, normal str. number: " + n);
            return CNAP_SPECIAL_CASE_NO;
        }
    }

    /* package */static String toLogSafePhoneNumber(String number) {
        // For unknown number, log empty string.
        if (number == null) {
            return "";
        }

        // Todo (klp): Figure out an equivalent for VDBG
        if (false) {
            // When VDBG is true we emit PII.
            return number;
        }

        // Do exactly same thing as Uri#toSafeString() does, which will enable us to compare
        // sanitized phone numbers.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c == '-' || c == '@' || c == '.') {
                builder.append(c);
            } else {
                builder.append('x');
            }
        }
        return builder.toString();
    }
}
