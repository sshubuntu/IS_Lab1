package ru.itmo.labwork.web;

import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.*;
import ru.itmo.labwork.service.Catalog;
import ru.itmo.labwork.service.LabService;

/**
 * Специальные операции над LabWork. Каждая операция живёт на отдельной странице и имеет собственное
 * поле ввода, поэтому параметры соседних команд не пересекаются. Таблица под формой показывает
 * затрагиваемые работы и обновляется прямо во время ввода.
 */
@Named
@ViewScoped
public class Operations implements Serializable {
  /** Колонки таблицы результатов: только то, что помогает узнать работу. */
  private static final List<String> RESULT_COLUMNS =
      List.of(
          "id",
          "name",
          "difficulty",
          "minimalPoint",
          "personalQualitiesMaximum",
          "discipline.name",
          "author.name");

  @Inject private LabService service;
  @Inject private Changes changes;

  private String prefix = "";
  private Integer labId, disciplineId, authorId, point;
  private Integer steps = 1;
  private String summary = "";
  private List<Map<String, String>> results = new ArrayList<>();

  // --- предпросмотр: вызывается во время ввода, ничего не меняет ---------------

  public void previewByPrefix() {
    Messages.safely(
        () -> {
          results = rows(service.special("prefix", prefix, null));
          summary =
              prefix == null || prefix.isEmpty()
                  ? "Все работы: " + results.size()
                  : "Работ, начинающихся с «" + prefix + "»: " + results.size();
        });
  }

  public void previewLab() {
    Messages.safely(
        () -> {
          if (clearWhenEmpty(labId, "Укажите ID лабораторной работы")) return;
          results = new ArrayList<>();
          results.add(service.row(service.get("LabWork", labId)));
          summary = "Текущая сложность: " + results.get(0).get("difficulty");
        });
  }

  public void previewProgram() {
    Messages.safely(
        () -> {
          if (clearWhenEmpty(disciplineId, "Укажите ID дисциплины")) return;
          results = service.program(disciplineId);
          summary = "Сейчас в программе дисциплины: " + results.size() + " работ";
        });
  }

  public void previewByAuthor() {
    Messages.safely(
        () -> {
          results = service.byAuthor(authorId);
          summary =
              (authorId == null ? "Работ без автора: " : "Работ этого автора: ") + results.size();
        });
  }

  public void previewByPoint() {
    Messages.safely(
        () -> {
          if (clearWhenEmpty(point, "Укажите минимальный балл для поиска")) return;
          results = service.byMinimalPoint(point);
          summary =
              results.isEmpty()
                  ? "Работ с таким баллом нет"
                  : "Работ с таким баллом: " + results.size() + "; будет удалена одна из них";
        });
  }

  // --- сами операции ----------------------------------------------------------

  public void lowerDifficulty() {
    if (Messages.safely(
        () -> {
          require(labId, "Укажите ID лабораторной работы");
          require(steps, "Укажите число шагов");
          service.special("lower", labId.toString(), steps.toString());
          results = new ArrayList<>(List.of(service.row(service.get("LabWork", labId))));
          summary = "Новая сложность лабораторной работы №" + labId + ": " + results.get(0).get("difficulty");
        })) changes.fire();
  }

  public void assignTopTen() {
    if (Messages.safely(
        () -> {
          require(disciplineId, "Укажите ID дисциплины");
          Object added = service.special("top", disciplineId.toString(), null);
          results = service.program(disciplineId);
          summary = "Добавлено работ: " + added + "; всего в программе: " + results.size();
        })) changes.fire();
  }

  public void deleteByAuthor() {
    if (Messages.safely(
        () -> {
          Object deleted =
              service.special("author", authorId == null ? null : authorId.toString(), null);
          results = service.byAuthor(authorId);
          summary = "Удалено работ: " + deleted;
        })) changes.fire();
  }

  public void deleteOneByPoint() {
    if (Messages.safely(
        () -> {
          require(point, "Укажите минимальный балл");
          Object deleted = service.special("point", point.toString(), null);
          results = service.byMinimalPoint(point);
          summary =
              deleted == null
                  ? "Работ с таким минимальным баллом нет"
                   : "Удалена лабораторная работа №" + deleted + "; осталось с таким минимальным баллом: " + results.size();
        })) changes.fire();
  }

  // --- вспомогательное --------------------------------------------------------

  /** Пустое поле — это не ошибка во время ввода: просто нечего показывать. */
  private boolean clearWhenEmpty(Integer value, String hint) {
    if (value != null) return false;
    results = new ArrayList<>();
    summary = hint;
    return true;
  }

  private static void require(Integer value, String message) {
    if (value == null) throw new IllegalArgumentException(message);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, String>> rows(Object result) {
    return new ArrayList<>((List<Map<String, String>>) result);
  }

  public List<String> getResultColumns() {
    return RESULT_COLUMNS;
  }

  public boolean isPaginated() {
    return results.size() > 10;
  }

  public String columnLabel(String path) {
    return switch (path) {
      case "minimalPoint" -> "Мин. балл";
      case "personalQualitiesMaximum" -> "Макс. качеств";
      case "discipline.name" -> "Дисциплина";
      case "author.name" -> "Автор";
      default -> Catalog.label(path);
    };
  }

  public String getSummary() {
    return summary;
  }

  public List<Map<String, String>> getResults() {
    return results;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(String v) {
    prefix = v;
  }

  public Integer getLabId() {
    return labId;
  }

  public void setLabId(Integer v) {
    labId = v;
  }

  public Integer getSteps() {
    return steps;
  }

  public void setSteps(Integer v) {
    steps = v;
  }

  public Integer getDisciplineId() {
    return disciplineId;
  }

  public void setDisciplineId(Integer v) {
    disciplineId = v;
  }

  public Integer getAuthorId() {
    return authorId;
  }

  public void setAuthorId(Integer v) {
    authorId = v;
  }

  public Integer getPoint() {
    return point;
  }

  public void setPoint(Integer v) {
    point = v;
  }
}
