package com.maramigo.accounts;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
/** Empty Tomcat proxy patterns trust every peer; reject that accidental configuration. @author Maram Abbas */
@Configuration
public class ProxyTrustConfiguration {
 static void validate(String pattern){
  if(pattern==null||pattern.isBlank())throw new IllegalStateException("TRUSTED_PROXY_REGEX must name trusted proxy peers explicitly, or use (?!) to trust none.");
  java.util.regex.Pattern.compile(pattern);
 }
 @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> proxyTrustGuard(@Value("${server.tomcat.remoteip.internal-proxies:(?!)}") String pattern){
  validate(pattern);return factory->{/* Spring Boot configures the native RemoteIpValve from the validated properties. */};
 }
}
