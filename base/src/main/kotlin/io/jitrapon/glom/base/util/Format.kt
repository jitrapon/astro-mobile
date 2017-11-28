package io.jitrapon.glom.base.util

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Global reusable formatter objects to be used throughout the application
 * Objects are initialized lazily. Internally is not thread-safe.
 *
 * @author Jitrapon Tiachunpun
 */
class Format {

    companion object {

        /**
         * Date format for format (i.e. Jun 20, 2017)
         */
        val DEFAULT_DATE_FORMAT: DateFormat by lazy {
            DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault())
        }

        /**
         * Time format (i.e. 3:30 PM or 15:30) depending on the system
         */
        val TIME_FORMAT: DateFormat by lazy {
            SimpleDateFormat("hh:mm a", Locale.getDefault())
        }
    }

    /**
     * Formats a specified time in epoch ms into a human-readable String using a date format
     */
    fun date(epochTimeMs: Long, format: DateFormat): String = format.format(Date(epochTimeMs))

    /**
     * Formats a specified time in epoch ms into a human-readable time String
     * Whether or not AM/PM shows depends on the current system settings
     */
    fun time(epochTimeMs: Long): String = TIME_FORMAT.format(Date(epochTimeMs))
}