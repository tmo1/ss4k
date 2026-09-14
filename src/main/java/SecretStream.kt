/*
 * SecretStream for Kotlin (SS4K): A simple, high level API for
 * streaming authenticated encryption
 *
 * Copyright (c) 2026 Thomas More
 *
 * This file is part of SS4K.
 *
 * SS4K is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SS4K is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SS4K.  If not, see <https://www.gnu.org/licenses/>.
 */


package com.github.tmo1.ss4k

import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.exceptions.SodiumException
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretStream
import com.sun.jna.NativeLong
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.min

const val DEFAULT_CHUNK_SIZE = 65536

/**
 * A wrapper around an OutputStream that provides transparent authenticated encryption via
 * the [libsodium SecretStream API](https://doc.libsodium.org/doc/secret-key_cryptography/secretstream)
 *
 * @property[lazySodium] initialized LazySodium object
 * @property[outputStream] OutputStream to wrap
 * @property[key] 32 byte encryption key
 * @property[chunkSize] chunk size to use, defaults to [DEFAULT_CHUNK_SIZE]
 *
 */
class SecretStreamOutputStream(
    val lazySodium: LazySodium,
    val outputStream: OutputStream,
    val key: ByteArray,
    val chunkSize: Int = DEFAULT_CHUNK_SIZE
) : FilterOutputStream(outputStream) {

    private val st = SecretStream.State()
    private val header = ByteArray(SecretStream.HEADERBYTES)

    init {
        require(key.size == SecretStream.KEYBYTES && chunkSize > 0)
        outputStream.write(ByteBuffer.allocate(4).putInt(chunkSize).array())
        lazySodium.cryptoSecretStreamInitPush(st, header, key)
        outputStream.write(header)
    }

    private val plaintextChunk = ByteArray(chunkSize)
    private var plaintextChunkPosition = 0
    private val ciphertextChunk = ByteArray(chunkSize + SecretStream.ABYTES)

    override fun write(b: Int) {
        if (plaintextChunkPosition == chunkSize) writeNonFinalChunk()
        plaintextChunk[plaintextChunkPosition++] = (b and 0xFF).toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        require(off >= 0 && len >= 0 && off + len <= b.size)
        var bPosition = off
        val offPlusLen = off + len
        while (bPosition < offPlusLen) {
            if (plaintextChunkPosition == chunkSize) writeNonFinalChunk()
            val bytesToWrite = min(chunkSize - plaintextChunkPosition, offPlusLen - bPosition)
            b.copyInto(
                plaintextChunk, plaintextChunkPosition, bPosition, bPosition + bytesToWrite
            )
            plaintextChunkPosition += bytesToWrite
            bPosition += bytesToWrite
        }
    }

    private fun writeNonFinalChunk() {
        //Log.d(LOG_TAG, "Writing a chunk")
        lazySodium.cryptoSecretStreamPush(
            st, ciphertextChunk, null, plaintextChunk, chunkSize, null, 0, SecretStream.TAG_MESSAGE
        )
        outputStream.write(ciphertextChunk)
        plaintextChunkPosition = 0
    }

    override fun close() {
        //Log.d(LOG_TAG, "Writing final chunk")
        lazySodium.cryptoSecretStreamPush(
            st, ciphertextChunk, null, plaintextChunk, plaintextChunkPosition, null, 0, SecretStream.TAG_FINAL
        )
        outputStream.write(ciphertextChunk, 0, plaintextChunkPosition + SecretStream.ABYTES)
        super.close()
    }
}

/**
 * A wrapper around an InputStream that provides transparent authenticated decryption via
 * the [libsodium SecretStream API](https://doc.libsodium.org/doc/secret-key_cryptography/secretstream)
 *
 * @property[lazySodium] initialized LazySodium object
 * @property[inputStream] InputStream to wrap
 * @property[key] 32 byte decryption key
 *
 * @throws[SodiumException] if SecretStream header is incomplete
 */
