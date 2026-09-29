package ru.itmo.labwork.web;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.ConverterException;
import jakarta.faces.convert.FacesConverter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@FacesConverter("localDateTime")
public class LocalDateTimeConverter implements Converter<LocalDateTime> {
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  @Override
  public LocalDateTime getAsObject(FacesContext context, UIComponent component, String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return LocalDateTime.parse(value.trim(), FORMAT);
    } catch (RuntimeException e) {
      throw new ConverterException("Неверный формат даты, ожидается «дд.ММ.гггг чч:мм»", e);
    }
  }

  @Override
  public String getAsString(FacesContext context, UIComponent component, LocalDateTime value) {
    return value == null ? "" : value.format(FORMAT);
  }
}