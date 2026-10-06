package com.motocrashguardian.core.model

enum class DeviceState {
    BOOT,
    DISARMED,
    ARMED,
    POST_TRIGGER,
    COOLDOWN,
    CALIBRATING,
    FAULT
}

enum class DeviceEventType {
    IMPACT,
    TILT,
    TEST,
    FAULT
}

enum class IncidentType {
    REAL,
    DRILL
}

enum class TriggerType {
    IMPACT,
    SEVERE_IMPACT,
    TILT,
    TEST,
    CONNECTION_LOST
}

enum class IncidentStatus {
    ACTIVE,
    NOT_CONFIRMED,
    CANCELLED_BY_USER,
    DISPATCHED,
    DISPATCH_PARTIAL,
    DISPATCH_FAILED,
    STALE_EVENT
}

enum class LocationSource {
    PHONE_GPS,
    DEVICE_GPS,
    LAST_KNOWN,
    NONE
}

enum class ContactRole {
    PRIMARY,
    SECONDARY
}

enum class SmsStatus {
    SENT,
    DELIVERED,
    FAILED,
    NOT_ATTEMPTED
}

enum class CallStatus {
    PLACED,
    FAILED,
    NOT_ATTEMPTED,
    SKIPPED_DRILL
}

enum class SyncState {
    PENDING,
    SYNCED,
    FAILED_PERMANENT
}
