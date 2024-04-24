package com.example.application.views;

import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;

import java.util.Locale;

public class LanguageSelect extends Select<Locale> implements LocaleChangeObserver {
    public LanguageSelect(I18NProvider i18NProvider) {
        super();
        setItems(i18NProvider.getProvidedLocales());
        setItemLabelGenerator(Locale::getDisplayLanguage);
        addValueChangeListener(event -> {
            if (event.isFromClient()) {
                getUI().ifPresent(ui -> {
                    ui.setLocale(event.getValue());
                    ui.getSession().setLocale(event.getValue());
                    ui.getPage().reload();
                });
            }
        });
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        setValue(event.getLocale());
    }
}
