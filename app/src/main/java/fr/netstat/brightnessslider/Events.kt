package fr.netstat.brightnessslider

data class SettingsUpdatedEvent(val sender: Int?)

enum class AccessibilityStatusType { BOUND, UNBOUND }
data class AccessibilityStatusChangedEvent(val newValue: AccessibilityStatusType)
