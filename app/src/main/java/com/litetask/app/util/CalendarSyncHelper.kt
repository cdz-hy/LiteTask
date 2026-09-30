package com.litetask.app.util

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.litetask.app.data.local.PreferenceManager
import com.litetask.app.data.model.TaskDetailComposite
import com.litetask.app.data.model.sortedByOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarSyncPrefsState(
    val targetCalendarId: Long = PreferenceManager.DEFAULT_CALENDAR_TARGET_ID,
    val targetCalendarName: String = "",
    val autoPushEnabled: Boolean = false,
    val pushIncludeDone: Boolean = false,
    val pullDaysAhead: Int = PreferenceManager.DEFAULT_CALENDAR_PULL_DAYS,
    val syncReminders: Boolean = true,
    val icsExportIncludeDone: Boolean = true,
    val scheduleAdviceUseCalendar: Boolean = true,
    val lastPushAt: Long = 0L,
    val lastPullAt: Long = 0L,
    val writableCalendars: List<WritableCalendarOption> = emptyList(),
    val hasCalendarPermission: Boolean = false
)

data class WritableCalendarOption(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val isPrimary: Boolean,
    val isLocalLiteTask: Boolean
)

data class CalendarPushResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val calendarName: String
)

data class ExternalCalendarEvent(
    val eventId: Long,
    val title: String,
    val description: String?,
    val startTime: Long,
    val deadline: Long,
    val location: String?,
    val reminderMinutes: List<Int>
)

/**
 * 系统日历双向同步助手 (基于 Android CalendarContract)。
 */
object CalendarSyncHelper {

    private const val APP_URI_PREFIX = "litetask://task/"
    private const val LOCAL_CALENDAR_NAME = "LiteTask"
    private const val LOCAL_ACCOUNT_NAME = "LiteTask Local"

    private enum class UpsertOutcome { INSERTED, UPDATED, SKIPPED }

