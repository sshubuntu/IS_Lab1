package ru.itmo.labwork.web;

import jakarta.inject.Inject;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;

@WebFilter("/*")
public class AuthFilter implements Filter {
  @Inject private Login login;

  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse res = (HttpServletResponse) response;
    res.setHeader("X-Content-Type-Options", "nosniff");
    res.setHeader("X-Frame-Options", "DENY");
    String path = req.getRequestURI().substring(req.getContextPath().length());
    // Рукопожатие websocket нельзя перенаправлять: Faces сам сверяет токен канала с сессией.
    if (path.equals("/login.xhtml")
        || path.equals("/register.xhtml")
        || path.startsWith("/jakarta.faces.resource/")
        || path.startsWith("/jakarta.faces.push/")) {
      chain.doFilter(request, response);
      return;
    }
    res.setHeader("Cache-Control", "no-store");
    if (!login.isAuthenticated()) {
      String target = req.getContextPath() + "/login.xhtml";
      if ("partial/ajax".equals(req.getHeader("Faces-Request"))) {
        res.setContentType("text/xml;charset=UTF-8");
        res.getWriter()
            .write(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><partial-response><redirect url=\""
                    + target
                    + "\"/></partial-response>");
      } else res.sendRedirect(target);
      return;
    }
    chain.doFilter(request, response);
  }
}
