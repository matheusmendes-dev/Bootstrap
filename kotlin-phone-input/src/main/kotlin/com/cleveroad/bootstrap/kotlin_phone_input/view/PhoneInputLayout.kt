package com.cleveroad.bootstrap.kotlin_phone_input.view

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.cleveroad.bootstrap.kotlin_phone_input.Constants.DEFAULT_COUNTRY_MASK
import com.cleveroad.bootstrap.kotlin_phone_input.Constants.EMPTY_STRING
import com.cleveroad.bootstrap.kotlin_phone_input.Constants.PHONE_PREF
import com.cleveroad.bootstrap.kotlin_phone_input.R
import com.cleveroad.bootstrap.kotlin_phone_input.data.models.CountryAsset
import com.cleveroad.bootstrap.kotlin_phone_input.data.models.KParcelable
import com.cleveroad.bootstrap.kotlin_phone_input.data.models.read
import com.cleveroad.bootstrap.kotlin_phone_input.data.models.write
import com.cleveroad.bootstrap.kotlin_phone_input.utils.*
import com.cleveroad.bootstrap.kotlin_phone_input.utils.CountryFlag.getCountryFlagByCode
import com.google.android.material.textfield.TextInputLayout
import com.redmadrobot.inputmask.MaskedTextChangedListener

class PhoneInputLayout : LinearLayout {

    companion object {
        private const val COEFFICIENT_FOR_ICON_PREVIEW = 2.5
        private const val ICON_SIZE_MIN = 20
        private const val ICON_SIZE_MAX = 120
        private const val DEFAULT_ICON_SIZE = ICON_SIZE_MIN
        private const val SYMBOLS_FOR_PHONE = "0123456789 -.+()"
    }

    private val Int.px: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private var tilPhone: TextInputLayout? = null
    private var tilCode: TextInputLayout? = null
    private var etPhone: EditText? = null
    private var etCode: EditText? = null

    private var phone: String = EMPTY_STRING
    private var code: String = EMPTY_STRING
    private var countryAsset: CountryAsset? = null

    private var iconSize: Int
    private var flagImageVisibility: Boolean = true
    private var flagPath: String? = EMPTY_STRING

    private var maskedTextChangedListener: MaskedTextChangedListener? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        setAddStatesFromChildren(true)

        val typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.PhoneInputLayout)

        var iconSizeTemp = DEFAULT_ICON_SIZE.px

        try {
            typedArray?.run {

                iconSizeTemp = getDimensionPixelSize(R.styleable.PhoneInputLayout_pcv_icon_size, DEFAULT_ICON_SIZE.px)

                flagPath = getFlagPath(getInt(R.styleable.PhoneInputLayout_pcv_flag_shape, -1))
                        ?.let(context::getString)
                        ?: getString(R.styleable.PhoneInputLayout_pcv_custom_flag_shape)
            }
        } finally {
            typedArray.recycle()
        }

        iconSize = if (isInEditMode) (iconSizeTemp / COEFFICIENT_FOR_ICON_PREVIEW).toInt() else getIconSizeInLimits(iconSizeTemp)
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (child is TextInputLayout && (tilPhone == null || tilCode == null)) {
            if (tilCode == null) {
                super.addView(initCode(child), index, params)
            } else {
                super.addView(initPhone(child), index, params)
                setData(countryAsset)
            }
        } else {
            super.addView(child, index, params)
        }
    }

    private fun initCode(input: TextInputLayout) = input.apply {
        tilCode = this
        tilCode?.editText?.apply {
            etCode = this
            isFocusable = false
            inputType = InputType.TYPE_NULL
        }
    }

    private fun initPhone(input: TextInputLayout) = input.apply {
        tilPhone = this
        tilPhone?.editText?.apply {
            etPhone = this
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_CLASS_PHONE
            keyListener = DigitsKeyListener.getInstance(SYMBOLS_FOR_PHONE)
        }
    }

    override fun onSaveInstanceState(): Parcelable? = SavedState(countryAsset, super.onSaveInstanceState())

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            setData(state.countryAsset)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    fun getPhone() = code to phone

    fun setIconSize(size: Int) {
        iconSize = getIconSizeInLimits(size)
        setCountryIcon(code, flagPath)
    }

    fun flagImageVisibility(visible: Boolean) {
        flagImageVisibility = visible
        if (flagImageVisibility) {
            setCountryIcon(code, flagPath)
        } else {
            etCode?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
        }
    }

    fun setData(countryAsset: CountryAsset?) {
        (countryAsset ?: getDefaultCountryAsset(context, isInEditMode)).let {
            this.countryAsset = it
            setPhoneMask(it)
            setCountryCode(it.dialCode.toString())
            setCountryIcon(it.ab, flagPath)
        }
    }

    private fun setPhoneMask(countryAsset: CountryAsset) {
        val phoneFormat = takeUnless { isInEditMode }?.let {
            PhoneFormatUtils.formatPhone(countryAsset.ab, true)
        } ?: countryAsset.phoneFormat
        setupPhoneEditText(PhoneMaskUtils.generatePhoneMask(phoneFormat, true))
        etPhone?.setText(phone)
    }

    private fun setCountryCode(dialCode: String) {
        val text = PHONE_PREF + dialCode
        etCode?.setText(text)
    }

    private fun setCountryIcon(countryCode: String, flagPath: String?) {
        if (flagImageVisibility) {
            code = countryCode
            val countryIcon = getCountryFlagByCode(context, code)
            val drawable = BitmapUtils.resizeIcon(countryIcon, iconSize, resources, flagPath)

            //TODO material-components-android version 1.1.0-alpha04 use endIconDrawable
            etCode?.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, drawable, null)
        }
    }

    private fun getIconSizeInLimits(size: Int) = when {
        size > ICON_SIZE_MAX.px -> ICON_SIZE_MAX.px
        size < ICON_SIZE_MIN.px -> ICON_SIZE_MIN.px
        else -> size
    }

    private fun setupPhoneEditText(phoneMask: String) {
        etPhone?.removeTextChangedListener(maskedTextChangedListener)

        if (!isInEditMode) {
            etPhone?.run {
                maskedTextChangedListener = MaskedTextChangedListener(phoneMask,
                        false,
                        this,
                        null,
                        object : MaskedTextChangedListener.ValueListener {
                            override fun onTextChanged(maskFilled: Boolean, extractedValue: String) {
                                phone = extractedValue
                            }
                        })
                addTextChangedListener(maskedTextChangedListener)
                onFocusChangeListener = maskedTextChangedListener
            }
        }
        val hint = if (isInEditMode) DEFAULT_COUNTRY_MASK else maskedTextChangedListener?.placeholder()
        if (tilPhone?.isHintEnabled == true) tilPhone?.hint = hint else etPhone?.hint = hint
    }

    private class SavedState : BaseSavedState {
        val countryAsset: CountryAsset?

        constructor(countryAsset: CountryAsset?, source: Parcel?) : super(source) {
            this.countryAsset = countryAsset
        }

        constructor(countryAsset: CountryAsset?, superState: Parcelable?) : super(superState) {
            this.countryAsset = countryAsset
        }

        companion object {
            @JvmField
            val CREATOR = KParcelable.generateCreator {
                SavedState(it.read(), it)
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.write(countryAsset)
            super.writeToParcel(dest, flags)
        }

    }

}