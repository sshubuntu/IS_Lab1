package ru.itmo.labwork.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@ApplicationScoped
public class UserStore {
  @PersistenceContext(unitName = "labwork")
  private EntityManager em;

  @Transactional
  public boolean exists(String username) {
    Number count =
        (Number)
            em.createNativeQuery("select count(*) from app_users where username = :u")
                .setParameter("u", username)
                .getSingleResult();
    return count.longValue() > 0;
  }

  @Transactional
  public void register(String username, String password) {
    em.createNativeQuery("insert into app_users(username, password_hash) values (:u, :h)")
        .setParameter("u", username)
        .setParameter("h", sha256(password))
        .executeUpdate();
  }

  @Transactional
  public boolean matches(String username, String password) {
    String stored =
        (String)
            em.createNativeQuery("select password_hash from app_users where username = :u")
                .setParameter("u", username)
                .getResultStream()
                .findFirst()
                .orElse(null);
    if (stored == null) return false;
    return MessageDigest.isEqual(
        stored.getBytes(StandardCharsets.UTF_8),
        sha256(password).getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256(String value) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}