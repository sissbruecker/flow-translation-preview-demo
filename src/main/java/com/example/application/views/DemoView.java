package com.example.application.views;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@PageTitle("Translation Preview Demo")
@Route(value = "demo", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
public class DemoView extends VerticalLayout {

    public DemoView() {
        var title = new H2(getTranslation("demo.form.title"));
        add(title);

        var firstName = new TextField(getTranslation("demo.form.firstName"));
        var lastName = new TextField(getTranslation("demo.form.lastName"));
        var birthDate = new TextField(getTranslation("demo.form.birthDate"));
        var phoneNumber = new TextField(getTranslation("demo.form.phoneNumber"));
        var email = new EmailField(getTranslation("demo.form.email"));
        var occupation = new TextField(getTranslation("demo.form.occupation"));

        var formLayout = new FormLayout();
        formLayout.add(firstName, lastName, birthDate, phoneNumber, email, occupation);
        add(formLayout);

        var save = new Button(getTranslation("demo.form.save"));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        var cancel = new Button(getTranslation("demo.form.cancel"));

        var buttons = new HorizontalLayout(save, cancel);
        add(buttons);
    }

}
