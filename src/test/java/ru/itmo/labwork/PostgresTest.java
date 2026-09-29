package ru.itmo.labwork;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.*;
import jakarta.validation.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ru.itmo.labwork.domain.*;
import ru.itmo.labwork.repository.Database;
import ru.itmo.labwork.service.*;

@EnabledIfEnvironmentVariable(named = "LAB_TEST_DB", matches = "true")
class PostgresTest {
  static EntityManager em;
  static LabService service;
  static LabService txService;
  static ValidatorFactory validation;

  @BeforeAll
  static void setup() throws Exception {
    if (!Database.env("DB_NAME", "").endsWith("_test"))
      throw new IllegalStateException("Tests require DB_NAME ending _test");
    String url =
        Database.env(
            "DB_URL",
            "jdbc:postgresql://"
                + Database.env("DB_HOST", "pg")
                + ":5432/"
                + Database.env("DB_NAME", "studs"));
    String user = Database.env("DB_USER", "studs"), password = Database.env("DB_PASSWORD", "");
    EntityManagerFactory factory =
        Persistence.createEntityManagerFactory(
            "labwork",
            Map.of(
                "jakarta.persistence.jdbc.url", url,
                "jakarta.persistence.jdbc.user", user,
                "jakarta.persistence.jdbc.password", password,
                "jakarta.persistence.jdbc.driver", "org.postgresql.Driver"));
    em = factory.createEntityManager();
    String actual =
        (String)
            tx(
                em,
                () -> em.createNativeQuery("select current_database()").getSingleResult());
    if (!actual.equals(Database.env("DB_NAME", "")) || !actual.endsWith("_test"))
      throw new IllegalStateException("DB_URL must target the named test database");
    service = new LabService();
    txService = new TxLabService();
    validation = Validation.buildDefaultValidatorFactory();
    inject("em", em);
    inject("validator", validation.getValidator());
  }

  static void inject(String name, Object value) throws Exception {
    for (LabService s : new LabService[] {service, txService}) {
      var f = LabService.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(s, value);
    }
  }

  @AfterAll
  static void finish() {
    if (em != null) em.close();
    if (validation != null) validation.close();
  }

  @BeforeEach
  void clear() {
    tx(
        em,
        () -> {
          em.createNativeQuery(
                  "truncate lab_work,discipline_program,person,discipline,location,coordinates"
                      + " restart identity cascade")
              .executeUpdate();
          return null;
        });
  }

  static Object tx(EntityManager em, Callable<Object> work) {
    em.getTransaction().begin();
    try {
      Object result = work.call();
      em.getTransaction().commit();
      return result;
    } catch (Exception e) {
      if (em.getTransaction().isActive()) em.getTransaction().rollback();
      throw e instanceof RuntimeException r ? r : new RuntimeException(e);
    }
  }

  /** @Transactional is a container interceptor; in standalone tests wrap each call manually. */
  static class TxLabService extends LabService {
    <T> T run(Callable<T> work) {
      return (T)
          tx(
              em,
              () -> {
                try {
                  return work.call();
                } catch (Exception e) {
                  throw e;
                }
              });
    }

    @Override
    public Page page(String kind, Map<String, String> filters, String sort, boolean desc, int page) {
      return run(() -> super.page(kind, filters, sort, desc, page));
    }

    @Override
    public RecordEntity get(String kind, int id) {
      return run(() -> super.get(kind, id));
    }

    @Override
    public List<RecordEntity> all(String kind) {
      return run(() -> super.all(kind));
    }

    @Override
    public int save(String kind, Integer id, long version, Map<String, String> values) {
      return run(() -> super.save(kind, id, version, values));
    }

    @Override
    public void delete(String kind, int id, Integer replacement, long expectedVersion) {
      run(() -> {
        super.delete(kind, id, replacement, expectedVersion);
        return null;
      });
    }

    @Override
    public Object special(String operation, String arg, String extra) {
      return run(() -> super.special(operation, arg, extra));
    }

    @Override
    public List<Map<String, String>> program(int id) {
      return run(() -> super.program(id));
    }
  }

  final LabService svc = txService;

  int coordinates() {
    return svc.save("Coordinates", null, 0, Map.of("x", "10", "y", "20"));
  }

  int discipline(String name) {
    return svc.save(
        "Discipline",
        null,
        0,
        Map.of("name", name, "practiceHours", "0", "selfStudyHours", "0", "labsCount", "0"));
  }

  Map<String, String> lab(String name, int c) {
    return new HashMap<>(
        Map.of(
            "name",
            name,
            "coordinates",
            "" + c,
            "description",
            "",
            "difficulty",
            "HARD",
            "minimalPoint",
            "10",
            "personalQualitiesMaximum",
            "20"));
  }

  int create(String name, int c) {
    return svc.save("LabWork", null, 0, lab(name, c));
  }

  @Test
  void crudAndGeneratedFields() {
    int id = create("Test", coordinates());
    var e = (LabWork) svc.get("LabWork", id);
    assertTrue(id > 0);
    assertNotNull(e.getCreationDate());
    var data = lab("Changed", e.getCoordinates().getId());
    svc.save("LabWork", id, e.getVersion(), data);
    assertEquals("Changed", ((LabWork) svc.get("LabWork", id)).getName());
    assertEquals(e.getCreationDate(), ((LabWork) svc.get("LabWork", id)).getCreationDate());
    svc.delete("LabWork", id, null, svc.get("LabWork", id).getVersion());
    assertThrows(IllegalArgumentException.class, () -> svc.get("LabWork", id));
  }

