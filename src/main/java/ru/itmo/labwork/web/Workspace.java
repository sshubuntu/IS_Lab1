package ru.itmo.labwork.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.*;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ru.itmo.labwork.domain.*;
import ru.itmo.labwork.service.*;

@Named
@ViewScoped
public class Workspace implements Serializable {
  @Inject private LabService service;
  private String kind = "LabWork",
      sort = "id",
      mode = "view",
      param1 = "",
      param2 = "0",
      operation = "prefix";
  private boolean descending;
  private int page;
  private long total, version;
  private Integer selectedId, replacement, lookupId;
  private Map<String, String> filters = new LinkedHashMap<>(),
      values = new LinkedHashMap<>(),
      detail = new LinkedHashMap<>();
  private Map<String, LocalDateTime> dateValues = new LinkedHashMap<>();
  private List<Map<String, String>> rows = new ArrayList<>(), results = new ArrayList<>();
  private String result = "";

  @jakarta.annotation.PostConstruct
  public void init() {
    refresh();
  }

  private void safely(Runnable action) {
    try {
      action.run();
    } catch (Exception e) {
      FacesContext.getCurrentInstance().validationFailed();
      message(FacesMessage.SEVERITY_ERROR, error(e));
    }
  }

  private String error(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause())
      if (t instanceof IllegalArgumentException) return t.getMessage();
    for (Throwable t = e; t != null; t = t.getCause())
      if (t instanceof org.postgresql.util.PSQLException p) {
        return switch (p.getSQLState()) {
          case "23503" ->
              "Связанный объект удалён или используется другими записями. Обновите форму и выберите"
                  + " замену.";
          case "23514", "23502", "22001", "22003" ->
              "Значение нарушает ограничения поля. Проверьте обязательные поля, длину и диапазон"
                  + " чисел.";
          case "P0001" -> p.getServerErrorMessage().getMessage();
          default -> "Не удалось выполнить операцию БД; повторите после обновления.";
        };
      }
    return "Операция не выполнена. Обновите данные и проверьте ввод.";
  }

  private void message(FacesMessage.Severity severity, String text) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
  }

  public void refresh() {
    safely(
        () -> {
          var p = service.page(kind, filters, sort, descending, page);
          rows = p.rows();
          total = p.total();
          page = Math.min(page, Math.max(0, (int) ((total - 1) / 20)));
          if (selectedId != null && mode.equals("view"))
            try {
              detail = service.row(service.get(kind, selectedId));
            } catch (IllegalArgumentException e) {
              detail = new LinkedHashMap<>();
            }
        });
  }

  public void changeKind() {
    page = 0;
    sort = "id";
    filters.clear();
    selectedId = null;
    detail.clear();
    refresh();
  }

  public void search() {
    page = 0;
    refresh();
  }

  public void next() {
    page++;
    refresh();
  }

  public void previous() {
    page = Math.max(0, page - 1);
    refresh();
  }

  public void create() {
    selectedId = null;
    values = new LinkedHashMap<>();
    dateValues.clear();
    mode = "edit";
    for (Field f : Catalog.editable(kind))
      if (f.getType() == LocalDateTime.class) dateValues.put(f.getName(), null);
      else values.put(f.getName(), f.getType().isPrimitive() ? "0" : "");
  }

  public void open(String id) {
    safely(
        () -> {
          var e = service.get(kind, Integer.parseInt(id));
          selectedId = e.getId();
          version = e.getVersion();
          detail = service.row(e);
          mode = "view";
        });
  }

  public void lookup() {
    if (lookupId != null) open(lookupId.toString());
  }

  public void editRow(String id) {
    open(id);
    edit();
  }

  public void edit() {
    if (selectedId == null) return;
    safely(
        () -> {
          var e = service.get(kind, selectedId);
          version = e.getVersion();
          values = new LinkedHashMap<>();
          dateValues.clear();
          for (Field f : Catalog.editable(kind)) {
            Object v = Catalog.read(e, f.getName());
            if (f.getType() == LocalDateTime.class)
              dateValues.put(f.getName(), (LocalDateTime) v);
            else
              values.put(
                  f.getName(),
                  v instanceof RecordEntity ref ? ref.getId().toString() : Catalog.text(v));
          }
          mode = "edit";
        });
  }

  public void prepareDelete() {
    replacement = null;
    mode = "delete";
  }

  public void prepareDeleteRow(String id) {
    open(id);
    prepareDelete();
  }

  public void save() {
    safely(
        () -> {
          for (Field f : Catalog.editable(kind))
            if (f.getType() == LocalDateTime.class)
              values.put(
                  f.getName(),
                  dateValues.get(f.getName()) == null
                      ? ""
                      : dateValues.get(f.getName()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
          selectedId = service.save(kind, selectedId, version, values);
          open(selectedId.toString());
          refresh();
          message(FacesMessage.SEVERITY_INFO, "Объект сохранён");
        });
  }

  public void delete() {
    safely(
        () -> {
          service.delete(kind, selectedId, replacement, version);
          selectedId = null;
          detail.clear();
          mode = "view";
          refresh();
          message(FacesMessage.SEVERITY_INFO, "Объект удалён, ссылки перенесены");
        });
  }

  public List<Input> getInputs() {
    return Catalog.editable(kind).stream().map(f -> new Input(f, service)).toList();
  }

  public static class Input {
    private final Field field;
    private final LabService service;

    Input(Field f, LabService s) {
      field = f;
      service = s;
    }

    public String getName() {
      return field.getName();
    }

    public boolean isChoice() {
      return field.getType().isEnum() || RecordEntity.class.isAssignableFrom(field.getType());
    }

    public boolean isDate() {
      return field.getType() == LocalDateTime.class;
    }

    public Map<String, String> getOptions() {
      Map<String, String> options = new LinkedHashMap<>();
      options.put("— не задано —", "");
      if (field.getType().isEnum())
        for (Object v : field.getType().getEnumConstants()) options.put(v.toString(), v.toString());
      else if (isChoice())
        for (RecordEntity e : service.all(field.getType().getSimpleName())) {
          String label = e.toString();
          try {
            label += " " + Catalog.read(e, "name");
          } catch (Exception ignored) {
          }
          options.put(label, e.getId().toString());
        }
      return options;
    }

    public String getHint() {
      Class<?> t = field.getType();
      String hint =
          t == java.time.LocalDateTime.class
              ? "Выберите дату в календаре"
              : t == Float.class
                  ? "Конечное вещественное число"
                  : t == int.class
                      ? "Целое число (32 бита)"
                      : t == long.class || t == Long.class ? "Целое число (64 бита)" : "";
      if (field.getAnnotation(jakarta.validation.constraints.NotNull.class) != null
          || field.getAnnotation(jakarta.validation.constraints.NotBlank.class) != null)
        hint += " • обязательно";
      if (field.getAnnotation(jakarta.validation.constraints.Positive.class) != null)
        hint += " • больше 0";
      if (field.getName().equals("name") && field.getDeclaringClass() == Location.class)
        hint += " • до 291 символа";
      return hint;
    }
  }

  public void runSpecial() {
    safely(
        () -> {
          String a = param1, e = param2;
          if (operation.equals("lower") && (e == null || e.isBlank())) e = "0";
          Object r = service.special(operation, a, e);
          results = new ArrayList<>();
          if (r instanceof List<?> l) {
            results = (List<Map<String, String>>) l;
            result = "Найдено: " + l.size();
          } else result = r == null ? "Совпадений нет" : "Результат: " + r;
          refresh();
        });
  }

  public void showProgram() {
    safely(
        () -> {
          results = service.program(selectedId);
          result = "Программа дисциплины: " + results.size() + " работ";
        });
  }

  public List<String> getKinds() {
    return new ArrayList<>(Catalog.TYPES.keySet());
  }

  public List<String> getColumns() {
    return Catalog.columns(kind);
  }

  public List<String> getStringColumns() {
    return Catalog.stringColumns(kind);
  }

  public List<String> getResultColumns() {
    return Catalog.columns("LabWork");
  }

  public List<RecordEntity> getReplacements() {
    return service.all(kind).stream().filter(e -> !e.getId().equals(selectedId)).toList();
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String v) {
    kind = v;
  }

  public String getSort() {
    return sort;
  }

  public void setSort(String v) {
    sort = v;
  }

  public boolean isDescending() {
    return descending;
  }

  public void setDescending(boolean v) {
    descending = v;
  }

  public Map<String, String> getFilters() {
    return filters;
  }

  public Map<String, String> getValues() {
    return values;
  }

  public List<Map<String, String>> getRows() {
    return rows;
  }

  public Map<String, String> getDetail() {
    return detail;
  }

  public List<Map<String, String>> getResults() {
    return results;
  }

  public String getResult() {
    return result;
  }

  public String getMode() {
    return mode;
  }

  public Integer getSelectedId() {
    return selectedId;
  }

  public Integer getReplacement() {
    return replacement;
  }

  public void setReplacement(Integer v) {
    replacement = v;
  }

  public Integer getLookupId() {
    return lookupId;
  }

  public void setLookupId(Integer v) {
    lookupId = v;
  }

  public String getArg() {
    return param1;
  }

  public void setArg(String v) {
    param1 = v;
  }

  public String getExtra() {
    return param2;
  }

  public void setExtra(String v) {
    param2 = v;
  }

  public String getParam1() {
    return param1;
  }

  public void setParam1(String v) {
    param1 = v;
  }

  public String getParam2() {
    return param2;
  }

  public void setParam2(String v) {
    param2 = v;
  }

  public Map<String, LocalDateTime> getDateValues() {
    return dateValues;
  }

  public List<String> getTopColumns() {
    return Catalog.ownColumns(kind);
  }

  public List<String> getSubgroups() {
    var set = new LinkedHashSet<String>();
    for (String column : Catalog.columns(kind)) {
      int dot = column.indexOf('.');
      if (dot > 0) set.add(column.substring(0, dot));
    }
    return new ArrayList<>(set);
  }

  public Map<String, List<String>> getSubcolumns() {
    var map = new LinkedHashMap<String, List<String>>();
    for (String group : getSubgroups()) {
      var list = new ArrayList<String>();
      for (String column : Catalog.columns(kind))
        if (column.startsWith(group + ".") && !column.endsWith(".id")) list.add(column);
      map.put(group, list);
    }
    return map;
  }

  public String columnLabel(String path) {
    return Catalog.label(path);
  }

  public int subcolumnCount(String group) {
    return getSubcolumns().getOrDefault(group, List.of()).size();
  }

  public String getOperation() {
    return operation;
  }

  public void setOperation(String v) {
    operation = v;
  }

  public long getTotal() {
    return total;
  }

  public int getPageNumber() {
    return page + 1;
  }

  public boolean isLast() {
    return (page + 1) * 20 >= total;
  }

  public boolean isFirst() {
    return page == 0;
  }
}
