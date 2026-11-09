package com.callalertbot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract

object Contacts {

    /** The saved contact name for [number], or null when there is none or the permission is missing. */
    fun name(context: Context, number: String): String? {
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
