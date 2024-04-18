package com.example.application;

import com.vaadin.flow.i18n.I18NProvider;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.fabric8.kubernetes.client.dsl.Resource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class PreviewI18nProvider implements I18NProvider {
    static final String DEFAULT_NAMESPACE = "default";
    static final String PREVIEW_LANGUAGE_TAG_LABEL = "vaadin.cc.i18n.translation-preview.language-tag";
    static final String PREVIEW_DEFAULT_LANGUAGE_LABEL = "vaadin.cc.i18n.translation-preview.default-language";
    static final Logger logger = LoggerFactory.getLogger(PreviewI18nProvider.class);

    private final Map<Locale, PreviewLanguage> discoveredLanguages = new HashMap<>();

    @PostConstruct
    void initialize() {
        var resources = getResources();
        resources.forEach(resource -> {
            var configMap = resource.get();

            // Store initial translations
            updateTranslations(configMap);

            // Watch for changes
            resource.watch(new Watcher<>() {
                @Override
                public void eventReceived(Action action, ConfigMap updatedConfigMap) {
                    updateTranslations(updatedConfigMap);
                }

                @Override
                public void onClose(WatcherException cause) {
                }
            });
        });
    }

    Stream<Resource<ConfigMap>> getResources() {
        var client = new KubernetesClientBuilder().build();
        return client.configMaps().inNamespace(DEFAULT_NAMESPACE).withLabel(PREVIEW_LANGUAGE_TAG_LABEL).resources();
    }

    private void updateTranslations(ConfigMap configMap) {
        var locale = detectLocale(configMap);
        var isUpdate = discoveredLanguages.containsKey(locale);

        var translations = configMap.getData();
        var isDefault = isDefaultLanguage(configMap);
        var previewLanguage = new PreviewLanguage(locale, translations, isDefault);

        this.discoveredLanguages.put(locale, previewLanguage);

        if (isUpdate) {
            logger.info("Updated preview translations for locale: {}", locale);
        } else {
            logger.info("Found preview translations for locale: {}", locale);
        }
    }

    private Optional<PreviewLanguage> resolveLanguage(Locale locale) {
        // Look for specified locale first
        var language = Optional.ofNullable(discoveredLanguages.get(locale));

        // Use a locale with the same language as a fallback
        language = language.or(() -> discoveredLanguages.keySet().stream()
                .filter(l -> l.getLanguage().equals(locale.getLanguage()))
                .findFirst()
                .map(discoveredLanguages::get));

        // Use the default language as a last resort
        return language.or(() -> discoveredLanguages.values().stream()
                .filter(PreviewLanguage::isDefault)
                .findFirst());
    }

    /**
     * Config maps only allow certain characters in keys, whereas a message key
     * coming from a properties file may contain other characters. There's also
     * a max length of 253 characters. This is a best effort to generate a valid
     * config map key from a message key.
     *
     * @param messageKey the message key
     * @return the sanitized message key
     */
    private String generateConfigMapKey(String messageKey) {
        messageKey = messageKey.replaceAll("[^-._a-zA-Z0-9]", "_");
        return messageKey.substring(0, Math.min(messageKey.length(), 253));
    }

    private Locale detectLocale(ConfigMap configMap) {
        var languageTag = configMap.getMetadata().getLabels().get(PREVIEW_LANGUAGE_TAG_LABEL);

        if (languageTag == null || languageTag.isEmpty()) {
            logger.warn("ConfigMap {} does not have a language tag label", configMap.getMetadata().getName());
            return Locale.getDefault();
        }

        // Make sure the language tag is in the correct format
        var sanitizedLanguageTag = languageTag.replaceAll("_", "-");

        return Locale.forLanguageTag(sanitizedLanguageTag);
    }

    private boolean isDefaultLanguage(ConfigMap configMap) {
        return configMap.getMetadata().getLabels().containsKey(PREVIEW_DEFAULT_LANGUAGE_LABEL);
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return discoveredLanguages.keySet().stream().toList();
    }

    @Override
    public String getTranslation(String messageKey, Locale locale, Object... objects) {
        var language = resolveLanguage(locale);
        var configMapKey = generateConfigMapKey(messageKey);

        return language.map(PreviewLanguage::translations)
                .map(translations -> translations.get(configMapKey))
                .orElse(messageKey);
    }

    private record PreviewLanguage(Locale locale, Map<String, String> translations, boolean isDefault) {
    }
}
