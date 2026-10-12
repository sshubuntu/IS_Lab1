package ru.itmo.labwork.web;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import ru.itmo.labwork.repository.Database;
import ru.itmo.labwork.repository.UserStore;

@Named
@SessionScoped
public class Login implements Serializable {
  @Inject private UserStore users;
  private String username = "", password = "";
  private boolean authenticated;

  public String submit() {
    String expected = Database.env("APP_PASSWORD", "");
    authenticated =
        !expected.isBlank()
            && equal(username, Database.env("APP_USER", "student"))
            && equal(password, expected);
    authenticated = authenticated || users.matches(username, password);
    password = "";
    if (authenticated) {
      ((jakarta.servlet.http.HttpServletRequest)
              FacesContext.getCurrentInstance().getExternalContext().getRequest())
          .changeSessionId();
      return "/index.xhtml?faces-redirect=true";
    }
    FacesContext.getCurrentInstance()
        .addMessage(
            null,
            new FacesMessage(FacesMessage.SEVERITY_ERROR, "Неверный логин или пароль.", null));
    return null;
  }

  private boolean equal(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  public String logout() {
    FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
    return "/login.xhtml?faces-redirect=true";
  }

  public boolean isAuthenticated() {
    return authenticated;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String v) {
    username = v;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String v) {
    password = v;
  }
}
