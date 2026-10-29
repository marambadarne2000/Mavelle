package com.maramigo.accounts;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;

/** Disabled employees lose existing-session access on their next request. @author Maram Abbas */
final class ActiveAccountFilter extends OncePerRequestFilter {
 private final UserRepository users;
 ActiveAccountFilter(UserRepository users){this.users=users;}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  var authentication=SecurityContextHolder.getContext().getAuthentication();
  if(authentication!=null && authentication.getPrincipal() instanceof AccountService.Principal actor && !users.existsByIdAndEnabledTrue(actor.id())){
   SecurityContextHolder.clearContext();var session=request.getSession(false);
   if(session!=null){try{session.invalidate();}catch(IllegalStateException alreadyInvalidated){/* Another concurrent request already invalidated this session. */}}
   response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"detail\":\"Please sign in to continue.\"}");return;
  }
  chain.doFilter(request,response);
 }
}