    fun hasCalendarPermissions(context: Context): Boolean {
        val readGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun queryWritableCalendars(context: Context): List<WritableCalendarOption> {
        if (!hasCalendarPermissions(context)) return emptyList()
        return queryWritableCalendarsInternal(context.contentResolver)
    }

    fun loadPrefsState(
        context: Context,
        preferenceManager: PreferenceManager
    ): CalendarSyncPrefsState {
        val hasPerm = hasCalendarPermissions(context)
        val calendars = if (hasPerm) queryWritableCalendarsInternal(context.contentResolver) else emptyList()
        val savedId = preferenceManager.getCalendarTargetId()
        val resolvedName = if (savedId > 0L) {
            calendars.firstOrNull { it.id == savedId }?.displayName
                ?: preferenceManager.getCalendarTargetName()
        } else {
            ""
        }
        return CalendarSyncPrefsState(
            targetCalendarId = savedId,
            targetCalendarName = resolvedName,
            autoPushEnabled = preferenceManager.isCalendarAutoPushEnabled(),
            pushIncludeDone = preferenceManager.isCalendarPushIncludeDone(),
            pullDaysAhead = preferenceManager.getCalendarPullDaysAhead(),
            syncReminders = preferenceManager.isCalendarSyncReminders(),
            icsExportIncludeDone = preferenceManager.isIcsExportIncludeDone(),
            scheduleAdviceUseCalendar = preferenceManager.isScheduleAdviceUseCalendarEnabled(),
            lastPushAt = preferenceManager.getCalendarLastPushAt(),
            lastPullAt = preferenceManager.getCalendarLastPullAt(),
            writableCalendars = calendars,
            hasCalendarPermission = hasPerm
        )
    }

    fun pushTasksToCalendar(
        context: Context,
        tasksWithDetails: List<TaskDetailComposite>,
        targetCalendarId: Long = -1L,
        includeFinished: Boolean = false,
        includeReminders: Boolean = true
    ): Result<CalendarPushResult> {
        if (!hasCalendarPermissions(context)) {
            return Result.failure(SecurityException("缺少系统日历读写权限"))
        }

        val resolver = context.contentResolver
        val targetCalendar = resolveTargetCalendar(resolver, targetCalendarId)
            ?: return Result.failure(IllegalStateException("未找到可用的系统日历账户"))

        val (calendarId, calendarName) = targetCalendar
        val existingMap = queryExistingLiteTaskEvents(resolver, context.packageName)
        val timezoneId = TimeZone.getDefault().id

        var inserted = 0
        var updated = 0
        var skipped = 0

        for (detail in tasksWithDetails) {
            val outcome = upsertTaskEvent(
                resolver = resolver,
                packageName = context.packageName,
                calendarId = calendarId,
                timezoneId = timezoneId,
                detail = detail,
                existingEventId = existingMap[detail.task.id],
                includeFinished = includeFinished,
                includeReminders = includeReminders
            )
            when (outcome) {
                UpsertOutcome.INSERTED -> inserted++
                UpsertOutcome.UPDATED -> updated++
                UpsertOutcome.SKIPPED -> skipped++
            }
        }

        return Result.success(
            CalendarPushResult(
                insertedCount = inserted,
                updatedCount = updated,
                skippedCount = skipped,
                calendarName = calendarName
            )
        )
    }

    fun syncSingleTask(
        context: Context,
        detail: TaskDetailComposite,
        targetCalendarId: Long = -1L,
        includeFinished: Boolean = false,
        includeReminders: Boolean = true
    ) {
        if (!hasCalendarPermissions(context)) return
        val resolver = context.contentResolver
        val (calendarId, _) = resolveTargetCalendar(resolver, targetCalendarId) ?: return
        val existingEventId = findExistingEventIdForTask(resolver, context.packageName, detail.task.id)
        upsertTaskEvent(
            resolver = resolver,
            packageName = context.packageName,
            calendarId = calendarId,
            timezoneId = TimeZone.getDefault().id,
            detail = detail,
            existingEventId = existingEventId,
            includeFinished = includeFinished,
            includeReminders = includeReminders
        )
    }

    fun deleteSyncedTaskEvent(context: Context, taskId: Long) {
        if (!hasCalendarPermissions(context)) return
        val resolver = context.contentResolver
        val existingEventId = findExistingEventIdForTask(resolver, context.packageName, taskId) ?: return
        val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
        resolver.delete(deleteUri, null, null)
    }

    fun pullUpcomingEventsFromCalendar(
        context: Context,
        daysAhead: Int = 30,
        includeReminders: Boolean = true
    ): Result<List<ExternalCalendarEvent>> {
        if (!hasCalendarPermissions(context)) {
            return Result.failure(SecurityException("缺少系统日历读取权限"))
        }

        val resolver = context.contentResolver
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val endRange = startOfToday + daysAhead.coerceAtLeast(1) * 24L * 60 * 60 * 1000

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CUSTOM_APP_PACKAGE,
            CalendarContract.Instances.CUSTOM_APP_URI
        )

        val rawEvents = mutableListOf<ExternalCalendarEvent>()
        val seenInstances = hashSetOf<Pair<Long, Long>>()

        val cursor = CalendarContract.Instances.query(resolver, projection, startOfToday, endRange)
        cursor?.use { c ->
            val idxId = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val idxTitle = c.getColumnIndex(CalendarContract.Instances.TITLE)
            val idxDesc = c.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
            val idxBegin = c.getColumnIndex(CalendarContract.Instances.BEGIN)
            val idxEnd = c.getColumnIndex(CalendarContract.Instances.END)
            val idxLoc = c.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val idxPkg = c.getColumnIndex(CalendarContract.Instances.CUSTOM_APP_PACKAGE)
            val idxUri = c.getColumnIndex(CalendarContract.Instances.CUSTOM_APP_URI)

            while (c.moveToNext()) {
                val eventId = if (idxId >= 0) c.getLong(idxId) else 0L
                val title = if (idxTitle >= 0) c.getString(idxTitle)?.trim().orEmpty() else ""
                if (title.isBlank()) continue

                val customPkg = if (idxPkg >= 0) c.getString(idxPkg) else null
                val customUri = if (idxUri >= 0) c.getString(idxUri) else null
                if (customPkg == context.packageName || customUri?.startsWith(APP_URI_PREFIX) == true) {
                    continue
                }

                val begin = if (idxBegin >= 0) c.getLong(idxBegin) else 0L
                val endRaw = if (idxEnd >= 0) c.getLong(idxEnd) else 0L
                if (begin <= 0L) continue
                if (!seenInstances.add(eventId to begin)) continue

                val deadline = if (endRaw > begin) endRaw else begin + 60L * 60 * 1000
                val desc = if (idxDesc >= 0) c.getString(idxDesc)?.takeIf { it.isNotBlank() } else null
                val location = if (idxLoc >= 0) c.getString(idxLoc)?.takeIf { it.isNotBlank() } else null
                val reminders = if (includeReminders) {
                    queryEventReminderMinutes(resolver, eventId)
                } else {
                    emptyList()
                }

                rawEvents.add(
                    ExternalCalendarEvent(
                        eventId = eventId,
                        title = title,
                        description = desc,
                        startTime = begin,
                        deadline = deadline,
                        location = location,
                        reminderMinutes = reminders
                    )
                )
            }
        }

        return Result.success(rawEvents)
    }

