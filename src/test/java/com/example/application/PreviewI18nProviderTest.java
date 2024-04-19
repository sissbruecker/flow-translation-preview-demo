package com.example.application;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Watcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PreviewI18nProviderTest {
    private ConfigMap configMapEn;
    private ConfigMap configMapDe;
    private ConfigMap configMapEnUs;

    @BeforeEach
    void setUp() {
        var translationsEn = new HashMap<String, String>();
        translationsEn.put("language", "en");
        translationsEn.put("format", "Total: {0,number,#.##}");
        configMapEn = createConfigMap("en", translationsEn, true);

        var translationsDe = new HashMap<String, String>();
        translationsDe.put("language", "de");
        translationsDe.put("format", "Gesamt: {0,number,#.##}");
        configMapDe = createConfigMap("de", translationsDe);

        var translationsEnUs = new HashMap<String, String>();
        translationsEnUs.put("language", "en-US");
        translationsEnUs.put("format", "Total: {0,number,#.##}");
        configMapEnUs = createConfigMap("en-US", translationsEnUs);
    }

    @SuppressWarnings("unchecked")
    @Test
    void initialize_watchesForConfigMapChanges() {
        // Start with no translations
        var provider = createProvider();
        var watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        verify(provider, times(1)).watchResources(watcherCaptor.capture());
        var watcher = watcherCaptor.getValue();

        var translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("app.title", translation);

        // Add translations
        var translations = Map.of("app.title", "Application title");
        var configMap = createConfigMap("en", translations);
        watcher.eventReceived(Watcher.Action.ADDED, configMap);

        translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("Application title", translation);

        // Update translations
        translations = Map.of("app.title", "New title");
        configMap = createConfigMap("en", translations);
        watcher.eventReceived(Watcher.Action.MODIFIED, configMap);

        translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("New title", translation);

        // Remove translations
        watcher.eventReceived(Watcher.Action.DELETED, configMap);

        translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("app.title", translation);
    }

    @Test
    void getProvidedLocales_detectsLocalesFromConfigMaps() {
        var provider = createProvider();
        var locales = provider.getProvidedLocales();

        assertEquals(0, locales.size());

        provider = createProvider(configMapEn, configMapDe);

        locales = provider.getProvidedLocales();
        assertEquals(2, locales.size());
        assertTrue(locales.contains(Locale.ENGLISH));
        assertTrue(locales.contains(Locale.GERMAN));

        provider = createProvider(configMapEn, configMapDe, configMapEnUs);

        locales = provider.getProvidedLocales();
        assertEquals(3, locales.size());
        assertTrue(locales.contains(Locale.ENGLISH));
        assertTrue(locales.contains(Locale.GERMAN));
        assertTrue(locales.contains(Locale.US));
    }

    @Test
    void getTranslation_convertsMessageKeyIntoConfigMapKey() {
        var translations = new HashMap<String, String>();

        var invalidCharactersKey = "(1) Some *important* information!";
        var invalidCharactersConfigMapKey = "_1__Some__important__information_";
        translations.put(invalidCharactersConfigMapKey, "invalid characters");

        var tooLongKey = "a".repeat(260);
        var tooLongConfigMapKey = "a".repeat(253);
        translations.put(tooLongConfigMapKey, "too long");

        var configMap = createConfigMap("en", translations);
        var provider = createProvider(configMap);

        var translation = provider.getTranslation(invalidCharactersKey, Locale.ENGLISH);
        assertEquals("invalid characters", translation);

        translation = provider.getTranslation(tooLongKey, Locale.ENGLISH);
        assertEquals("too long", translation);
    }

    @Test
    void getTranslation_returnsMessageKeyForMissingTranslations() {
        var provider = createProvider();

        var translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("app.title", translation);

        translation = provider.getTranslation("Application title", Locale.ENGLISH);
        assertEquals("Application title", translation);
    }

    @Test
    void getTranslation_handlesConfigMapsWithNonStandardLanguageTag() {
        var translations = Map.of("app.title", "Application title");
        var configMap = createConfigMap("en_US", translations);
        var provider = createProvider(configMap);

        var translation = provider.getTranslation("app.title", Locale.US);
        assertEquals("Application title", translation);
    }

    @Test
    void getTranslation_properlyResolvesLocale() {
        var provider = createProvider(configMapEn, configMapDe, configMapEnUs);

        // Exact match
        var translation = provider.getTranslation("language", Locale.US);
        assertEquals("en-US", translation);

        translation = provider.getTranslation("language", Locale.GERMAN);
        assertEquals("de", translation);

        // Fall back to same language
        translation = provider.getTranslation("language", Locale.forLanguageTag("de-DE"));
        assertEquals("de", translation);

        translation = provider.getTranslation("language", Locale.forLanguageTag("en-GB"));
        assertEquals("en", translation);

        // Fall back to default language
        translation = provider.getTranslation("language", Locale.FRANCE);
        assertEquals("en", translation);

        translation = provider.getTranslation("language", Locale.CHINESE);
        assertEquals("en", translation);
    }

    @Test
    void getTranslation_supportsMessageFormat() {
        var provider = createProvider(configMapEn, configMapDe, configMapEnUs);

        // Exact match
        var translation = provider.getTranslation("format", Locale.US, 123.456);
        assertEquals("Total: 123.46", translation);

        translation = provider.getTranslation("format", Locale.GERMAN, 123.456);
        assertEquals("Gesamt: 123,46", translation);

        // Fall back to same language
        translation = provider.getTranslation("format", Locale.forLanguageTag("de-DE"), 123.456);
        assertEquals("Gesamt: 123,46", translation);

        // Fall back to default language
        translation = provider.getTranslation("format", Locale.FRANCE, 123.456);
        assertEquals("Total: 123.46", translation);
    }

    private ConfigMap createConfigMap(String languageTag, Map<String, String> translations) {
        return createConfigMap(languageTag, translations, false);
    }

    private ConfigMap createConfigMap(String languageTag, Map<String, String> translations, boolean isDefault) {
        var metadata = new ObjectMeta();
        var labels = new HashMap<String, String>();
        labels.put(PreviewI18nProvider.PREVIEW_LANGUAGE_TAG_LABEL, languageTag);
        if (isDefault) {
            labels.put(PreviewI18nProvider.PREVIEW_DEFAULT_LANGUAGE_LABEL, "true");
        }
        metadata.setLabels(labels);

        var configMap = new ConfigMap();
        configMap.setMetadata(metadata);
        configMap.getData().putAll(translations);
        return configMap;
    }

    @SuppressWarnings("unchecked")
    private PreviewI18nProvider createProvider(ConfigMap... configMaps) {
        var provider = spy(new PreviewI18nProvider());
        var watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        doAnswer(invocation -> {
            // Ignore, just disable the original implementation
            return null;
        }).when(provider).watchResources(watcherCaptor.capture());
        provider.initialize();
        Arrays.stream(configMaps).forEach(configMap -> watcherCaptor.getValue().eventReceived(Watcher.Action.ADDED, configMap));
        return provider;
    }
}
