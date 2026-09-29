package ru.itmo.labwork.service;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import ru.itmo.labwork.domain.*;

public final class Catalog {
  private Catalog() {}

  private static final Map<String, String> FIELD_LABELS =
      Map.ofEntries(
          Map.entry("id", "ID"),
          Map.entry("name", "Название"),
          Map.entry("description", "Описание"),
          Map.entry("creationDate", "Дата создания"),
          Map.entry("difficulty", "Сложность"),
          Map.entry("minimalPoint", "Минимальный балл"),
          Map.entry("personalQualitiesMaximum", "Максимум личных качеств"),
          Map.entry("x", "X"),
          Map.entry("y", "Y"),
          Map.entry("lectureHours", "Лекционные часы"),
          Map.entry("practiceHours", "Практические часы"),
          Map.entry("selfStudyHours", "Самоподготовка, ч"),
          Map.entry("labsCount", "Кол-во лабораторных"),
          Map.entry("eyeColor", "Цвет глаз"),
          Map.entry("hairColor", "Цвет волос"),
          Map.entry("birthday", "Дата рождения"),
          Map.entry("nationality", "Национальность"));

  private static final Map<String, String> OBJECT_LABELS =
      Map.of(
          "coordinates", "Координаты",
          "discipline", "Дисциплина",
          "author", "Автор",
          "location", "Локация");

  public static String label(String path) {
    return Arrays.stream(path.split("\\."))
        .map(part -> FIELD_LABELS.getOrDefault(part, OBJECT_LABELS.getOrDefault(part, part)))
        .collect(Collectors.joining(" · "));
  }

  public static final Map<String, Class<? extends RecordEntity>> TYPES;

  static {
    var m = new LinkedHashMap<String, Class<? extends RecordEntity>>();
    m.put("LabWork", LabWork.class);
    m.put("Coordinates", Coordinates.class);
    m.put("Discipline", Discipline.class);
    m.put("Person", Person.class);
    m.put("Location", Location.class);
    TYPES = Collections.unmodifiableMap(m);
  }

  public static Class<? extends RecordEntity> type(String key) {
    var type = TYPES.get(key);
    if (type == null) throw new IllegalArgumentException("Неизвестный тип");
    return type;
  }

  public static List<Field> editable(String key) {
    return Arrays.stream(type(key).getDeclaredFields())
        .filter(f -> !f.getName().equals("creationDate"))
        .toList();
  }

  public static Object read(Object object, String path) {
    if (object == null) return null;
    try {
      int dot = path.indexOf('.');
      String first = dot < 0 ? path : path.substring(0, dot);
      Object value =
          object
              .getClass()
              .getMethod("get" + Character.toUpperCase(first.charAt(0)) + first.substring(1))
              .invoke(object);
      return dot < 0 ? value : read(value, path.substring(dot + 1));
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static List<String> columns(String key) {
    var result = new ArrayList<String>();
    result.add("id");
    flatten(type(key), "", result);
    return result;
  }

  private static void flatten(Class<?> type, String prefix, List<String> out) {
    for (Field f : type.getDeclaredFields()) {
      String path = prefix + f.getName();
      if (RecordEntity.class.isAssignableFrom(f.getType())) {
        out.add(path + ".id");
        flatten(f.getType(), path + ".", out);
      } else out.add(path);
    }
  }

  public static List<String> stringColumns(String key) {
    return columns(key).stream()
        .filter(
            p -> {
              Class<?> type = type(key);
              try {
                for (String part : p.split("\\.")) {
                  if (part.equals("id")) return false;
                  type = type.getDeclaredField(part).getType();
                }
                return type == String.class || type.isEnum();
              } catch (Exception e) {
                throw new IllegalArgumentException(e);
              }
            })
        .toList();
  }

  public static List<String> ownColumns(String key) {
    var result = new ArrayList<String>();
    result.add("id");
    for (Field f : type(key).getDeclaredFields()) result.add(f.getName());
    return result;
  }

  public static String text(Object value) {
    return value == null ? "" : value.toString();
  }
}
