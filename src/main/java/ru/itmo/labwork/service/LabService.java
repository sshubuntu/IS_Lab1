package ru.itmo.labwork.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.validation.*;
import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.util.*;
import ru.itmo.labwork.domain.*;

@ApplicationScoped
public class LabService {
  @PersistenceContext(unitName = "labwork")
  private EntityManager em;

  @Inject private Validator validator;

  public record Page(List<Map<String, String>> rows, long total) {}

  @Transactional
  public Page page(String kind, Map<String, String> filters, String sort, boolean desc, int page) {
    Class<?> type = Catalog.type(kind);
    List<String> allowed = Catalog.ownColumns(kind);
    if (!allowed.contains(sort)) throw new IllegalArgumentException("Недопустимая сортировка");
    String from = " from " + type.getSimpleName() + " e";
    // LEFT JOIN is required: optional author/discipline must not hide rows.
    Map<String, String> paths = new HashMap<>();
    StringBuilder joins = new StringBuilder();
    for (String path : allowed) {
      String[] parts = path.split("\\.");
      String parent = "e", prefix = "";
      for (int i = 0; i < parts.length - 1; i++) {
        prefix += parts[i] + ".";
        if (!paths.containsKey(prefix)) {
          String alias = "j" + paths.size();
          paths.put(prefix, alias);
          joins
              .append(" left join ")
              .append(parent)
              .append('.')
              .append(parts[i])
              .append(' ')
              .append(alias);
        }
        parent = paths.get(prefix);
      }
      paths.put(path, parent + "." + parts[parts.length - 1]);
    }
    StringBuilder where = new StringBuilder(" where 1=1");
    Map<String, Object> params = new HashMap<>();
    for (String col : allowed) {
      String value = filters.get(col);
      if (value != null && !value.isEmpty()) {
        String param = "p" + params.size();
        where
            .append(" and locate(lower(:")
            .append(param)
            .append("), lower(cast(")
            .append(paths.get(col))
            .append(" as string))) > 0");
        params.put(param, value);
      }
    }
    String base = from + joins + where;
    var count = em.createQuery("select count(e)" + base, Long.class);
    params.forEach(count::setParameter);
    long total = count.getSingleResult();
    int last = (int) Math.max(0, (total - 1) / 20), actual = Math.min(Math.max(0, page), last);
    var query =
        em.createQuery(
            "select e"
                + base
                + " order by "
                + paths.get(sort)
                + (desc ? " desc" : " asc")
                + ",e.id",
            RecordEntity.class);
    params.forEach(query::setParameter);
    List<Map<String, String>> rows =
        query.setFirstResult(actual * 20).setMaxResults(20).getResultList().stream()
            .map(this::row)
            .toList();
    return new Page(rows, total);
  }

