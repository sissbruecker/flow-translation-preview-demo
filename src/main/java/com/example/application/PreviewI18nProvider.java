package com.example.application;

import com.vaadin.flow.i18n.I18NProvider;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PreviewI18nProvider implements I18NProvider {
    private static final String DEFAULT_NAMESPACE = "default";
    private static final String PREVIEW_CONFIG_MAP_LABEL = "vaadin.cc.i18n.translation-preview";
    private static final String DEFAULT_LANGUAGE_TAG = "default";
    private static final Logger logger = LoggerFactory.getLogger(PreviewI18nProvider.class);

    private List<Locale> locales = new ArrayList<>();
    private final Map<String, Map<String, String>> translations = new HashMap<>();

    @PostConstruct
    private void initialize() {
        System.out.println("Initializing PreviewI18nProvider");
        var client = new KubernetesClientBuilder().build();
        var locales = new ArrayList<Locale>();
        client.configMaps().inNamespace(DEFAULT_NAMESPACE).withLabel(PREVIEW_CONFIG_MAP_LABEL).resources().forEach(resource -> {
            var configMap = resource.get();
            var languageTag = configMap.getMetadata().getLabels().get(PREVIEW_CONFIG_MAP_LABEL);
            var locale = Locale.forLanguageTag(languageTag);
            locales.add(locale);

            // Store initial translations
            updateTranslations(languageTag, configMap);
            logger.info("Found preview translations for language: {}", languageTag);

            // Watch for changes
            resource.watch(new Watcher<>() {
                @Override
                public void eventReceived(Action action, ConfigMap updatedConfigMap) {
                    var languageTag = updatedConfigMap.getMetadata().getLabels().get(PREVIEW_CONFIG_MAP_LABEL);
                    updateTranslations(languageTag, updatedConfigMap);
                    logger.info("Updated preview translations for language: {}", languageTag);
                }

                @Override
                public void onClose(WatcherException cause) {
                }
            });
        });

        this.locales = locales;
    }

    private void updateTranslations(String languageTag, ConfigMap configMap) {
        var translations = configMap.getData();
        this.translations.put(languageTag, translations);
    }

    private Map<String, String> resolveTranslations(Locale locale) {
        var languageTag = locale.toLanguageTag();
        // Look for specified locale first
        if (translations.containsKey(languageTag)) {
            return translations.get(languageTag);
        }
        // If not found, look for the default language
        if (translations.containsKey(DEFAULT_LANGUAGE_TAG)) {
            return translations.get(DEFAULT_LANGUAGE_TAG);
        }
        // If no translations are found, return an empty map
        return Map.of();
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

    @Override
    public List<Locale> getProvidedLocales() {
        return locales;
    }

    @Override
    public String getTranslation(String messageKey, Locale locale, Object... objects) {
        var translations = resolveTranslations(locale);
        var configMapKey = generateConfigMapKey(messageKey);

        return translations.getOrDefault(configMapKey, messageKey);
    }
}
