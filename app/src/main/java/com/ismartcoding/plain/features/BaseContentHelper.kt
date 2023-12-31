package com.ismartcoding.plain.features

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import com.ismartcoding.lib.content.ContentWhere
import com.ismartcoding.lib.data.SortBy
import com.ismartcoding.lib.extensions.getStringValue
import com.ismartcoding.lib.extensions.paging
import com.ismartcoding.lib.extensions.sort
import com.ismartcoding.lib.extensions.where
import com.ismartcoding.lib.helpers.StringHelper
import com.ismartcoding.lib.isQPlus
import com.ismartcoding.lib.isRPlus
import com.ismartcoding.lib.logcat.LogCat
import java.io.File

abstract class BaseContentHelper {
    abstract val uriExternal: Uri

    fun getItemUri(id: String): Uri {
        return Uri.withAppendedPath(uriExternal, id)
    }

    abstract fun getWhere(query: String): ContentWhere
    abstract fun getProjection(): Array<String>

    open fun count(context: Context, query: String): Int {
        val where = getWhere(query)
        var result = 0
        if (isQPlus()) {
            context.contentResolver.query(uriExternal, null, Bundle().apply {
                where(where.toSelection(), where.args)
            }, null)?.run {
                moveToFirst()
                result = count
                close()
            }
        } else {
            try {
                context.contentResolver.query(
                    uriExternal, arrayOf("count(*) AS count"),
                    where.toSelection(), where.args.toTypedArray(), null
                )?.run {
                    moveToFirst()
                    if (count > 0) {
                        result = getInt(0)
                    }
                    close()
                }
            } catch (ex: Exception) {
                // Fatal Exception: java.lang.IllegalArgumentException: Non-token detected in 'count(*) AS count'
                context.contentResolver.query(uriExternal, null, Bundle().apply {
                    where(where.toSelection(), where.args)
                }, null)?.run {
                    moveToFirst()
                    result = count
                    close()
                }
            }
        }

        return result
    }

    fun getSearchCursor(context: Context, query: String, limit: Int, offset: Int, sortBy: SortBy): Cursor? {
        return if (isRPlus()) {
            // From Android 11, LIMIT and OFFSET should be retrieved using Bundle
            getSearchCursorWithBundle(context, query, limit, offset, sortBy)
        } else {
            try {
                getSearchCursorWithSortOrder(context, query, limit, offset, sortBy)
            } catch (ex: Exception) {
                // Huawei OS android 10 may throw error `Invalid token LIMIT`
                getSearchCursorWithBundle(context, query, limit, offset, sortBy)
            }
        }
    }

    protected fun getSearchCursorWithBundle(context: Context, query: String, limit: Int, offset: Int, sortBy: SortBy): Cursor? {
        val where = getWhere(query)
        val sourceUri = uriExternal.buildUpon()
            .appendQueryParameter("limit", limit.toString())
            .appendQueryParameter("offset", offset.toString())
            .build()
        return context.contentResolver.query(sourceUri, getProjection(), Bundle().apply {
            paging(offset, limit)
            sort(sortBy)
            where(where.toSelection(), where.args)
        }, null)
    }

    protected fun getSearchCursorWithSortOrder(context: Context, query: String, limit: Int, offset: Int, sortBy: SortBy?): Cursor? {
        val where = getWhere(query)
        return context.contentResolver.query(
            uriExternal, getProjection(),
            where.toSelection(), where.args.toTypedArray(), if (sortBy != null) "${sortBy.field} ${sortBy.direction} LIMIT $offset, $limit" else "LIMIT $offset, $limit"
        )
    }

    open fun deleteByIds(context: Context, ids: Set<String>) {
        ids.chunked(30).forEach { chunk ->
            val selection = "${BaseColumns._ID} IN (${StringHelper.getQuestionMarks(chunk.size)})"
            val selectionArgs = chunk.map { it }.toTypedArray()
            context.contentResolver.delete(uriExternal, selection, selectionArgs)
        }
    }

    fun deleteAll(context: Context) {
        context.contentResolver.delete(uriExternal, null, null)
    }

    fun deleteRecordsAndFilesByIds(context: Context, ids: Set<String>):Set<String> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA)
        val where = ContentWhere()
        where.addIn(MediaStore.MediaColumns._ID, ids.toList())
        var deletedCount = 0
        val cursor = context.contentResolver.query(
            uriExternal, projection, where.toSelection(),
            where.args.toTypedArray(), null
        )
        val paths = mutableSetOf<String>()
        if (cursor != null) {
            cursor.moveToFirst()
            while (!cursor.isAfterLast) {
                val id = cursor.getStringValue(MediaStore.MediaColumns._ID)
                val path = cursor.getStringValue(MediaStore.MediaColumns.DATA)
                paths.add(path)
                try { // File.delete can throw a security exception
                    val f = File(path)
                    if (f.deleteRecursively()) {
                        context.contentResolver.delete(
                            getItemUri(id), null, null
                        )
                        deletedCount++
                    }
                    cursor.moveToNext()
                } catch (ex: Exception) {
                    cursor.moveToNext()
                    LogCat.e(ex.toString())
                }
            }
            cursor.close()
        }

        return paths
    }
}