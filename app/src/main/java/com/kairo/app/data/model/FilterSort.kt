package com.kairo.app.data.model

enum class StatusFilter(val label: String) {
    ACTIVE("Active"),
    COMPLETED("Completed")
}

enum class DateFilter(val label: String) {
    TODAY("Today"),
    TOMORROW("Tomorrow"),
    RANGE("Range")
}

data class FilterCriteria(
    val selectedStatuses: Set<StatusFilter> = emptySet(),
    val selectedPriorities: Set<Priority> = emptySet(),
    val selectedDate: DateFilter? = null,
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null
) {
    val isActive: Boolean
        get() = selectedStatuses.isNotEmpty() || selectedPriorities.isNotEmpty() || selectedDate != null
}

enum class TaskSort(val displayName: String, val shortName: String, val category: String) {
    DATE_NEAR("Earliest First", "Due Date (Near)", "Due Date"),
    DATE_LATE("Latest First", "Due Date (Late)", "Due Date"),
    PRIORITY_HIGH("Highest First", "High Priority", "Priority"),
    PRIORITY_LOW("Lowest First", "Low Priority", "Priority"),
    TITLE_AZ("A to Z", "Name (A-Z)", "Alphabetical"),
    TITLE_ZA("Z to A", "Name (Z-A)", "Alphabetical")
}
