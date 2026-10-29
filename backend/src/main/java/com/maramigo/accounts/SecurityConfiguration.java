package com.maramigo.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import java.time.Clock;
import jakarta.servlet.DispatcherType;

/** @author Maram Abbas */
@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper json, UserRepository users,
            @Value("${maramigo.login-limit:10}") int limit) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**", "/api/companies", "/actuator/health", "/api/auth/csrf").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/staff-register").permitAll()
            .requestMatchers("/api/staff/**").hasAnyRole("ADMIN","EMPLOYEE")
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/orders", "/api/orders/**").hasAnyRole("CUSTOMER","ADMIN")
            .anyRequest().authenticated())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(cache -> cache.disable())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"detail\":\"Please sign in to continue.\"}");
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403); response.setContentType("application/json");
                    response.getWriter().write("{\"detail\":\"This action is not allowed or the security token has expired.\"}");
                }))
            .formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((request, response, authentication) -> {
                    var user=(AccountService.Principal)authentication.getPrincipal();
                    String portal=request.getParameter("portal");
                    String expected=switch(portal==null?"":portal){case "customer"->"CUSTOMER";case "owner"->"ADMIN";case "staff"->"EMPLOYEE";case ""->user.role();default->"INVALID";};
                    if(!expected.equals(user.role())){
                        org.springframework.security.core.context.SecurityContextHolder.clearContext();
                        var session=request.getSession(false);if(session!=null){try{session.invalidate();}catch(IllegalStateException alreadyInvalidated){/* Concurrent logout already cleared the session. */}}
                        response.setStatus(403);response.setContentType("application/json");response.getWriter().write("{\"detail\":\"This account cannot use this sign-in area.\"}");return;
                    }
                    response.setContentType("application/json");
                    json.writeValue(response.getWriter(), ((AccountService.Principal)authentication.getPrincipal()).view());
                })
                .failureHandler((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json");
                    response.getWriter().write("{\"detail\":\"Invalid email or password.\"}");
                }))
            .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
            .addFilterBefore(new LoginRateLimiter(Clock.systemUTC(), limit), UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new ActiveAccountFilter(users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
