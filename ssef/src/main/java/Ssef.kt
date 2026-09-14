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

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.exceptions.SodiumException
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom

// Constants
const val PROGRAM_NAME = "ssef"
const val FILENAME_SUFFIX = ".ssef"
val MAGIC_NUMBER = "SSEF".toByteArray() + 0x00 + 0xFF.toByte()
val FORMAT_VERSION = byteArrayOf(0x00, 0x01)
const val INTEGER_LENGTH = 4

// Argon2id parameters
// These are the values of the "SECOND RECOMMENDED option" of RFC 9106, for situations where
// "much less memory is available."
// https://datatracker.ietf.org/doc/html/rfc9106#name-parameter-choice

const val T_COST_IN_ITERATIONS = 3
const val M_COST_IN_KIBIBYTES = 65536

// libsodium doesn't expose the Argon2 parallelism parameter:
// https://github.com/jedisct1/libsodium/issues/488
// https://github.com/jedisct1/libsodium/issues/986
// https://github.com/jedisct1/libsodium/issues/993
// https://github.com/jedisct1/libsodium/discussions/1092
//const val PARALLELISM = 4
const val SALT_LENGTH = 16

const val USAGE_MESSAGE = """
    "Usage: $PROGRAM_NAME [encrypt | decrypt] <input-file> [<output-file>]
    
    <output-file> may be omitted for decryption, in which case <output-file> will be set to <input-file> + '$FILENAME_SUFFIX',
     or for encryption if <input-file> ends with '$FILENAME_SUFFIX', in which case <output-file> will be set to <input-file>
     with '$FILENAME_SUFFIX' removed.
"""

fun main(args: Array<String>) {

    // Verify and parse arguments
    if (args.size !in 2..3 || args[0] !in listOf(
            "encrypt", "decrypt"
        ) || (args.size == 2 && args[0] == "decrypt" && !args[1].endsWith(FILENAME_SUFFIX))
    ) exitWithError(USAGE_MESSAGE)

    // Open <input-file> and <output-file>
    val inputStream: InputStream
    try {
        inputStream = File(args[1]).inputStream()
    } catch (_: FileNotFoundException) {
        exitWithError("File not found: '${args[1]}'")
    }
    val outputStream = File(
        if (args.size == 3) args[2] else if (args[0] == "encrypt") args[1] + FILENAME_SUFFIX else args[1].removeSuffix(
            FILENAME_SUFFIX
        )
    ).outputStream()

    // Initialize libsodium
    val lazySodium = LazySodiumJava(SodiumJava())

    // Declare Argon2id parameter variables
    val tCostInIterations: Int
    val mCostInKibibytes: Int
    val salt = ByteArray(SALT_LENGTH)

    // Write or read SSEF header
    if (args[0] == "encrypt") {
        tCostInIterations = T_COST_IN_ITERATIONS
        mCostInKibibytes = M_COST_IN_KIBIBYTES
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(salt)
        outputStream.write(
            MAGIC_NUMBER + FORMAT_VERSION + ByteBuffer.allocate(INTEGER_LENGTH).putInt(tCostInIterations)
                .array() + ByteBuffer.allocate(INTEGER_LENGTH).putInt(mCostInKibibytes).array() + salt
        )
    } else {
        val magicNumber = ByteArray(MAGIC_NUMBER.size)
        inputStream.read(magicNumber)
        if (!magicNumber.contentEquals(MAGIC_NUMBER)) exitWithError("File '${args[1]}' is not an SSEF encrypted file")
        inputStream.skip(FORMAT_VERSION.size.toLong()) // we don't currently do anything with the FORMAT_VERSION
        val byteArray = ByteArray(INTEGER_LENGTH)
        inputStream.read(byteArray)
        tCostInIterations = ByteBuffer.wrap(byteArray).getInt()
        inputStream.read(byteArray)
        mCostInKibibytes = ByteBuffer.wrap(byteArray).getInt()
        inputStream.read(salt)
    }

    // Get encryption key
    print("Enter passphrase: ")
    val passphrase = readln()
    val encryptionKey = passphraseToKey(lazySodium, passphrase, tCostInIterations, mCostInKibibytes, salt)

    // Copy all data from <input-file> to <output-file> with transparent encryption / decryption and authentication
    val (readFrom, writeTo) = if (args[0] == "encrypt") inputStream to SecretStreamOutputStream(
        lazySodium, outputStream, encryptionKey
    ) else SecretStreamInputStream(lazySodium, inputStream, encryptionKey) to outputStream
    readFrom.use { inputStream ->
        writeTo.use { outputStream ->
            try {
                inputStream.transferTo(outputStream)
            } catch (e: SodiumException) {
                exitWithError(e.message ?: "Unknown libsodium error")
            }
        }
    }
}

fun exitWithError(error: String): Nothing {
    System.err.println(error)
    kotlin.system.exitProcess(1)
}
