package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Owner-provided token, encrypted with a non-exportable Android Keystore key. Never logged. */
final class ArtworkCredentials {
  private static final String ALIAS = "elppa_movie_artwork";
  private final SharedPreferences store;
  private String token = "";

  ArtworkCredentials(Context context) {
    store = context.getSharedPreferences("artwork_credentials", Context.MODE_PRIVATE);
    try {
      String encrypted = store.getString("encrypted", "");
      String iv = store.getString("iv", "");
      if (!encrypted.isEmpty() && !iv.isEmpty()) {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        token =
            new String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {
      token = "";
    }
  }

  String token() {
    return token;
  }

  boolean configured() {
    return !token.isEmpty();
  }

  static String normalise(String value) {
    return value.trim().replaceFirst("(?i)^Bearer\\s+", "");
  }

  boolean save(String value) {
    String candidate = normalise(value);
    if (!candidate.matches("[A-Za-z0-9._~-]{20,4096}")) return false;
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key());
      String encrypted =
          Base64.encodeToString(
              cipher.doFinal(candidate.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
      boolean saved =
          store
              .edit()
              .putString("encrypted", encrypted)
              .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
              .commit();
      if (saved) token = candidate;
      return saved;
    } catch (Exception ignored) {
      return false;
    }
  }

  void clear() {
    store.edit().clear().apply();
    token = "";
  }

  private SecretKey key() throws Exception {
    KeyStore store = KeyStore.getInstance("AndroidKeyStore");
    store.load(null);
    if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
    KeyGenerator generator =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
    generator.init(
        new KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
    return generator.generateKey();
  }
}
