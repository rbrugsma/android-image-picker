package com.rickb.imagepicker.model

import android.os.Parcel
import android.os.Parcelable

data class ImageQuality(
        val desiredWidth: Int,
        val desiredHeight: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readInt(),
            parcel.readInt())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(desiredWidth)
        parcel.writeInt(desiredHeight)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ImageQuality> {
        override fun createFromParcel(parcel: Parcel): ImageQuality {
            return ImageQuality(parcel)
        }

        override fun newArray(size: Int): Array<ImageQuality?> {
            return arrayOfNulls(size)
        }
    }
}