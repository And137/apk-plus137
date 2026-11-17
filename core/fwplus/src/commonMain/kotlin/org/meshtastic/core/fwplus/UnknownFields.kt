/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.fwplus

import co.touchlab.kermit.Logger
import com.squareup.wire.FieldEncoding
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import okio.Buffer
import okio.ByteString
import okio.IOException

/**
 * Low-level access to fields that the upstream `org.meshtastic:protobufs` models do not declare.
 *
 * Wire keeps every field it does not know in a message's `unknownFields`, byte-for-byte, and writes them back when the
 * message is encoded. FW+ fields therefore survive a decode/encode round trip through the upstream models, and these
 * helpers read and replace them by field number.
 */
internal object UnknownFields {

    /** One raw field: its number, wire encoding and decoded raw value (Long for varint/fixed, ByteString otherwise). */
    private data class RawField(val tag: Int, val encoding: FieldEncoding, val value: Any)

    private fun parse(bytes: ByteString): List<RawField> {
        if (bytes.size == 0) return emptyList()
        val reader = ProtoReader(Buffer().write(bytes))
        val fields = mutableListOf<RawField>()
        val token = reader.beginMessage()
        var tag = reader.nextTag()
        while (tag != -1) {
            val encoding = checkNotNull(reader.peekFieldEncoding()) { "No encoding for field $tag" }
            encoding.rawProtoAdapter().decode(reader)?.let { fields += RawField(tag, encoding, it) }
            tag = reader.nextTag()
        }
        reader.endMessageAndGetUnknownFields(token)
        return fields
    }

    private fun encode(fields: List<RawField>): ByteString {
        if (fields.isEmpty()) return ByteString.EMPTY
        val buffer = Buffer()
        val writer = ProtoWriter(buffer)
        for (field in fields) {
            @Suppress("UNCHECKED_CAST")
            (field.encoding.rawProtoAdapter() as ProtoAdapter<Any>).encodeWithTag(writer, field.tag, field.value)
        }
        return buffer.readByteString()
    }

    // A malformed FW+ field must not break handling of the upstream message carrying it: log it and treat the
    // field as absent.
    private inline fun <T> safely(default: T, block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        Logger.w(e) { "Malformed FW+ field" }
        default
    } catch (e: IllegalStateException) {
        Logger.w(e) { "Malformed FW+ field" }
        default
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "Malformed FW+ field" }
        default
    }

    /**
     * Decodes every occurrence of the embedded-message field [tag] as one message. Protobuf merges repeated occurrences
     * of a message field, which is what decoding their concatenation does. Returns null when the field is absent or
     * does not parse.
     */
    fun <M : Any> readMessage(unknownFields: ByteString, tag: Int, adapter: ProtoAdapter<M>): M? = safely(null) {
        val chunks = parse(unknownFields).filter { it.tag == tag && it.encoding == FieldEncoding.LENGTH_DELIMITED }
        if (chunks.isEmpty()) {
            null
        } else {
            val merged = Buffer()
            chunks.forEach { merged.write(it.value as ByteString) }
            adapter.decode(merged.readByteString())
        }
    }

    /** Returns the last value of the varint field [tag] (proto3 "last one wins"), or null when absent. */
    fun readVarint(unknownFields: ByteString, tag: Int): Long? = safely(null) {
        parse(unknownFields).lastOrNull { it.tag == tag && it.encoding == FieldEncoding.VARINT }?.value as Long?
    }

    /** Returns [unknownFields] with every occurrence of [tag] removed and, when [value] is non-null, the new value. */
    fun <M : Any> writeMessage(unknownFields: ByteString, tag: Int, adapter: ProtoAdapter<M>, value: M?): ByteString {
        val kept = safely(emptyList()) { parse(unknownFields) }.filterNot { it.tag == tag }
        val added = value?.let { listOf(RawField(tag, FieldEncoding.LENGTH_DELIMITED, adapter.encodeByteString(it))) }
        return encode(kept + added.orEmpty())
    }

    /** Returns [unknownFields] with every occurrence of [tag] removed and, when [value] is non-null, the new varint. */
    fun writeVarint(unknownFields: ByteString, tag: Int, value: Long?): ByteString {
        val kept = safely(emptyList()) { parse(unknownFields) }.filterNot { it.tag == tag }
        val added = value?.let { listOf(RawField(tag, FieldEncoding.VARINT, it)) }
        return encode(kept + added.orEmpty())
    }
}
