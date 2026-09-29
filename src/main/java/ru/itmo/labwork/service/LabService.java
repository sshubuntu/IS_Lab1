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
    List<String> allowed = Catalog.columns(kind);
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
    for (String col : Catalog.stringColumns(kind)) {
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
    int last = (int) Math.max(0, (total - 1) / 20),
        actual = Math.min(Math.max(0, page), last);
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
        throw new IllegalArgumentException("Объект изменён другим пользователем. Откройте его заново.");
      for (Field field : Catalog.editable(kind)) {
        String raw = values.get(field.getName());
        Class<?> t = field.getType();
        Object v;
        try {
          if (t == String.class) v = raw == null ? "" : raw;
          else if (raw == null || raw.isBlank()) {
            if (t.isPrimitive()) throw new IllegalArgumentException("обязательное число");
            v = null;
          } else if (t == int.class) v = Integer.valueOf(raw);
          else if (t == long.class || t == Long.class) v = Long.valueOf(raw);
          else if (t == Float.class) {
            v = Float.valueOf(raw);
            if (!Float.isFinite((Float) v)) throw new IllegalArgumentException("требуется конечное число");
          } else if (t == LocalDateTime.class) v = LocalDateTime.parse(raw);
          else if (t.isEnum()) v = Enum.valueOf((Class) t, raw);
          else {
            v = em.find(t, Integer.valueOf(raw));
            if (v == null) throw new IllegalArgumentException("связанный объект не найден");
          }
        } catch (RuntimeException error) {
          throw new IllegalArgumentException(field.getName() + ": " + error.getMessage());
        }
        field.setAccessible(true);
        field.set(e, v);
      }
      var errors = validator.validate(e);
      if (!errors.isEmpty())
        throw new IllegalArgumentException(
            errors.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
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

  @Transactional
  public void delete(String kind, int id, Integer replacement, long expectedVersion) {
    Class<? extends RecordEntity> type = Catalog.type(kind);
    var old = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
    if (old == null) throw new IllegalArgumentException("Объект уже удалён");
    if (old.getVersion() != expectedVersion)
      throw new IllegalArgumentException("Объект изменён; откройте удаление заново");
    if (replacement != null
        && (replacement == id || em.find(type, replacement, LockModeType.PESSIMISTIC_WRITE) == null))
      throw new IllegalArgumentException("Выберите другой существующий объект");
    for (var entry : Catalog.TYPES.entrySet())
      for (Field field : Catalog.editable(entry.getKey())) {
        if (field.getType() != type) continue;
        String base = entry.getKey() + " e";
        long count =
            em.createQuery(
                    "select count(e) from " + base + " where e." + field.getName() + ".id=:id",
                    Long.class)
                .setParameter("id", id)
                .getSingleResult();
        if (count > 0) {
          if (replacement == null)
            throw new IllegalArgumentException("Есть связанные объекты. Выберите замену.");
          em.createQuery(
                  "update "
                      + base
                      + " set e."
                      + field.getName()
                      + "=:next,e.version=e.version+1 where e."
                      + field.getName()
                      + ".id=:id")
              .setParameter("next", em.getReference(type, replacement))
              .setParameter("id", id)
              .executeUpdate();
        }
      }
    if (kind.equals("Discipline")) {
      Number count =
          (Number)
              em.createNativeQuery(
                      "select count(*) from discipline_program where discipline_id=:id")
                  .setParameter("id", id)
                  .getSingleResult();
      if (count.longValue() > 0) {
        if (replacement == null)
          throw new IllegalArgumentException("В программе есть работы. Выберите замену дисциплины.");
        em.createNativeQuery(
                "insert into discipline_program select :next,lab_work_id from"
                    + " discipline_program where discipline_id=:id on conflict do nothing")
            .setParameter("next", replacement)
            .setParameter("id", id)
            .executeUpdate();
        em.createNativeQuery("delete from discipline_program where discipline_id=:id")
            .setParameter("id", id)
            .executeUpdate();
      }
    }
    em.remove(old);
    em.flush();
  }

  @Transactional
  public Object special(String operation, String arg, String extra) {
    if (operation.equals("prefix"))
      return em
          .createNativeQuery(
              "select * from find_labworks_by_name_prefix(cast(:arg as text))", LabWork.class)
          .setParameter("arg", arg == null ? "" : arg)
          .getResultList()
          .stream()
          .map(x -> row((RecordEntity) x))
          .toList();
    String function =
        switch (operation) {
          case "author" -> "delete_labworks_by_author";
          case "point" -> "delete_one_labwork_by_minimal_point";
          case "lower" -> "lower_labwork_difficulty";
          case "top" -> "assign_top_ten_hardest_to_discipline";
          default -> throw new IllegalArgumentException("Неизвестная операция");
        };
    Integer value = arg == null || arg.isBlank() ? null : Integer.valueOf(arg);
    var q =
        em.createNativeQuery(
                "select "
                    + function
                    + "(cast(:arg as integer)"
                    + (operation.equals("lower") ? ",cast(:steps as integer)" : "")
                    + ")")
            .setParameter("arg", value);
    if (operation.equals("lower")) q.setParameter("steps", Integer.valueOf(extra));
    return q.getSingleResult();
  }

  @Transactional
  public List<Map<String, String>> program(int id) {
    return em
        .createNativeQuery(
            "select l.* from lab_work l join discipline_program p on p.lab_work_id=l.id"
                + " where p.discipline_id=:id order by l.id",
            LabWork.class)
        .setParameter("id", id)
        .getResultList()
        .stream()
        .map(x -> row((RecordEntity) x))
        .toList();
  }
}