  @Test
  void validationRollsBack() {
    int c = coordinates();
    var d = lab(" ", c);
    assertThrows(IllegalArgumentException.class, () -> svc.save("LabWork", null, 0, d));
    assertEquals(0, svc.page("LabWork", Map.of(), "id", false, 0).total());
  }

  @Test
  void databaseConstraints() {
    assertThrows(
        RuntimeException.class,
        () ->
            tx(
                em,
                () -> {
                  em.createNativeQuery("insert into coordinates(x,y) values(1,null)")
                      .executeUpdate();
                  return null;
                }));
  }

  @Test
  void prefixIsLiteral() {
    int c = coordinates();
    create("a%b", c);
    create("abc", c);
    assertEquals(1, ((List<?>) svc.special("prefix", "a%", "")).size());
  }

  @Test
  void lowerClampsAndRejectsNegative() {
    int id = create("X", coordinates());
    assertEquals("EASY", svc.special("lower", "" + id, "99"));
    assertThrows(RuntimeException.class, () -> svc.special("lower", "" + id, "-1"));
  }

  @Test
  void deleteOnlyOneByPoint() {
    int c = coordinates();
    create("A", c);
    create("B", c);
    assertNotNull(svc.special("point", "10", ""));
    assertEquals(1, svc.all("LabWork").size());
  }

  @Test
  void deleteNullAuthor() {
    int c = coordinates();
    create("A", c);
    create("B", c);
    assertEquals(2, ((Number) svc.special("author", "", "")).intValue());
    assertTrue(svc.all("LabWork").isEmpty());
  }

  @Test
  void topTenPreservesSourceAndIsIdempotent() {
    int c = coordinates(), d = discipline("Target"), source = discipline("Source");
    for (int i = 0; i < 12; i++) {
      var data = lab("Work " + i, c);
      data.put("discipline", "" + source);
      data.put("difficulty", i < 2 ? "EASY" : "TERRIBLE");
      svc.save("LabWork", null, 0, data);
    }
    assertEquals(10, ((Number) svc.special("top", "" + d, "")).intValue());
    assertEquals(0, ((Number) svc.special("top", "" + d, "")).intValue());
    assertEquals(10, svc.program(d).size());
    assertTrue(
        svc.program(d).stream()
            .allMatch(
                r ->
                    r.get("difficulty").equals("TERRIBLE")
                        && r.get("discipline.id").equals("" + source)));
  }

  @Test
  void replacementIsRequiredAndAtomic() {
    int old = coordinates(), next = coordinates(), id = create("X", old);
    assertThrows(IllegalArgumentException.class, () -> svc.delete("Coordinates", old, null, 0));
    assertEquals(old, ((LabWork) svc.get("LabWork", id)).getCoordinates().getId());
    svc.delete("Coordinates", old, next, 0);
    assertEquals(next, ((LabWork) svc.get("LabWork", id)).getCoordinates().getId());
  }

  @Test
  void detectsStaleUpdate() {
    int c = coordinates(), id = create("X", c);
    svc.save("LabWork", id, 0, lab("Y", c));
    assertThrows(IllegalArgumentException.class, () -> svc.save("LabWork", id, 0, lab("Z", c)));
  }

  @Test
  void filtersAndPaginationKeepNullRelations() {
    int c = coordinates();
    for (int i = 0; i < 25; i++) create("Work " + i, c);
    assertEquals(25, svc.page("LabWork", Map.of(), "author.name", false, 0).total());
    assertEquals(5, svc.page("LabWork", Map.of(), "id", false, 1).rows().size());
    assertEquals(11, svc.page("LabWork", Map.of("name", "k 1"), "name", false, 0).total());
  }

  @Test
  void authorDeletionUsesIdentity() {
    int c = coordinates();
    int location = svc.save("Location", null, 0, Map.of("x", "1", "y", "2"));
    int author =
        svc.save(
            "Person",
            null,
            0,
            Map.of("name", "A", "location", "" + location, "nationality", "FRANCE"));
    var data = lab("With author", c);
    data.put("author", "" + author);
    svc.save("LabWork", null, 0, data);
    create("Without author", c);
    assertEquals(1, ((Number) svc.special("author", "" + author, "")).intValue());
    assertEquals("Without author", ((LabWork) svc.all("LabWork").get(0)).getName());
  }

  @Test
  void disciplineReplacementMergesProgramsAndReferences() {
    int c = coordinates(), old = discipline("Old"), next = discipline("Next");
    var data = lab("Program work", c);
    data.put("discipline", "" + old);
    int work = svc.save("LabWork", null, 0, data);
    svc.special("top", "" + old, "");
    svc.special("top", "" + next, "");
    svc.delete("Discipline", old, next, 0);
    assertEquals(next, ((LabWork) svc.get("LabWork", work)).getDiscipline().getId());
    assertEquals(1, svc.program(next).size());
    assertThrows(IllegalArgumentException.class, () -> svc.get("Discipline", old));
  }
}