package ru.itmo.labwork.web;

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
  @Inject private Changes changes;

  private String kind = "LabWork", sort = "id", mode = "view";
  private boolean descending;
  private int page;
  private long total, version;
  private Integer selectedId, replacement, lookupId;
  private int dependents;
  private Map<String, String> filters = new LinkedHashMap<>(),
      values = new LinkedHashMap<>(),
      detail = new LinkedHashMap<>();
  private Map<String, LocalDateTime> dateValues = new LinkedHashMap<>();
  // JSF/PrimeFaces может вернуть Number при создании и String при редактировании.
  private Map<String, Object> numberValues = new LinkedHashMap<>();
  private List<Map<String, String>> rows = new ArrayList<>();

  @jakarta.annotation.PostConstruct
  public void init() {
    refresh();
  }

  public void refresh() {
    Messages.safely(
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
              selectedId = null;
            }
        });
  }

  public void changeKind() {
    page = 0;
    sort = "id";
    descending = false;
    filters.clear();
    selectedId = null;
    lookupId = null;
    detail.clear();
    refresh();
  }

  public void search() {
    page = 0;
    refresh();
  }

  /** Сортировка по колонке: повторный клик меняет направление. */
  public void applySort() {
    String column =
        FacesContext.getCurrentInstance()
            .getExternalContext()
            .getRequestParameterMap()
            .get("sortColumn");
    if (column == null || !Catalog.ownColumns(kind).contains(column)) return;
    if (column.equals(sort)) descending = !descending;
    else {
      sort = column;
      descending = false;
    }
    page = 0;
    refresh();
  }

  public String sortIcon(String column) {
    if (!column.equals(sort)) return "pi pi-sort-alt";
    return descending ? "pi pi-sort-amount-down" : "pi pi-sort-amount-up";
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
    version = 0;
    values = new LinkedHashMap<>();
    dateValues.clear();
    numberValues.clear();
    mode = "edit";
    for (Field f : Catalog.editable(kind)) {
      Class<?> t = f.getType();
      if (t == LocalDateTime.class) dateValues.put(f.getName(), null);
      else if (isNumber(t)) numberValues.put(f.getName(), defaultNumber(f));
      else values.put(f.getName(), "");
    }
  }

  public void open(String id) {
    Messages.safely(
        () -> {
          var e = service.get(kind, Integer.parseInt(id));
          selectedId = e.getId();
          version = e.getVersion();
          detail = service.row(e);
          mode = "view";
        });
  }

  public void lookup() {
    if (lookupId == null) {
      Messages.error("Укажите ID объекта");
      detail.clear();
      selectedId = null;
      return;
    }
    open(lookupId.toString());
  }

  public void clearDetail() {
    selectedId = null;
    lookupId = null;
    detail.clear();
  }

  public void editRow(String id) {
    open(id);
    edit();
  }

  public void edit() {
    if (selectedId == null) return;
    Messages.safely(
        () -> {
          var e = service.get(kind, selectedId);
          version = e.getVersion();
          values = new LinkedHashMap<>();
          dateValues.clear();
          numberValues.clear();
          for (Field f : Catalog.editable(kind)) {
            Object v = Catalog.read(e, f.getName());
            if (f.getType() == LocalDateTime.class) dateValues.put(f.getName(), (LocalDateTime) v);
            else if (isNumber(f.getType())) numberValues.put(f.getName(), (Number) v);
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
    dependents = 0;
    mode = "delete";
    if (selectedId != null)
      Messages.safely(() -> dependents = service.countDependents(kind, selectedId));
  }

  public void prepareDeleteRow(String id) {
    open(id);
    prepareDelete();
  }

  public void save() {
    if (Messages.safely(
        () -> {
          for (Field f : Catalog.editable(kind)) {
            Class<?> t = f.getType();
            if (t == LocalDateTime.class)
              values.put(
                  f.getName(),
                  dateValues.get(f.getName()) == null
                      ? ""
                      : dateValues.get(f.getName()).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            else if (isNumber(t))
              values.put(f.getName(), numberToText(t, numberValues.get(f.getName())));
          }
          selectedId = service.save(kind, selectedId, version, values);
          open(selectedId.toString());
          refresh();
          Messages.info("Объект сохранён");
        })) changes.fire();
  }

  public void delete() {
    if (Messages.safely(
        () -> {
          int cascaded = service.delete(kind, selectedId, replacement, version);
          selectedId = null;
          lookupId = null;
          detail.clear();
          dependents = 0;
          mode = "view";
          refresh();
          Messages.info(
              replacement != null
                  ? "Объект удалён, ссылки перенесены"
                  : cascaded == 0
                      ? "Объект удалён"
                      : "Объект удалён вместе со связанными записями: " + cascaded);
        })) changes.fire();
  }

  /** Предел типа float4 (колонка real) в виде обычного десятичного числа. */
  private static final String FLOAT_LIMIT = "340282350000000000000000000000000000000";

  static boolean isNumber(Class<?> t) {
    return t == int.class
        || t == Integer.class
        || t == long.class
        || t == Long.class
        || t == Float.class;
  }

  private static Number defaultNumber(Field field) {
    Class<?> t = field.getType();
    boolean positive = field.getAnnotation(jakarta.validation.constraints.Positive.class) != null;
    if (positive) {
      if (t == long.class || t == Long.class) return 1L;
      if (t == Float.class) return 1f;
      return 1;
    }
    if (t == int.class) return 0;
    if (t == long.class) return 0L;
    if (t == Float.class) return 0f;
    return null;
  }

  private static String numberToText(Class<?> t, Object value) {
    if (value == null) return "";
    if (value instanceof String text) return text.trim();
    Number number = (Number) value;
    if (t == int.class || t == Integer.class) return String.valueOf(number.intValue());
    if (t == long.class || t == Long.class) return String.valueOf(number.longValue());
    return String.valueOf(number.floatValue());
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

    public boolean isNumber() {
      return Workspace.isNumber(field.getType());
    }

    public boolean isRequired() {
      return field.getType().isPrimitive()
          || field.getAnnotation(jakarta.validation.constraints.NotNull.class) != null
          || field.getAnnotation(jakarta.validation.constraints.NotBlank.class) != null;
    }

    /** Нижняя граница ввода: ограничение поля или минимум его типа. */
    public String getMinValue() {
      if (field.getAnnotation(jakarta.validation.constraints.Positive.class) != null) return "1";
      Class<?> t = field.getType();
      if (t == int.class || t == Integer.class) return "-2147483648";
      if (t == long.class || t == Long.class) return "-9223372036854775808";
      return "-" + FLOAT_LIMIT;
    }

    /** Верхняя граница ввода: максимум типа, дальше значение не поместится в колонку. */
    public String getMaxValue() {
      Class<?> t = field.getType();
      if (t == int.class || t == Integer.class) return "2147483647";
      if (t == long.class || t == Long.class) return "9223372036854775807";
      return FLOAT_LIMIT;
    }

    public String getDecimalPlaces() {
      return field.getType() == Float.class ? "4" : "0";
    }

    public Map<String, String> getOptions() {
      Map<String, String> options = new LinkedHashMap<>();
      if (!isRequired()) options.put("— не задано —", "");
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
        hint += " - обязательно для заполнения";
      if (field.getAnnotation(jakarta.validation.constraints.Positive.class) != null)
        hint += " - значение должно быть больше 0";
      if (field.getType() == String.class) hint += " - не более " + getMaxlength() + " символов";
      return hint;
    }

    /** Сколько символов физически можно набрать: предел из @Size, колонки или диапазона типа. */
    public int getMaxlength() {
      Class<?> t = field.getType();
      if (t == String.class) {
        var size = field.getAnnotation(jakarta.validation.constraints.Size.class);
        if (size != null && size.max() < Integer.MAX_VALUE) return size.max();
        var column = field.getAnnotation(jakarta.persistence.Column.class);
        return column != null && column.length() > 0 ? column.length() : 255;
      }
      if (t == int.class || t == Integer.class) return 11; // -2147483648
      if (t == long.class || t == Long.class) return 20; // -9223372036854775808
      if (t == Float.class) return 40;
      return 255;
    }
  }

  public List<String> getKinds() {
    return new ArrayList<>(Catalog.TYPES.keySet());
  }

  public List<String> getColumns() {
    return Catalog.columns(kind);
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

  public boolean isDescending() {
    return descending;
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

  public String getMode() {
    return mode;
  }

  public Integer getSelectedId() {
    return selectedId;
  }

  public int getDependents() {
    return dependents;
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

  public Map<String, LocalDateTime> getDateValues() {
    return dateValues;
  }

  public Map<String, Object> getNumberValues() {
    return numberValues;
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
