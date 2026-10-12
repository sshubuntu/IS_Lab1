package ru.itmo.labwork.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

/** Единая обработка ошибок и уведомлений для страниц. */
public final class Messages {
  private static final System.Logger LOG = System.getLogger(Messages.class.getName());

  private Messages() {}

  /** Выполняет действие, показывая пользователю понятное сообщение об ошибке. */
  public static boolean safely(Runnable action) {
    try {
      action.run();
      return true;
    } catch (Exception e) {
      LOG.log(System.Logger.Level.ERROR, "Ошибка при выполнении операции", e);
      FacesContext.getCurrentInstance().validationFailed();
      error(describe(e));
      return false;
    }
  }

  public static String describe(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof IllegalArgumentException) return t.getMessage();
      if (t instanceof jakarta.validation.ConstraintViolationException validation
          && !validation.getConstraintViolations().isEmpty())
        return validation.getConstraintViolations().stream()
            .map(jakarta.validation.ConstraintViolation::getMessage)
            .sorted()
            .reduce((a, b) -> a + "; " + b)
            .orElse("Введённые данные не прошли проверку");
    }
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof org.postgresql.util.PSQLException p) {
        return describePostgres(p);
      }
      if (t instanceof java.sql.SQLException sql) {
        for (java.sql.SQLException next = sql.getNextException();
            next != null;
            next = next.getNextException())
          if (next instanceof org.postgresql.util.PSQLException p) return describePostgres(p);
      }
    }
    return "Операция не выполнена. Проверьте правильность введённых данных и попробуйте ещё раз.";
  }

  private static String describePostgres(org.postgresql.util.PSQLException p) {
    return switch (p.getSQLState()) {
      case "23503" ->
          "Связанный объект уже используется или удалён. Обновите данные и выберите подходящую"
              + " замену.";
      case "23505" -> "Нарушено ограничение уникальности. Такое значение уже существует.";
      case "23514", "23502", "22001", "22003" ->
          "Введённое значение не соответствует ограничениям поля. Проверьте обязательные поля,"
              + " максимальную длину и допустимый диапазон чисел.";
      case "P0001" -> {
        var msg = p.getServerErrorMessage();
        yield msg != null && msg.getMessage() != null
            ? msg.getMessage()
            : "Ошибка при выполнении операции.";
      }
      default ->
          "Не удалось выполнить операцию в базе данных. Обновите страницу и повторите попытку.";
    };
  }

  public static void info(String text) {
    add(FacesMessage.SEVERITY_INFO, text);
  }

  public static void error(String text) {
    add(FacesMessage.SEVERITY_ERROR, text);
  }

  private static void add(FacesMessage.Severity severity, String text) {
    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, text, null));
  }
}
