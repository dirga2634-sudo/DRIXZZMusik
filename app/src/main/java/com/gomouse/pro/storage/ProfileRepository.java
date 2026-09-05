package com.gomouse.pro.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.gomouse.pro.model.Profile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists {@link Profile}s as one JSON file per profile under the app's
 * private storage ({@code context.getFilesDir()/profiles/<id>.json}), so
 * profiles survive app restarts and device reboots without needing any
 * special permission. Also tracks which profile is currently active and a
 * most-recently-used list, both in SharedPreferences.
 */
public class ProfileRepository {

    private static final String PROFILES_DIR = "profiles";
    private static final String PREFS_NAME = "gomouse_prefs";
    private static final String KEY_ACTIVE_PROFILE_ID = "active_profile_id";
    private static final String KEY_RECENT_IDS = "recent_profile_ids";
    private static final int MAX_RECENTS = 10;

    private final Context appContext;
    private final Gson gson;
    private final File profilesDir;

    private static volatile ProfileRepository instance;

    public static ProfileRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ProfileRepository.class) {
                if (instance == null) {
                    instance = new ProfileRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private ProfileRepository(Context appContext) {
        this.appContext = appContext;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.profilesDir = new File(appContext.getFilesDir(), PROFILES_DIR);
        if (!profilesDir.exists()) {
            profilesDir.mkdirs();
        }
    }

    private File fileFor(String profileId) {
        return new File(profilesDir, profileId + ".json");
    }

    /** Saves (creates or overwrites) a profile and updates its updatedAt timestamp. */
    public synchronized boolean save(Profile profile) {
        profile.touch();
        File target = fileFor(profile.getId());
        File tmp = new File(profilesDir, profile.getId() + ".json.tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            gson.toJson(profile, writer);
            writer.flush();
        } catch (IOException e) {
            return false;
        }
        // Atomic-ish swap so a crash mid-write never corrupts the saved profile.
        return tmp.renameTo(target);
    }

    public synchronized Profile load(String profileId) {
        File file = fileFor(profileId);
        if (!file.exists()) {
            return null;
        }
        try (InputStream in = appContext.openFileInput(PROFILES_DIR + File.separator + profileId + ".json")) {
            InputStreamReaderHelper helper = new InputStreamReaderHelper(in);
            return gson.fromJson(helper.readAll(), Profile.class);
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    public synchronized List<Profile> loadAll() {
        List<Profile> result = new ArrayList<>();
        File[] files = profilesDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                try (InputStream in = new java.io.FileInputStream(f)) {
                    InputStreamReaderHelper helper = new InputStreamReaderHelper(in);
                    Profile p = gson.fromJson(helper.readAll(), Profile.class);
                    if (p != null) {
                        result.add(p);
                    }
                } catch (IOException | JsonSyntaxException ignored) {
                    // Skip unreadable/corrupt profile files rather than failing the whole list.
                }
            }
        }
        Collections.sort(result, Comparator.comparingLong(Profile::getUpdatedAt).reversed());
        return result;
    }

    public synchronized boolean delete(String profileId) {
        File file = fileFor(profileId);
        boolean deleted = !file.exists() || file.delete();
        removeFromRecents(profileId);
        if (profileId.equals(getActiveProfileId())) {
            setActiveProfileId(null);
        }
        return deleted;
    }

    public synchronized boolean exists(String profileId) {
        return fileFor(profileId).exists();
    }

    // --- Export / import ---------------------------------------------------

    /** Writes the profile's JSON to an arbitrary output stream (e.g. one opened via a share Intent). */
    public boolean exportTo(Profile profile, OutputStream out) {
        try (Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            gson.toJson(profile, writer);
            writer.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Reads a profile from an arbitrary input stream (e.g. a picked file Uri) and assigns it a fresh id. */
    public Profile importFrom(InputStream in) {
        try {
            InputStreamReaderHelper helper = new InputStreamReaderHelper(in);
            Profile imported = gson.fromJson(helper.readAll(), Profile.class);
            if (imported == null) {
                return null;
            }
            // Always mint a new id on import so it can never collide with (or
            // silently overwrite) an existing local profile.
            imported.setId(java.util.UUID.randomUUID().toString());
            return imported;
        } catch (IOException | JsonSyntaxException e) {
            return null;
        }
    }

    /** Copies a file to app-private cache storage and returns a content:// Uri sharable via FileProvider. */
    public Uri prepareShareUri(Profile profile, String authority) throws IOException {
        File cacheDir = new File(appContext.getCacheDir(), "shared_profiles");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        String safeName = profile.getName().replaceAll("[^a-zA-Z0-9-_]", "_");
        File shareFile = new File(cacheDir, safeName + ".gomouseprofile.json");
        try (OutputStream out = new FileOutputStream(shareFile)) {
            exportTo(profile, out);
        }
        return androidx.core.content.FileProvider.getUriForFile(appContext, authority, shareFile);
    }

    // --- Active profile + recents ------------------------------------------

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getActiveProfileId() {
        return prefs().getString(KEY_ACTIVE_PROFILE_ID, null);
    }

    public void setActiveProfileId(String profileId) {
        prefs().edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).apply();
        if (profileId != null) {
            markRecent(profileId);
        }
    }

    public void markRecent(String profileId) {
        Set<String> ordered = new LinkedHashSet<>(getRecentIds());
        ordered.remove(profileId);
        List<String> asList = new ArrayList<>();
        asList.add(profileId);
        asList.addAll(ordered);
        if (asList.size() > MAX_RECENTS) {
            asList = asList.subList(0, MAX_RECENTS);
        }
        saveRecentIds(asList);
    }

    private void removeFromRecents(String profileId) {
        List<String> ids = getRecentIds();
        ids.remove(profileId);
        saveRecentIds(ids);
    }

    public List<String> getRecentIds() {
        String joined = prefs().getString(KEY_RECENT_IDS, "");
        List<String> ids = new ArrayList<>();
        if (!joined.isEmpty()) {
            Collections.addAll(ids, joined.split(","));
        }
        return ids;
    }

    private void saveRecentIds(List<String> ids) {
        prefs().edit().putString(KEY_RECENT_IDS, String.join(",", ids)).apply();
    }

    public List<Profile> loadRecents() {
        List<Profile> result = new ArrayList<>();
        for (String id : getRecentIds()) {
            Profile p = load(id);
            if (p != null) {
                result.add(p);
            }
        }
        return result;
    }

    /** Small helper to read a whole InputStream as a UTF-8 string without extra dependencies. */
    private static class InputStreamReaderHelper {
        private final InputStream in;

        InputStreamReaderHelper(InputStream in) {
            this.in = in;
        }

        String readAll() throws IOException {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString("UTF-8");
        }
    }
}
