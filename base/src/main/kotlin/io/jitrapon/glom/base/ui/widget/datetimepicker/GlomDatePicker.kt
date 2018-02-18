package io.jitrapon.glom.base.ui.widget.datetimepicker

import android.app.DatePickerDialog
import android.content.Context

/**
 * Created by Jitrapon
 */
class GlomDatePicker :  DatePickerDialog {

    constructor(context: Context, listener: DatePickerDialog.OnDateSetListener?, year: Int, month: Int, dayOfMonth: Int)
            : super(context, listener, year, month, dayOfMonth) {

    }
}