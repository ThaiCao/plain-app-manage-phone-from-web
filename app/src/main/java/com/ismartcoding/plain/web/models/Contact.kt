package com.ismartcoding.plain.web.models

import com.ismartcoding.plain.features.contact.*
import com.ismartcoding.plain.helpers.FileHelper
import kotlinx.datetime.Instant

data class Contact(
    var id: ID,
    var prefix: String,
    var firstName: String,
    var middleName: String,
    var lastName: String,
    var suffix: String,
    var nickname: String,
    var photoId: String,
    var phoneNumbers: List<PhoneNumber>,
    var emails: List<ContentItem>,
    var addresses: List<ContentItem>,
    var events: List<ContentItem>,
    var source: String,
    var starred: Boolean,
    var contactId: ID,
    var thumbnailId: String,
    var notes: String,
    var groups: List<ContactGroup>,
    var organization: Organization?,
    var websites: List<ContentItem>,
    var ims: List<ContentItem>,
    var ringtone: String,
    var updatedAt: Instant,
)

fun DContact.toModel(): Contact {
    return Contact(
        ID(id.toString()), prefix, givenName, middleName, familyName, suffix,
        nickname, FileHelper.getFileId(photoUri), phoneNumbers, emails, addresses, events, source,
        starred == 1, ID(contactId.toString()), FileHelper.getFileId(thumbnailUri),
        notes, groups.map { it.toModel() }, organization, websites, ims, ringtone, updatedAt
    )
}