    private fun upsertTaskEvent(
        resolver: ContentResolver,
        packageName: String,
        calendarId: Long,
        timezoneId: String,
        detail: TaskDetailComposite,
        existingEventId: Long?,
        includeFinished: Boolean,
        includeReminders: Boolean
    ): UpsertOutcome {
        val task = detail.task
        val isFinished = task.isDone || task.isExpired

        // 未启用推送已完成/已过期任务且此前未推送过的，跳过写入
        if (isFinished && !includeFinished && existingEventId == null) {
            return UpsertOutcome.SKIPPED
        }

        val safeStart = task.startTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val safeEnd = maxOf(task.deadline, safeStart + 60_000L)
        val displayTitle = when {
            task.isDone -> "[已完成] ${task.title}"
            task.isExpired -> "[已过期] ${task.title}"
            else -> task.title
        }
        val description = buildString {
            detail.category?.name?.let { append("分类: $it\n") }
            if (!task.description.isNullOrBlank()) {
                append(task.description.trim()).append("\n")
            }
            if (detail.subTasks.isNotEmpty()) {
                append("\n子任务:\n")
                detail.subTasks.sortedByOrder().forEach { sub ->
                    val mark = if (sub.isCompleted) "[x]" else "[ ]"
                    append("- $mark ${sub.content}\n")
                }
            }
        }.trim()

        val reminderMinutes = if (includeReminders && !isFinished) {
            detail.reminders
                .filter { it.triggerAt > 0L && !it.isFired }
                .map { reminder ->
                    ((safeStart - reminder.triggerAt) / 60_000L).toInt().coerceAtLeast(0)
                }
                .distinct()
        } else {
            emptyList()
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, displayTitle)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, safeStart)
            put(CalendarContract.Events.DTEND, safeEnd)
            put(CalendarContract.Events.EVENT_TIMEZONE, timezoneId)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, "$APP_URI_PREFIX${task.id}")
            put(CalendarContract.Events.HAS_ALARM, if (reminderMinutes.isNotEmpty()) 1 else 0)
            put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
        }

        if (existingEventId != null) {
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, existingEventId)
            val rows = resolver.update(updateUri, values, null, null)
            if (rows > 0) {
                replaceEventReminders(resolver, existingEventId, reminderMinutes)
                return UpsertOutcome.UPDATED
            }
        }

        val newId = insertEventAndReminders(resolver, values, reminderMinutes)
        return if (newId != null) UpsertOutcome.INSERTED else UpsertOutcome.SKIPPED
    }

    private fun queryExistingLiteTaskEvents(
        resolver: ContentResolver,
        packageName: String
    ): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CUSTOM_APP_URI
        )
        val selection =
            "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? OR ${CalendarContract.Events.CUSTOM_APP_URI} LIKE ?) AND ${CalendarContract.Events.DELETED} = 0"
        val args = arrayOf(packageName, "$APP_URI_PREFIX%")

        resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, args, null)?.use { c ->
            val idxId = c.getColumnIndex(CalendarContract.Events._ID)
            val idxUri = c.getColumnIndex(CalendarContract.Events.CUSTOM_APP_URI)
            while (c.moveToNext()) {
                val eventId = if (idxId >= 0) c.getLong(idxId) else continue
                val uri = if (idxUri >= 0) c.getString(idxUri) else continue
                val taskId = uri?.removePrefix(APP_URI_PREFIX)?.toLongOrNull() ?: continue
                result[taskId] = eventId
            }
        }
        return result
    }

    private fun findExistingEventIdForTask(
        resolver: ContentResolver,
        packageName: String,
        taskId: Long
    ): Long? {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection =
            "(${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ? OR ${CalendarContract.Events.CUSTOM_APP_URI} = ?) AND ${CalendarContract.Events.CUSTOM_APP_URI} = ? AND ${CalendarContract.Events.DELETED} = 0"
        val taskUri = "$APP_URI_PREFIX$taskId"
        val args = arrayOf(packageName, taskUri, taskUri)

        resolver.query(CalendarContract.Events.CONTENT_URI, projection, selection, args, null)?.use { c ->
            val idxId = c.getColumnIndex(CalendarContract.Events._ID)
            if (c.moveToFirst() && idxId >= 0) {
                return c.getLong(idxId)
            }
        }
        return null
    }

    private fun resolveTargetCalendar(
        resolver: ContentResolver,
        preferredCalendarId: Long
    ): Pair<Long, String>? {
        val calendars = queryWritableCalendarsInternal(resolver)
        if (preferredCalendarId > 0L) {
            calendars.firstOrNull { it.id == preferredCalendarId }?.let {
                return it.id to it.displayName
            }
        }
        calendars.firstOrNull { it.isPrimary }?.let { return it.id to it.displayName }
        calendars.firstOrNull()?.let { return it.id to it.displayName }
        return createLocalLiteTaskCalendar(resolver)
    }

    private fun queryWritableCalendarsInternal(resolver: ContentResolver): List<WritableCalendarOption> {
        val list = mutableListOf<WritableCalendarOption>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val selectionArgs = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        resolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, selectionArgs, null)?.use { c ->
            val idxId = c.getColumnIndex(CalendarContract.Calendars._ID)
            val idxName = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val idxAccount = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val idxType = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            val idxPrimary = c.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

            while (c.moveToNext()) {
                val id = if (idxId >= 0) c.getLong(idxId) else continue
                val name = (if (idxName >= 0) c.getString(idxName) else null)?.takeIf { it.isNotBlank() } ?: "系统日历"
                val accountName = (if (idxAccount >= 0) c.getString(idxAccount) else null).orEmpty()
                val accountType = (if (idxType >= 0) c.getString(idxType) else null).orEmpty()
                val isPrimary = idxPrimary >= 0 && c.getInt(idxPrimary) == 1
                val isLocalLiteTask = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL &&
                    accountName == LOCAL_ACCOUNT_NAME

                list.add(
                    WritableCalendarOption(
                        id = id,
                        displayName = name,
                        accountName = accountName.ifBlank { "本地日历" },
                        isPrimary = isPrimary,
                        isLocalLiteTask = isLocalLiteTask
                    )
                )
            }
        }
        return list.sortedWith(
            compareByDescending<WritableCalendarOption> { it.isPrimary }
                .thenBy { it.isLocalLiteTask }
                .thenBy { it.displayName }
        )
    }

    private fun createLocalLiteTaskCalendar(resolver: ContentResolver): Pair<Long, String>? {
        val syncUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, LOCAL_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, LOCAL_CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF0078D4.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
        }

        return runCatching {
            val inserted = resolver.insert(syncUri, values) ?: return@runCatching null
            ContentUris.parseId(inserted) to LOCAL_CALENDAR_NAME
        }.getOrNull()
    }

    private fun insertEventAndReminders(
        resolver: ContentResolver,
        values: ContentValues,
        reminderMinutes: List<Int>
    ): Long? {
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(uri)
        replaceEventReminders(resolver, eventId, reminderMinutes)
        return eventId
    }

    private fun replaceEventReminders(
        resolver: ContentResolver,
        eventId: Long,
        reminderMinutes: List<Int>
    ) {
        resolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )
        reminderMinutes.forEach { minutes ->
            val remValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            resolver.insert(CalendarContract.Reminders.CONTENT_URI, remValues)
        }
    }

    private fun queryEventReminderMinutes(resolver: ContentResolver, eventId: Long): List<Int> {
        val result = mutableListOf<Int>()
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null
        )?.use { c ->
            val idxMin = c.getColumnIndex(CalendarContract.Reminders.MINUTES)
            while (c.moveToNext()) {
                if (idxMin >= 0) {
                    val mins = c.getInt(idxMin)
                    if (mins >= 0) result.add(mins)
                }
            }
        }
        return result.distinct()
    }
}

