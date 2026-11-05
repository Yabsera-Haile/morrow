# SQLCipher calls back into these classes from native code.
-keep,includedescriptorclasses class net.zetetic.database.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.sqlcipher.** { *; }
