# SecretStream For Kotlin: A Simple, High Level API For Streaming Authenticated Encryption

SecretStream for Kotlin (SS4K) provides a simple, high level API for streaming [authenticated encryption](https://en.wikipedia.org/wiki/Authenticated_encryption) based on the [libsodium SecretStream API](https://doc.libsodium.org/doc/secret-key_cryptography/secretstream), via the `SecretStreamInputStream` and `SecretStreamOutputStream` classes that wrap `FilterInputStream` and `FilterOutputStream` respectively with logic that implements streaming authenticated encryption and decryption. Since these classes are subclasses of `InputStream` and `OutputStream`, they can be used anywhere their superclasses can, allowing the addition of transparent encryption and decryption / authentication to applications with minimal changes to the IO code.

For example, to add encryption to the following code:

```
val outputStream = File("/path/to/file").outputStream()

outputStream.use {
    it.write(someData)
    it.write(moreData)
    it.write(evenMoreData)
}
```

We can do:

```
val lazySodium = LazySodiumJava(SodiumJava())
val key = ByteArray(32)
lazySodium.cryptoSecretBoxKeygen(key)
val outputStream = SecretStreamOutputStream(lazySodium, File("/path/to/file").outputStream(), key)

outputStream.use {
    it.write(someData)
    it.write(moreData)
    it.write(evenMoreData)
}
```

(In a real application, the key would obviously have to be stored somewhere; key management is out of scope for SS4K.)

## Usage

### Gradle configuration

In your project's Gradle configurataion file, add `mavenCentral()` to the `repositories` section (if it isn't yet there), and add the following to the `dependencies` section:

```
implementation("cz.mormegil:lazysodium-java:6.2.0")
implementation("net.java.dev.jna:jna:5.19.1")
```

