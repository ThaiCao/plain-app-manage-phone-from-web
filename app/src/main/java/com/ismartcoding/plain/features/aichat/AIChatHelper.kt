package com.ismartcoding.plain.features.aichat

import androidx.sqlite.db.SimpleSQLiteQuery
import com.ismartcoding.lib.content.ContentSort
import com.ismartcoding.lib.content.ContentWhere
import com.ismartcoding.lib.helpers.SearchHelper
import com.ismartcoding.plain.db.AIChatDao
import com.ismartcoding.plain.db.AppDatabase
import com.ismartcoding.plain.db.DAIChat
import com.ismartcoding.plain.web.models.AIChat

object AIChatHelper {
    private val chatDao: AIChatDao by lazy {
        AppDatabase.instance.aiChatDao()
    }

    fun getChats(id: String): List<DAIChat> {
        return chatDao.getChats(id)
    }

    suspend fun createChatItemsAsync(parentId: String, isMe: Boolean, message: String): List<DAIChat> {
        val item = DAIChat()
        item.isMe = isMe
        item.content = message
        item.parentId = parentId
        chatDao.insert(item)
        return listOf(item)
    }

    fun count(query: String): Int {
        var sql = "SELECT COUNT(id) FROM aichats"
        val where = ContentWhere()
        val sort = ContentSort("updated_at", "DESC")
        if (query.isNotEmpty()) {
            parseQuery(where, sort, query)
            sql += " WHERE ${where.toSelection()}"
        }

        return chatDao.count(SimpleSQLiteQuery(sql, where.args.toTypedArray()))
    }

    fun search(query: String, limit: Int, offset: Int): List<DAIChat> {
        var sql = "SELECT * FROM aichats"
        val where = ContentWhere()
        val sort = ContentSort("updated_at", "DESC")
        if (query.isNotEmpty()) {
            parseQuery(where, sort, query)
            sql += " WHERE ${where.toSelection()}"
        }

        sql += " ORDER BY ${sort.name} ${sort.direction} LIMIT $limit OFFSET $offset"

        return chatDao.search(SimpleSQLiteQuery(sql, where.args.toTypedArray()))
    }

    suspend fun getAsync(id: String): DAIChat? {
        return chatDao.getById(id)
    }

    suspend fun deleteAsync(ids: Set<String>) {
        chatDao.delete(ids)
    }

    suspend fun deleteByParentIdsAsync(parentIds: Set<String>) {
        chatDao.deleteByParentIds(parentIds)
    }

    private fun parseQuery(where: ContentWhere, sort: ContentSort, query: String) {
        val queryGroups = SearchHelper.parse(query)
        queryGroups.forEach {
            if (it.name == "text") {
                where.addLikes(listOf("content"), listOf(it.value, it.value))
            } else if (it.name == "parent_id") {
                where.add("parent_id=?", it.value)
            } else if (it.name == "ids") {
                val ids = it.value.split(",")
                if (ids.isNotEmpty()) {
                    where.addIn("id", ids)
                }
            } else if (it.name == "sort") {
                val split = it.value.split("-")
                sort.name = split[0]
                sort.direction = split[1]
            }
        }
    }
}