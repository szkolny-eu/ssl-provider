# SSLProvider

The only TLS utility library for Android you'll ever need.

## What is this?

SSLProvider is a simple, lightweight library enabling the use of any TLS version (1.0-1.3) on any supported
Android version (down to 4.1 - API 16!).

The library uses the most appropriate method to enable TLS, considering the API level and the availability
of Google Play Services (GMS):

- Android 4.1 - 5.0, GMS, TLSv1.3 not required - GMS provider is used
- Android 4.1 - 5.0, no GMS, TLSv1.3 not required - Conscrypt is used
- Android 5.1 - 8.0, GMS/no GMS, TLSv1.3 not required - nothing is done (supports 1.0-1.2)
- Android 4.1 - 8.0, GMS/no GMS, TLSv1.3 **is required** - Conscrypt is used
- Android 8.1+ - nothing is done, as it supports all protocols natively

### Ok but what is Conscrypt?

[Conscrypt](https://github.com/google/conscrypt) is a Java Security Provider implementing an SSL engine,
allowing to use whatever TLS version Conscrypt supports on every Android version.

### What is this library for, then?

Shipping a whole SSL engine along with the Java binding code in your app, just to support those few
users with Jelly Bean, is not really the best idea. On the other hand, allowing them to connect to
Internet through your app also seems cool. Why not both?

Such an old device might not have Google Play Services installed, or they may be outdated. The default
`ProviderInstaller.installIfNeeded(context)` just doesn't work it that case.

The `SSLProvider` uses the GMS whenever applicable, else **downloads the Conscrypt library** on runtime,
loads it and installs. This allows to do both cool ideas - keeping the app size as low as possible,
while still supporting old platforms.

## Usage

Check out the [sample app](https://github.com/szkolny-eu/ssl-provider/blob/master/app/src/main/java/eu/szkolny/sslprovider/sample/MainActivity.kt) for a working example.

Install the dependency:
```groovy
repositories {
     jcenter()
     maven { url "https://jitpack.io" }
}
dependencies {
      implementation "eu.szkolny:ssl-provider:1.0.0"
}
```

Install the SSL provider:
```kotlin
SSLProvider.install(context, downloadIfNeeded = true, supportTls13 = true, onFinish = {
    // installation succeeded
}, onError = { e ->
    // there was a problem
    e.printStackTrace()
})
```

The `downloadIfNeeded` parameter indicates whether the Conscrypt library code should be downloaded
automatically. The `supportTls13` indicates whether TLSv1.3 support is required by your app.

### Important information

If you're using OkHttp (and possibly other HTTP clients), make sure to call `SSLProvider` **before
building your client** (or build another one after installing SSL). I am no expert, but the HTTP
client probably keeps some reference to the old SSL context, along with the provider and enabled
protocols.

### Important information #2

You should not install both the GMS provider AND the Conscrypt provider (i.e. first without TLS1.3,
then with TLS1.3) - this will crash your app. Just.. don't.

## What is in the Conscrypt ZIP file?

The ZIP file contains a compiled version of the [Conscrypt](https://github.com/google/conscrypt) library.
A [sample ZIP](http://s1.szkolny.eu/ssl-provider/conscrypt-android.zip) hosted by Szkolny.eu is included
as the default in the code.

**As the ZIP contains the SSL engine, it should be downloaded using HTTP :)**

The ZIP structure is as follows:
```
conscrypt-android.zip
    - arm64-v8a
        - libconscrypt_jni.so
    - armeabi-v7a
        - libconscrypt_jni.so
    - x86
        - libconscrypt_jni.so
    - x86_64
        - libconscrypt_jni.so
    - classes.dex
```

Unfortunately, as Android requires a .dex file to load classes from, it cannot be directly downloaded
from [Maven Central](https://search.maven.org/artifact/org.conscrypt/conscrypt-android). To "build" such
a ZIP file, you can use the `dx` utility from the Android SDK build-tools:
```shell script
$ dx --dex --output=conscrypt-android.jar classes.jar
```
Extract the `classes.jar` from the AAR downloaded from Maven. 

Rename `conscrypt-android.jar` to `conscrypt-android.zip`, then copy the `jni` directory's contents
(from the AAR) to the ZIP.

## Credits

- [Android: TLS 1.3 with OkHttp and Conscrypt on all Android versions (Tested on 4.1+)](https://gist.github.com/Karewan/4b0270755e7053b471fdca4419467216)
- [microG/GmsCore/ProviderInstallerImpl.java](https://github.com/microg/GmsCore/blob/v0.2.10.19420/play-services-core/src/main/java/com/google/android/gms/common/security/ProviderInstallerImpl.java)
- [Conscrypt JSP](https://github.com/google/conscrypt)

## License

```
   Copyright 2021 Kuba Szczodrzyński

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
