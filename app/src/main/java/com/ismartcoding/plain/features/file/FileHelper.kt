package com.ismartcoding.plain.features.file

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ismartcoding.lib.content.ContentWhere
import com.ismartcoding.lib.extensions.*
import com.ismartcoding.lib.helpers.SearchHelper
import com.ismartcoding.plain.features.BaseContentHelper

object FileHelper : BaseContentHelper() {
    override val uriExternal: Uri = MediaStore.Files.getContentUri("external")

    override fun getProjection(): Array<String> {
        return arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.TITLE,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
    }

    override fun getWhere(query: String): ContentWhere {
        val where = ContentWhere()
        if (query.isNotEmpty()) {
            val queryGroups = SearchHelper.parse(query)
            queryGroups.forEach {
                if (it.name == "text") {
                    where.add("${MediaStore.Files.FileColumns.TITLE} LIKE ?", "%${it.value}%")
                } else if (it.name == "ids") {
                    val ids = it.value.split(",")
                    if (ids.isNotEmpty()) {
                        where.addIn(MediaStore.Files.FileColumns._ID, ids)
                    }
                }
            }
        }
        return where
    }

    fun search(context: Context, query: String, limit: Int, offset: Int, sortBy: FileSortBy): List<DFile> {
        val cursor = getSearchCursor(context, query, limit, offset, sortBy.toSortBy())
        val result = mutableListOf<DFile>()
        if (cursor?.moveToFirst() == true) {
            do {
                val id = cursor.getStringValue(MediaStore.Files.FileColumns._ID)
                val title = cursor.getStringValue(MediaStore.Files.FileColumns.TITLE)
                val size = cursor.getLongValue(MediaStore.Files.FileColumns.SIZE)
                val path = cursor.getStringValue(MediaStore.Files.FileColumns.DATA)
                val updatedAt = cursor.getTimeValue(MediaStore.Files.FileColumns.DATE_MODIFIED)
                result.add(DFile(title, path, "", updatedAt, size, size == 0L, 0))
            } while (cursor.moveToNext())
        }
        return result
    }
}