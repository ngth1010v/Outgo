package app.outgo.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    dateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatDayHeader(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy").format(Instant.ofEpochMilli(epochMillis).atZone(zone))
