package de.wonderfulworld.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate
import java.time.YearMonth

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)
    @TypeConverter fun monthToString(m: YearMonth?): String? = m?.toString()
    @TypeConverter fun stringToMonth(v: String?): YearMonth? = v?.let(YearMonth::parse)
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor = PrimitiveSerialDescriptor("YearMonth", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: YearMonth) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): YearMonth = YearMonth.parse(decoder.decodeString())
}
