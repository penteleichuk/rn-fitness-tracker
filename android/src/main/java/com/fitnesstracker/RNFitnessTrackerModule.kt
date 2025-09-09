package com.fitnesstracker

import android.app.Activity
import com.facebook.react.bridge.*
import com.fitnesstracker.googlefit.DateHelper
import com.fitnesstracker.googlefit.GoogleFitManager
import com.fitnesstracker.permission.Permission
import com.fitnesstracker.permission.PermissionKind
import com.google.android.gms.fitness.FitnessOptions
import java.util.Date
import java.util.ArrayList

class RNFitnessTrackerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    private val googleFitManager: GoogleFitManager = GoogleFitManager(reactContext)

    override fun getName(): String = "RNFitnessTracker"

    private fun requireActivity(promise: Promise): Activity? {
        val activity = reactApplicationContext.currentActivity
        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, ACTIVITY_DOES_NOT_EXIST_MESSAGE)
            return null
        }
        return activity
    }

    @ReactMethod
    fun authorize(
        readPermissions: ReadableArray,
        writePermission: ReadableArray,
        promise: Promise
    ) {
        val permissions: ArrayList<Permission> =
            createPermissionsFromReactArray(readPermissions, writePermission, promise)

        if (googleFitManager.isTrackingAvailable(permissions)) {
            promise.resolve(true)
            return
        }

        val activity = requireActivity(promise) ?: return
        googleFitManager.authorize(promise, activity, permissions)
    }

    @ReactMethod
    fun isTrackingAvailable(
        readPermissions: ReadableArray,
        writePermission: ReadableArray,
        promise: Promise
    ) {
        val permissions: ArrayList<Permission> =
            createPermissionsFromReactArray(readPermissions, writePermission, promise)

        val hasPermissions = googleFitManager.isTrackingAvailable(permissions)

        if (hasPermissions && !googleFitManager.isAuthorized()) {
            val activity = requireActivity(promise) ?: return
            googleFitManager.authorize(promise, activity, permissions)
        }

        promise.resolve(hasPermissions)
    }

    @ReactMethod
    fun queryTotal(dataType: String, startDate: Double, endDate: Double, promise: Promise) {
        try {
            val endTime: Long = endDate.toLong()
            val startTime: Long = startDate.toLong()
            val permission = Permission(PermissionKind.getByValue(dataType))

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getHistoryClient()
                .queryTotal(promise, startTime, endTime, permission)
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun queryDailyTotals(dataType: String, startDate: Double, endDate: Double, promise: Promise) {
        try {
            val endTime: Long = endDate.toLong()
            val startTime: Long = startDate.toLong()
            val permission = Permission(PermissionKind.getByValue(dataType))

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getHistoryClient()
                .queryDailyTotals(
                    promise,
                    Date(startTime),
                    Date(endTime),
                    permission,
                    Arguments.createMap()
                )
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getStatisticWeekDaily(dataType: String, promise: Promise) {
        try {
            val permission = Permission(PermissionKind.getByValue(dataType))

            val endDate = Date()
            val startDate = DateHelper.addDays(endDate, -7)

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getHistoryClient()
                .queryDailyTotals(
                    promise,
                    startDate,
                    endDate,
                    permission,
                    Arguments.createMap()
                )
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getStatisticWeekTotal(dataType: String, promise: Promise) {
        try {
            val permission = Permission(PermissionKind.getByValue(dataType))

            val endDate = Date()
            val startDate = DateHelper.addDays(endDate, -7)

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getHistoryClient()
                .queryTotal(promise, startDate.time, endDate.time, permission)
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getStatisticTodayTotal(dataType: String, promise: Promise) {
        try {
            val permission = Permission(PermissionKind.getByValue(dataType))

            val endDate = Date()
            val startDate = DateHelper.getStartOfDay(endDate)

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getHistoryClient()
                .queryTotal(promise, startDate.time, endDate.time, permission)
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun getLatestDataRecord(dataType: String, promise: Promise) {
        try {
            val permission = Permission(PermissionKind.getByValue(dataType))

            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager.getHistoryClient().getLatestDataRecord(promise, permission)
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun writeWorkout(startTime: Double, endTime: Double, options: ReadableMap, promise: Promise) {
        try {
            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getActivityHistory()
                .writeWorkout(promise, startTime.toLong(), endTime.toLong(), options)
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    @ReactMethod
    fun deleteWorkouts(startTime: Double, endTime: Double, promise: Promise) {
        try {
            if (!googleFitManager.isAuthorized()) {
                promise.reject(Throwable(UNAUTHORIZED_GOOGLE_FIT))
                return
            }

            googleFitManager
                .getActivityHistory()
                .deleteWorkouts(promise, startTime.toLong(), endTime.toLong())
        } catch (e: Throwable) {
            promise.reject(e)
        }
    }

    private fun createPermissionsFromReactArray(
        readPermissions: ReadableArray,
        writePermissions: ReadableArray,
        promise: Promise
    ): ArrayList<Permission> {
        val result = ArrayList<Permission>()

        val readSize = readPermissions.size()
        for (i in 0 until readSize) {
            try {
                val permissionKind = readPermissions.getString(i)
                if (permissionKind == null) {
                    promise.reject(Throwable("readPermissions[$i] is null"))
                    continue
                }
                result.add(
                    Permission(
                        PermissionKind.getByValue(permissionKind),
                        FitnessOptions.ACCESS_READ
                    )
                )
            } catch (e: Throwable) {
                promise.reject(e)
            }
        }

        val writeSize = writePermissions.size()
        for (i in 0 until writeSize) {
            try {
                val permissionKind = writePermissions.getString(i)
                if (permissionKind == null) {
                    promise.reject(Throwable("writePermissions[$i] is null"))
                    continue
                }
                result.add(
                    Permission(
                        PermissionKind.getByValue(permissionKind),
                        FitnessOptions.ACCESS_WRITE
                    )
                )
            } catch (e: Throwable) {
                promise.reject(e)
            }
        }

        return result
    }

    companion object {
        const val E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST"
        const val ACTIVITY_DOES_NOT_EXIST_MESSAGE = "currentActivity returned null"
        const val UNAUTHORIZED_GOOGLE_FIT =
            "Unauthorized GoogleFit. You must first run authorize method or isTrackingAvailable method."
    }
}
