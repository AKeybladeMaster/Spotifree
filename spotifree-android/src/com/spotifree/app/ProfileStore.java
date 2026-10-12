package com.spotifree.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ProfileStore {
    private static final String[] URLS = {"https://open.spotify.com", "https://spotify.com", "https://accounts.spotify.com"};
    private static final String KEY_ALIAS = "spotifree-profile-key";
    private final SharedPreferences prefs;

    ProfileStore(Context context) { prefs = context.getSharedPreferences("profiles", 0); }

    List<String> names() {
        String raw = prefs.getString("names", "");
        List<String> result = new ArrayList<String>();
        if (!raw.isEmpty()) result.addAll(Arrays.asList(raw.split("\\n")));
        return result;
    }

    String active() { return prefs.getString("active", "Default"); }

    boolean valid(String name) { return name != null && name.matches("[A-Za-z0-9 _-]{1,40}"); }

    void save(String name) throws Exception {
        if (!valid(name)) throw new IllegalArgumentException("Invalid profile name");
        CookieManager manager = CookieManager.getInstance();
        JSONObject json = new JSONObject();
        for (String url : URLS) json.put(url, manager.getCookie(url));
        String encrypted = encrypt(json.toString());
        List<String> names = names();
        if (!names.contains(name)) names.add(name);
        prefs.edit().putString("names", join(names)).putString("cookie:" + name, encrypted)
            .putString("active", name).apply();
    }

    void switchTo(String name, Runnable done) throws Exception {
        String encrypted = prefs.getString("cookie:" + name, null);
        if (encrypted == null) throw new IllegalArgumentException("Profile not found");
        final JSONObject json = new JSONObject(decrypt(encrypted));
        prefs.edit().putString("active", name).apply();
        final CookieManager manager = CookieManager.getInstance();
        manager.removeAllCookies(value -> {
            for (String url : URLS) {
                String cookie = json.optString(url, "");
                for (String pair : cookie.split(";\\s*")) {
                    if (!pair.isEmpty() && pair.contains("=")) manager.setCookie(url, pair + "; Secure; SameSite=Lax");
                }
            }
            manager.flush();
            done.run();
        });
    }

    void delete(String name) {
        List<String> names = names();
        names.remove(name);
        SharedPreferences.Editor editor = prefs.edit().remove("cookie:" + name).putString("names", join(names));
        if (name.equals(active())) editor.putString("active", "Default");
        editor.apply();
    }

    private String join(List<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) { if (result.length() > 0) result.append('\n'); result.append(name); }
        return result.toString();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return (SecretKey) store.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(data.getBytes("UTF-8"));
        byte[] output = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, output, 0, iv.length);
        System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);
        return Base64.encodeToString(output, Base64.NO_WRAP);
    }

    private String decrypt(String encoded) throws Exception {
        byte[] bytes = Base64.decode(encoded, Base64.DEFAULT);
        byte[] iv = Arrays.copyOfRange(bytes, 0, 12);
        byte[] ciphertext = Arrays.copyOfRange(bytes, 12, bytes.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }
}
