package com.example.application;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PreviewI18nProviderTest {
    private ConfigMap configMapEn;
    private ConfigMap configMapDe;
    private ConfigMap configMapEnUs;

    @BeforeEach
    void setUp() {
        var translationsEn = Map.of("app.title", "app.title - en");
        configMapEn = createConfigMap("en", translationsEn, true);

        var translationsDe = Map.of("app.title", "app.title - de");
        configMapDe = createConfigMap("de", translationsDe);

        var translationsEnUs = Map.of("app.title", "app.title - en-US");
        configMapEnUs = createConfigMap("en-US", translationsEnUs);
    }

    @SuppressWarnings("unchecked")
    @Test
    void initialize_watchesForConfigMapChanges() {
        var initialTranslations = Map.of("app.title", "Application title");
        var configMap = createConfigMap("en", initialTranslations);
        var resource = (Resource<ConfigMap>) Mockito.mock(Resource.class);
        when(resource.get()).thenReturn(configMap);

        var provider = spy(new PreviewI18nProvider());
        when(provider.getResources()).thenReturn(Stream.of(resource));
        provider.initialize();

        var translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("Application title", translation);

        var watcherCaptor = ArgumentCaptor.forClass(Watcher.class);
        Mockito.verify(resource).watch(watcherCaptor.capture());

        var updatedTranslations = Map.of("app.title", "Updated title");
        var updatedConfigMap = createConfigMap("en", updatedTranslations);
        watcherCaptor.getValue().eventReceived(Watcher.Action.MODIFIED, updatedConfigMap);

        translation = provider.getTranslation("app.title", Locale.ENGLISH);
        assertEquals("Updated title", translation);
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
        var messageKey = "(1) Some *important* information!";
        var configMapKey = "_1__Some__important__information_";
        var translations = Map.of(configMapKey, "translation");
        var configMap = createConfigMap("en", translations);
        var provider = createProvider(configMap);

        var translation = provider.getTranslation(messageKey, Locale.ENGLISH);
        assertEquals("translation", translation);
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
        var translation = provider.getTranslation("app.title", Locale.US);
        assertEquals("app.title - en-US", translation);

        translation = provider.getTranslation("app.title", Locale.GERMAN);
        assertEquals("app.title - de", translation);

        // Fall back to same language
        translation = provider.getTranslation("app.title", Locale.forLanguageTag("de-DE"));
        assertEquals("app.title - de", translation);

        translation = provider.getTranslation("app.title", Locale.forLanguageTag("en-GB"));
        assertEquals("app.title - en", translation);

        // Fall back to default language
        translation = provider.getTranslation("app.title", Locale.FRANCE);
        assertEquals("app.title - en", translation);

        translation = provider.getTranslation("app.title", Locale.CHINESE);
        assertEquals("app.title - en", translation);

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
        var resources = Stream.of(configMaps).map(configMap -> {
            var resource = (Resource<ConfigMap>) Mockito.mock(Resource.class);
            when(resource.get()).thenReturn(configMap);
            return resource;
        });
        when(provider.getResources()).thenReturn(resources);
        provider.initialize();
        return provider;
    }
}