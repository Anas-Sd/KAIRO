package com.kairo.app.data.model

enum class TaskFilter(val displayName: String) {
    ALL("All"),
    TODAY("Today"),
    URGENT("Urgent"),
    WORK("Work"),
    FINANCE("Finance"),
    COMPLETED("Completed")
}

enum class TaskSort(val displayName: String) {
    DUE_DATE("Due Date"),
    PRIORITY("Priority"),
    TITLE("Alphabetical")
}