/**
 * 共享的系统日历与 .ics 同步配置响应式存储，确保设置页与数据管理页状态实时一致。
 */
@Singleton
class CalendarSyncPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {
    private val _state = MutableStateFlow(CalendarSyncHelper.loadPrefsState(context, preferenceManager))
    val state: StateFlow<CalendarSyncPrefsState> = _state.asStateFlow()

    fun refresh() {
        _state.value = CalendarSyncHelper.loadPrefsState(context, preferenceManager)
    }

    fun setTargetCalendar(id: Long, name: String) {
        preferenceManager.setCalendarTarget(id, name)
        refresh()
    }

    fun setAutoPushEnabled(enabled: Boolean) {
        preferenceManager.setCalendarAutoPushEnabled(enabled)
        refresh()
    }

    fun setPushIncludeDone(include: Boolean) {
        preferenceManager.setCalendarPushIncludeDone(include)
        refresh()
    }

    fun setPullDaysAhead(days: Int) {
        preferenceManager.setCalendarPullDaysAhead(days)
        refresh()
    }

    fun setSyncReminders(enabled: Boolean) {
        preferenceManager.setCalendarSyncReminders(enabled)
        refresh()
    }

    fun setIcsExportIncludeDone(include: Boolean) {
        preferenceManager.setIcsExportIncludeDone(include)
        refresh()
    }

    fun setScheduleAdviceUseCalendarEnabled(enabled: Boolean) {
        preferenceManager.setScheduleAdviceUseCalendarEnabled(enabled)
        refresh()
    }

    fun markPushCompleted(timestamp: Long = System.currentTimeMillis()) {
        preferenceManager.setCalendarLastPushAt(timestamp)
        refresh()
    }

    fun markPullCompleted(timestamp: Long = System.currentTimeMillis()) {
        preferenceManager.setCalendarLastPullAt(timestamp)
        refresh()
    }
}
