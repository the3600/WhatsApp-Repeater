package com.whatsapprepeater.core.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.whatsapprepeater.core.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ContactsRepository {
  suspend fun loadPhoneContacts(): List<ContactInfo>
}

class AndroidContactsRepository(
  private val context: Context,
) : ContactsRepository {

  override suspend fun loadPhoneContacts(): List<ContactInfo> = withContext(Dispatchers.IO) {
    val results = LinkedHashMap<String, ContactInfo>()
    val projection = arrayOf(
      ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
      ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
      ContactsContract.CommonDataKinds.Phone.NUMBER,
    )
    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"

    context.contentResolver.query(
      ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
      projection,
      null,
      null,
      sortOrder,
    )?.use { cursor ->
      val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
      val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
      val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

      while (cursor.moveToNext()) {
        val id = if (idIndex >= 0) cursor.getString(idIndex).orEmpty() else ""
        val name = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty().trim() else ""
        val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty().trim() else ""

        if (rawNumber.isBlank()) continue
        val digits = rawNumber.filter { it.isDigit() }
        if (digits.length < 6) continue

        val key = "${name.lowercase()}_$digits"
        if (!results.containsKey(key)) {
          results[key] = ContactInfo(
            id = id.ifBlank { key },
            name = name.ifBlank { rawNumber },
            phoneNumber = rawNumber,
          )
        }
      }
    }

    results.values.toList()
  }
}

