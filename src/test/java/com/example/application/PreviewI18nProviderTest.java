package com.example.application;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class PreviewI18nProviderTest {
    private ConfigMap configMapEn;
    private ConfigMap configMapDe;
    private ConfigMap configMapEnUs;

    @BeforeEach
    void setUp() {
        configMapEn = createConfigMap("en", Map.of());
        configMapDe = createConfigMap("de", Map.of());
        configMapEnUs = createConfigMap("en-US", Map.of());
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
        assertEquals(Locale.ENGLISH, locales.get(0));
        assertEquals(Locale.GERMAN, locales.get(1));

        provider = createProvider(configMapEn, configMapDe, configMapEnUs);

        locales = provider.getProvidedLocales();
        assertEquals(3, locales.size());
        assertEquals(Locale.ENGLISH, locales.get(0));
        assertEquals(Locale.GERMAN, locales.get(1));
        assertEquals(Locale.US, locales.get(2));
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

    private ConfigMap createConfigMap(String languageTag, Map<String, String> translations) {
        var metadata = new ObjectMeta();
        metadata.setLabels(Map.of(PreviewI18nProvider.PREVIEW_CONFIG_MAP_LABEL, languageTag));

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