Use the latest versions available from Maven Central:
![Maven Central Version](https://img.shields.io/maven-central/v/cz.mormegil/lazysodium-java?label=lazysodium-java) ![Maven Central Version](https://img.shields.io/maven-central/v/net.java.dev.jna/jna?label=JNA)

### Libsodium / LazySodium initialization

In your Kotlin code, initialize the libsodium library and the LazySodium binding:

```
val lazySodium = LazySodiumJava(SodiumJava())
```

Or on Android:

```
val lazySodium = LazySodiumAndroid(SodiumAndroid())
```

See [the official LazySodium documentation](https://github.com/terl/lazysodium-java/wiki/Getting-started#part-2-usage) for more information.

### SS4K classes and methods

SS4K provides two classes, `SecretStreamOutputStream` for encryption and `SecretStreamInputStream` for decryption and authentication. Both take three parameters:

* An initialized LazySodium instance
* A stream to wrap
* A 32 byte encryption key

`SecretStreamOutputStream` takes an optional fourth parameter, the SecretStream chunk size (this is written during `SecretStreamOutputStream` initialization to the underlying `OutputStream` immediately prior to the SecretStream header, and read during `SecretStreamInputStream` initialization). If omitted, a chunk size of 65536 is used.

You can write arbitrary data (e.g., file format metadata, key management related data) to an `OutputStream` before wrapping it in a `SecretStreamOutputStream`, and read this data from the corresponding raw `InputStream` before wrapping it in a `SecretStreamInputStream`.

Data of arbitrary length can be written to a `SecretStreamOutputStream` and read from a `SecretStreamInputStream` using any of the standard `write()` and `read()` calls. Internally, `SecretStreamOutputStream` accumulates data passed in `write()` calls in a fixed size buffer; whenever this buffer fills up, the data is passed to the SecretStream API for encryption and the result is then written to the underlying `OutputStream`. (When the stream is closed, any remaining data is written in a final chunk, which may be smaller than the standard chunk size.) `SecretStreamInputStream` reads data in fixed size chunks, each of which is passed to the SecretStream API for decryption and authentication and the result stored in a fixed size buffer, from where it is provided to `read()` calls. **No data is returned until it has been authenticated.**

SS4K provides an additional utility function `passphraseToKey()` which is just a convenience wrapper around the libsodium / LazySodium function [`cryptoPwHash()`](https://libsodium.gitbook.io/doc/password_hashing/default_phf) to derive an encryption key from a passphrase via the [Argon2id algorithm](https://en.wikipedia.org/wiki/Argon2); see its API documentation for usage.

## Ssef.kt

SS4K includes a short but complete example application `Ssef.kt`, which implements passphrase based file encryption and decryption via `SecretStreamOutputStream`, `SecretStreamInputStream`, and `passphraseToKey`. The file format is the one used by [SMS Import / Export](https://github.com/tmo1/sms-ie) for its encrypted export files, and is documented [here](https://github.com/tmo1/sms-ie/blob/master/ENCRYPTION.md#internals).

`Ssef.kt` can be run via Gradle by cloning this repository, switching to the root directory, and running:

```
~$ ./gradlew run -q --console=plain --args="encrypt /path/to/somefile"
```

or:

```
~$ ./gradlew run -q --console=plain --args="decrypt /path/to/somefile.ssef"
```

If `Ssef.kt` is run without arguments or with invalid arguments, it will display a usage message.

### Decryption failure

When decryption of a chunk fails, libsodium does not (cannot?) report the reason for the failure. Accordingly, if decryption fails at any point in the process, `Ssef.kt` will abort the decryption and report `Decryption failure on chunk nnnn.` If `nnnn` is 1, then there are a variety of possible reasons for the failure, including the use of a passphrase different from the one used during encryption, but if `nnnn` is greater than 1, then the only possible explanation for the failure is that the file has been altered (either deliberately or accidentally).

## Cryptography

Here are some detailed discussions of streaming AEAD in general and the libsodium SecretStream implementation of it in particular:

* [Official documentation of the libsodium SecretStream algorithm and its cryptographic properties](https://doc.libsodium.org/doc/secret-key_cryptography/secretstream)
* [Loup Vaillant (developer of Monocypher) on streaming AEAD](https://old.reddit.com/r/crypto/comments/dz4m3h/does_streaming_interfaces_for_aead_have_any/)
* [A detailed discussion of streaming AEAD, the SecretStream algorithm, and other implementations of streaming AEAD on Cryptography Stack Exchange](https://crypto.stackexchange.com/a/106992)

## Background and alternatives

SS4K was originally written as a part of [SMS Import / Export](https://github.com/tmo1/sms-ie). I had originally added authenticated encryption to SMS I/E by wrapping `InputStream` and `OutputStream` classes with the standard Java / Kotlin `CipherInputStream` and `CipherOutputStream` classes, using the [ChaCha20-Poly1305 algorithm](https://en.wikipedia.org/wiki/ChaCha20-Poly1305), but I soon realized that these classes do not do *streaming* authenticated encryption - they store a single authentication tag at the end of the encrypted data, and don't verify the authenticity of the data until the entire stream has been read. (In general, the `CipherOutputStream` / `CipherInputStream` classes have a history of problems and confusion when used with authenticated encryption ciphers - see [here](https://blog.heckel.io/2014/03/01/cipherinputstream-for-aead-modes-is-broken-in-jdk7-gcm/), [here](https://mail.openjdk.org/pipermail/security-dev/2014-March/010238.html), [here](https://crypto.stackexchange.com/questions/20333/encryption-of-big-files-in-java-with-aes-gcm), [here](https://stackoverflow.com/questions/26920906/how-come-putting-the-gcm-authentication-tag-at-the-end-of-a-cipher-stream-requir), [here](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/CipherOutputStream.html)).

The only facility for ready made streaming authenticated encryption that I could find was [Google Tink's](https://developers.google.com/tink) [Streaming AEAD primitive](https://developers.google.com/tink/streaming-aead), but I found Tink frustrating to work with, in large part due to its highly opinionated insistence on the use of its key management abstractions and its push toward the use of [centralized Key management systems](https://en.wikipedia.org/wiki/Key_management#Key_management_system).

## License

SS4K is free / open source software, released under the terms of the [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html) or later.