class SecretStreamInputStream(
    val lazySodium: LazySodium, val inputStream: InputStream, val key: ByteArray
) : FilterInputStream(inputStream) {

    private val st = SecretStream.State()
    private val header = ByteArray(SecretStream.HEADERBYTES)
    private var chunkSize = 0
    private var chunkNumber = 1

    init {
        require(key.size == SecretStream.KEYBYTES)
        val byteArray = ByteArray(4)
        inputStream.read(byteArray)
        chunkSize = ByteBuffer.wrap(byteArray).getInt()
        if (chunkSize < 0) throw IllegalArgumentException("Negative chunk size in file header - file may be invalid or corrupted")
        inputStream.read(header, 0, SecretStream.HEADERBYTES)
        if (!lazySodium.cryptoSecretStreamInitPull(
                st, header, key
            )
        ) throw SodiumException("Incomplete SecretStream header")
    }

    private val plaintextChunk = ByteArray(chunkSize)
    private var plaintextChunkPosition = 0
    private var bytesInPlaintextChunk = 0
    private var finalChunkRead = false

    /** Read a single byte
     *
     * @throws[SodiumException] if EOF is reached before the end of the encrypted stream or vice versa
     */
    override fun read(): Int {
        if (plaintextChunkPosition == bytesInPlaintextChunk) readNextChunk()
        if (plaintextChunkPosition == -1) return -1
        return plaintextChunk[plaintextChunkPosition++].toInt()
    }

    /** Read multiple bytes
     *
     * @throws[SodiumException] if EOF is reached before the end of the encrypted stream or vice versa
     */
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        require(off >= 0 && len >= 0 && off + len <= b.size)
        if (len == 0) return 0
        var bPosition = off
        val offPlusLen = off + len
        while (bPosition < offPlusLen) {
            if (plaintextChunkPosition == bytesInPlaintextChunk) readNextChunk()
            if (plaintextChunkPosition == -1) return if (bPosition > off) bPosition - off else -1
            val bytesToRead = min(bytesInPlaintextChunk - plaintextChunkPosition, offPlusLen - bPosition)
            plaintextChunk.copyInto(
                b, bPosition, plaintextChunkPosition, plaintextChunkPosition + bytesToRead
            )
            plaintextChunkPosition += bytesToRead
            bPosition += bytesToRead
        }
        return bPosition - off
    }

    private fun readNextChunk() {
        val tag = ByteArray(1)
        val ciphertextChunk = ByteArray(chunkSize + SecretStream.ABYTES)
        //println("Reading a chunk")
        val bytesRead = inputStream.read(ciphertextChunk, 0, ciphertextChunk.size)
        if (bytesRead == -1) {
            if (!finalChunkRead) throw SodiumException("End of file reached before end of stream")
            plaintextChunkPosition = -1
            return
        }
        if (finalChunkRead) throw SodiumException("End of stream reached before end of file")
        if (!lazySodium.cryptoSecretStreamPull(
                st, plaintextChunk, null, tag, ciphertextChunk, bytesRead, null, 0
            )
        ) throw SodiumException("Decryption failure on chunk $chunkNumber")
        if (tag[0] == SecretStream.TAG_FINAL) finalChunkRead = true
        plaintextChunkPosition = 0
        bytesInPlaintextChunk = bytesRead - SecretStream.ABYTES
        chunkNumber++
    }
}

const val ENCRYPTION_KEY_LENGTH = 32

/** Derive a libsodium encryption key via the Argon2id algorithm from a passphrase, Argon2id parameters, and salt
 *
 * @param[lazySodium] initialized LazySodium object
 * @param[passphrase] passphrase
 * @param[tCostInIterations] time cost in iterations
 * @param[mCostInKibibytes] memory cost in kibibytes
 * @param[salt] salt
 *
 * @throws[SodiumException] if key derivation fails, most likely due to memory allocation failure
 */
fun passphraseToKey(
    lazySodium: LazySodium, passphrase: String, tCostInIterations: Int, mCostInKibibytes: Int, salt: ByteArray
): ByteArray {
    val key = ByteArray(ENCRYPTION_KEY_LENGTH)
    val passphraseByteArray = passphrase.toByteArray()
    if (!lazySodium.cryptoPwHash(
            key,
            ENCRYPTION_KEY_LENGTH,
            passphraseByteArray,
            passphraseByteArray.size,
            salt,
            tCostInIterations.toLong(),
            NativeLong(mCostInKibibytes.toLong()),
            PwHash.Alg.PWHASH_ALG_ARGON2ID13
        )
    ) throw SodiumException("Key derivation failure, probably due to memory allocation failure")
    return key
}