  public Map<String, String> row(RecordEntity e) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String p : Catalog.columns(e.getClass().getSimpleName()))
      result.put(p, Catalog.text(Catalog.read(e, p)));
    return result;
  }

  @Transactional
  public RecordEntity get(String kind, int id) {
    var e = em.find(Catalog.type(kind), id);
    if (e == null) throw new IllegalArgumentException("Объект #" + id + " не найден");
    return e;
  }

  @Transactional
  public List<RecordEntity> all(String kind) {
    return em.createQuery(
            "from " + Catalog.type(kind).getSimpleName() + " order by id", RecordEntity.class)
        .getResultList();
  }

  @Transactional
  public int save(String kind, Integer id, long version, Map<String, String> values) {
    try {
      RecordEntity e =
          id == null
              ? Catalog.type(kind).getDeclaredConstructor().newInstance()
              : em.find(Catalog.type(kind), id, LockModeType.PESSIMISTIC_WRITE);
      if (e == null) throw new IllegalArgumentException("Объект уже удалён");
      if (id != null && e.getVersion() != version)
        throw new IllegalArgumentException(
            "Объект изменён другим пользователем. Откройте его заново.");
      for (Field field : Catalog.editable(kind)) {
        Object v = parse(field, values.get(field.getName()));
        field.setAccessible(true);
        field.set(e, v);
      }
      var errors = validator.validate(e);
      if (!errors.isEmpty())
        throw new IllegalArgumentException(
            errors.stream()
                .map(jakarta.validation.ConstraintViolation::getMessage)
                .sorted()
                .reduce((a, b) -> a + "; " + b)
                .orElse(""));
      if (id == null) em.persist(e);
      em.flush();
      return e.getId();
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  /** Преобразует введённое пользователем значение в тип поля, поясняя ошибку по-русски. */
  private Object parse(Field field, String raw) {
    String label = Catalog.label(field.getName());
    Class<?> t = field.getType();
    if (t == String.class) return raw == null ? "" : raw;
    if (raw == null || raw.isBlank()) {
      if (t.isPrimitive()) throw new IllegalArgumentException(label + ": поле обязательно для заполнения");
      return null;
    }
    String value = raw.trim();
    if (t == int.class || t == Integer.class) return parseInteger(label, value);
    if (t == long.class || t == Long.class) return parseLong(label, value);
    if (t == Float.class) {
      try {
        Float f = Float.valueOf(value);
        if (!Float.isFinite(f))
          throw new IllegalArgumentException(
              label + ": значение должно быть конечным числом (не NaN и не Infinity)");
        return f;
      } catch (NumberFormatException error) {
        throw new IllegalArgumentException(label + ": значение должно быть числом");
      }
    }
    if (t == LocalDateTime.class) {
      try {
        return LocalDateTime.parse(value);
      } catch (java.time.format.DateTimeParseException error) {
        throw new IllegalArgumentException(
            label + ": неверный формат даты, ожидается дд.ММ.гггг чч:мм");
      }
    }
    if (t.isEnum()) {
      try {
        return Enum.valueOf((Class) t, value);
      } catch (IllegalArgumentException error) {
        throw new IllegalArgumentException(label + ": выбрано недопустимое значение");
      }
    }
    int refId;
    try {
      refId = Integer.parseInt(value);
    } catch (NumberFormatException error) {
      throw new IllegalArgumentException(label + ": идентификатор должен быть целым числом");
    }
    Object ref = em.find(t, refId);
    if (ref == null) throw new IllegalArgumentException(label + ": связанная запись не найдена");
    return ref;
  }

  private static int parseInteger(String label, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException error) {
      if (value.matches("[+-]?\\d+"))
        throw new IllegalArgumentException(
            label + ": число слишком большое (допустимо от -2147483648 до 2147483647)");
      throw new IllegalArgumentException(label + ": должно быть целым числом");
    }
  }

  private static long parseLong(String label, String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException error) {
      if (value.matches("[+-]?\\d+"))
        throw new IllegalArgumentException(
            label + ": число слишком большое (допустимо от -9223372036854775808 до 9223372036854775807)");
      throw new IllegalArgumentException(label + ": должно быть целым числом");
    }
  }

  /**
   * Удаляет объект. Если выбрана замена, ссылающиеся записи переносятся на неё; если замена не
   * выбрана, они удаляются каскадом вместе со своими зависимостями.
   *
   * @return число удалённых каскадом связанных записей
   */
  @Transactional
  public int delete(String kind, int id, Integer replacement, long expectedVersion) {
    Class<? extends RecordEntity> type = Catalog.type(kind);
    var old = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    if (old == null) throw new IllegalArgumentException("Объект уже удалён");
    if (old.getVersion() != expectedVersion)
      throw new IllegalArgumentException("Объект изменён; откройте удаление заново");
    if (replacement != null
        && (replacement == id
            || em.find(type, replacement, LockModeType.PESSIMISTIC_WRITE) == null))
      throw new IllegalArgumentException("Выберите другой существующий объект");
    int cascaded =
        replacement == null ? cascade(kind, id, new HashSet<>()) : reassign(kind, id, replacement);
    em.remove(old);
    em.flush();
    // Массовые операции идут мимо кэша контекста; не отдаём устаревшие связи.
    em.clear();
    return cascaded;
  }

  /** Переносит все ссылки на объект на другой объект того же типа. */
  private int reassign(String kind, int id, int replacement) {
    Class<? extends RecordEntity> type = Catalog.type(kind);
    forEachReferencingField(
        type,
        (owner, field) ->
            em.createQuery(
                    "update "
                        + owner
                        + " e set e."
                        + field.getName()
                        + "=:next,e.version=e.version+1 where e."
                        + field.getName()
                        + ".id=:id")
                .setParameter("next", em.getReference(type, replacement))
                .setParameter("id", id)
                .executeUpdate());
    return 0;
  }

  /** Удаляет записи, ссылающиеся на объект, вместе с их собственными зависимостями. */
  private int cascade(String kind, int id, Set<String> visited) {
    Class<? extends RecordEntity> type = Catalog.type(kind);
    int[] removed = {0};
    forEachReferencingField(
        type,
        (owner, field) -> {
          for (RecordEntity dependent : referencing(owner, field.getName(), id)) {
            if (!visited.add(owner + "#" + dependent.getId())) continue;
            removed[0] += 1 + cascade(owner, dependent.getId(), visited);
            em.remove(dependent);
            // Потомки должны исчезнуть раньше родителя, иначе сработает внешний ключ.
            em.flush();
          }
          return 0;
        });
    return removed[0];
  }

  /** Сколько записей будет удалено каскадом вместе с объектом. */
  @Transactional
  public int countDependents(String kind, int id) {
    return cascadeSize(kind, id, new HashSet<>());
  }

  private int cascadeSize(String kind, int id, Set<String> visited) {
    int[] total = {0};
    forEachReferencingField(
        Catalog.type(kind),
        (owner, field) -> {
          for (RecordEntity dependent : referencing(owner, field.getName(), id))
            if (visited.add(owner + "#" + dependent.getId()))
              total[0] += 1 + cascadeSize(owner, dependent.getId(), visited);
          return 0;
        });
    return total[0];
  }

  private List<RecordEntity> referencing(String owner, String field, int id) {
    return em.createQuery("from " + owner + " e where e." + field + ".id=:id", RecordEntity.class)
        .setParameter("id", id)
        .getResultList();
  }

  /** Обходит все поля каталога, ссылающиеся на заданный тип. */
  private void forEachReferencingField(
      Class<? extends RecordEntity> type,
      java.util.function.BiFunction<String, Field, Integer> action) {
    for (var entry : Catalog.TYPES.entrySet())
      for (Field field : Catalog.editable(entry.getKey()))
        if (field.getType() == type) action.apply(entry.getKey(), field);
  }

  @Transactional
  public Object special(String operation, String arg, String extra) {
    return switch (operation) {
      case "prefix" -> findByNamePrefix(arg);
      case "author", "point", "top" -> callSpecialFunction(operation, arg, null);
      case "lower" -> callSpecialFunction(operation, arg, extra);
      default -> throw new IllegalArgumentException("Неизвестная операция");
    };
  }

  private List<Map<String, String>> findByNamePrefix(String prefix) {
    return em
        .createNativeQuery(
            "select * from find_labworks_by_name_prefix(cast(:arg as text))", LabWork.class)
        .setParameter("arg", prefix == null ? "" : prefix)
        .getResultList()
        .stream()
        .map(x -> row((RecordEntity) x))
        .toList();
  }

  private Object callSpecialFunction(String operation, String arg, String extra) {
    String function =
        switch (operation) {
          case "author" -> "delete_labworks_by_author";
          case "point" -> "delete_one_labwork_by_minimal_point";
          case "lower" -> "lower_labwork_difficulty";
          case "top" -> "assign_top_ten_hardest_to_discipline";
          default -> throw new IllegalArgumentException("Неизвестная операция");
        };
    Integer value = arg == null || arg.isBlank() ? null : Integer.valueOf(arg);
    String sql =
        "select "
            + function
            + "(cast(:arg as integer)"
            + (operation.equals("lower") ? ",cast(:steps as integer)" : "")
            + ")";
    var query = em.createNativeQuery(sql).setParameter("arg", value);
    if (operation.equals("lower")) query.setParameter("steps", Integer.valueOf(extra));
    Object result = query.getSingleResult();
    // Функция меняет строки напрямую, минуя кэш контекста; не отдаём устаревшие сущности.
    em.flush();
    em.clear();
    return result;
  }

  /** Работы указанного автора; null означает работы без автора. */
  @Transactional
  public List<Map<String, String>> byAuthor(Integer authorId) {
    var query =
        authorId == null
            ? em.createQuery("from LabWork e where e.author is null order by e.id", LabWork.class)
            : em.createQuery("from LabWork e where e.author.id=:id order by e.id", LabWork.class)
                .setParameter("id", authorId);
    return query.getResultList().stream().map(this::row).toList();
  }

  /** Работы с точно заданным минимальным баллом. */
  @Transactional
  public List<Map<String, String>> byMinimalPoint(int minimalPoint) {
    return em
        .createQuery("from LabWork e where e.minimalPoint=:point order by e.id", LabWork.class)
        .setParameter("point", minimalPoint)
        .getResultList()
        .stream()
        .map(this::row)
        .toList();
  }

  /** Программа дисциплины — работы, привязанные к ней полем discipline. */
  @Transactional
  public List<Map<String, String>> program(int id) {
    return em
        .createQuery("from LabWork e where e.discipline.id=:id order by e.id", LabWork.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .map(this::row)
        .toList();
  }
}
