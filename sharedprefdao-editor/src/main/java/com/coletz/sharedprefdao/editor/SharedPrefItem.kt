package com.coletz.sharedprefdao.editorimport android.os.Buildimport android.os.Parcelimport android.os.Parcelableclass SharedPrefItem : Parcelable {    val type: SharedPrefType    val key: String    val value: Any    constructor(type: SharedPrefType, key: String, value: Any) {        this.type = type        this.key = key        this.value = value    }    constructor(parcel: Parcel) {        type = SharedPrefType.values()[parcel.readInt()]        key = requireNotNull(parcel.readString())        value = Helper.valueFromParcel(type, parcel)    }    object Helper {        fun valueFromParcel(type: SharedPrefType, parcel: Parcel): Any {            val retVal: Any? = when (type) {                SharedPrefType.STRING -> parcel.readString()                SharedPrefType.INTEGER -> parcel.readInt()                SharedPrefType.BOOLEAN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { parcel.readBoolean() } else { parcel.readInt() != 0 }                SharedPrefType.FLOAT -> parcel.readFloat()                SharedPrefType.LONG -> parcel.readLong()                SharedPrefType.STRING_SET -> mutableListOf<String>().apply { parcel.readStringList(this) }.toMutableSet()            }            return requireNotNull(retVal)        }        fun fromPrefPair(pair: Pair<String, Any>): SharedPrefItem =            SharedPrefItem(                SharedPrefType.fromPrefValue(pair.second),                pair.first,                pair.second            )    }    override fun writeToParcel(parcel: Parcel, flags: Int) {        parcel.writeInt(type.ordinal)        parcel.writeString(key)        when (type) {            SharedPrefType.STRING -> parcel.writeString(value as String)            SharedPrefType.INTEGER -> parcel.writeInt(value as Int)            SharedPrefType.BOOLEAN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { parcel.writeBoolean(value as Boolean) } else { parcel.writeInt(if (value as Boolean) 1 else 0) }            SharedPrefType.FLOAT -> parcel.writeFloat(value as Float)            SharedPrefType.LONG -> parcel.writeLong(value as Long)            SharedPrefType.STRING_SET -> parcel.writeStringList((value as Set<*>).map { it as String })        }    }    override fun describeContents(): Int {        return 0    }    operator fun component1(): SharedPrefType = type    operator fun component2(): String = key    operator fun component3(): Any = value    companion object CREATOR : Parcelable.Creator<SharedPrefItem> {        override fun createFromParcel(parcel: Parcel): SharedPrefItem {            return SharedPrefItem(parcel)        }        override fun newArray(size: Int): Array<SharedPrefItem?> {            return arrayOfNulls(size)        }    }}