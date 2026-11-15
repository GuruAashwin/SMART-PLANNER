package com.aashwin.smartplanner.data

import org.json.JSONArray
import org.json.JSONObject

/** Plain JSON export/import of everything, so data can be saved to a file and restored later. */
object Backup {
    class Data(
        val tasks: List<TaskEntity>,
        val subtasks: List<SubtaskEntity>,
        val reminders: List<ReminderEntity>,
        val categories: List<CategoryEntity>,
        val habits: List<HabitEntity>,
        val habitLogs: List<HabitLogEntity>,
    )

    fun toJson(
        tasks: List<TaskEntity>, subtasks: List<SubtaskEntity>, reminders: List<ReminderEntity>,
        categories: List<CategoryEntity>, habits: List<HabitEntity>, logs: List<HabitLogEntity>,
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("tasks", JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("title", t.title); put("description", t.description)
                    put("categoryId", t.categoryId ?: JSONObject.NULL); put("priority", t.priority)
                    put("dueDate", t.dueDate ?: JSONObject.NULL); put("dueMinutes", t.dueMinutes ?: JSONObject.NULL)
                    put("status", t.status); put("completedAt", t.completedAt ?: JSONObject.NULL)
                    put("createdAt", t.createdAt); put("alarmEnabled", t.alarmEnabled)
                    put("recType", t.recType); put("recInterval", t.recInterval); put("recDays", t.recDays)
                    put("postponeCount", t.postponeCount)
                })
            }
        })
        root.put("subtasks", JSONArray().apply {
            subtasks.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id); put("taskId", s.taskId); put("title", s.title)
                    put("done", s.done); put("position", s.position)
                })
            }
        })
        root.put("reminders", JSONArray().apply {
            reminders.forEach { r ->
                put(JSONObject().apply { put("id", r.id); put("taskId", r.taskId); put("offsetMinutes", r.offsetMinutes) })
            }
        })
        root.put("categories", JSONArray().apply {
            categories.forEach { c -> put(JSONObject().apply { put("id", c.id); put("name", c.name) }) }
        })
        root.put("habits", JSONArray().apply {
            habits.forEach { h -> put(JSONObject().apply { put("id", h.id); put("name", h.name); put("createdAt", h.createdAt) }) }
        })
        root.put("habitLogs", JSONArray().apply {
            logs.forEach { l -> put(JSONObject().apply { put("habitId", l.habitId); put("day", l.day) }) }
        })
        return root.toString(2)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else getInt(key)

    private inline fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).map { f(getJSONObject(it)) }
    }

    fun fromJson(json: String): Data {
        val root = JSONObject(json)
        return Data(
            tasks = root.optJSONArray("tasks").mapObjects { o ->
                TaskEntity(
                    id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"),
                    categoryId = o.optLongOrNull("categoryId"), priority = o.optInt("priority", Priority.MEDIUM),
                    dueDate = o.optLongOrNull("dueDate"), dueMinutes = o.optIntOrNull("dueMinutes"),
                    status = o.optInt("status"), completedAt = o.optLongOrNull("completedAt"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    alarmEnabled = o.optBoolean("alarmEnabled"), recType = o.optInt("recType"),
                    recInterval = o.optInt("recInterval", 1), recDays = o.optInt("recDays"),
                    postponeCount = o.optInt("postponeCount"),
                )
            },
            subtasks = root.optJSONArray("subtasks").mapObjects { o ->
                SubtaskEntity(o.getLong("id"), o.getLong("taskId"), o.getString("title"), o.optBoolean("done"), o.optInt("position"))
            },
            reminders = root.optJSONArray("reminders").mapObjects { o ->
                ReminderEntity(o.getLong("id"), o.getLong("taskId"), o.getInt("offsetMinutes"))
            },
            categories = root.optJSONArray("categories").mapObjects { o -> CategoryEntity(o.getLong("id"), o.getString("name")) },
            habits = root.optJSONArray("habits").mapObjects { o ->
                HabitEntity(o.getLong("id"), o.getString("name"), o.optLong("createdAt", System.currentTimeMillis()))
            },
            habitLogs = root.optJSONArray("habitLogs").mapObjects { o -> HabitLogEntity(o.getLong("habitId"), o.getLong("day")) },
        )
    }
}
