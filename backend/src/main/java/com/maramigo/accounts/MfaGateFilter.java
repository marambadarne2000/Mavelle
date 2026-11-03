package com.maramigo.accounts;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;

/** A password-only owner session cannot access any protected API when MFA is enabled. @author Maram Abbas */
final class MfaGateFilter extends OncePerRequestFilter {
 private final MfaService mfa;MfaGateFilter(MfaService mfa){this.mfa=mfa;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  var auth=SecurityContextHolder.getContext().getAuthentication();var session=req.getSession(false);String path=req.getServletPath();
  if(auth!=null&&auth.getPrincipal() instanceof AccountService.Principal user&&user.role().equals("ADMIN")&&mfa.enabled(user.id())&&(session==null||!Boolean.TRUE.equals(session.getAttribute("MFA_VERIFIED")))){
   boolean allowed=java.util.Set.of("/api/auth/mfa/verify","/api/auth/csrf","/api/auth/options","/api/auth/logout","/actuator/health","/api/companies","/api/business-plan","/api/discovery/announcements").contains(path)||("GET".equals(req.getMethod())&&(path.equals("/api/products")||path.startsWith("/api/products/")||path.matches("/api/media/[a-f0-9-]{36}")));
   if(!allowed){res.setStatus(path.equals("/api/auth/me")?401:403);res.setContentType("application/json");res.getWriter().write("{\"detail\":\"Complete two-step verification to continue.\"}");return;}
  }chain.doFilter(req,res);
 }
}
