package com.manageme.ratelimitter.ratellimitter.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class RateLimitFilter implements WebFilter {

    Map<String, Bucket> rateLimitingCache = new HashMap<>();

    private Bucket createSessionRateLimitBucket() {
        var limit = Bandwidth.simple(1, Duration.ofMinutes(1));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createIpRateLimitBucket() {
        var limit = Bandwidth.simple(1, Duration.ofMinutes(1));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange serverWebExchange, WebFilterChain webFilterChain) {
        var sourceIP = "";
        if(serverWebExchange.getRequest().getHeaders().get("X-FORWARDED-FOR") != null) {
            sourceIP = serverWebExchange.getRequest().getHeaders().get("X-FORWARDED-FOR").get(0);
        }
        sourceIP = Objects.requireNonNull(serverWebExchange.getRequest().getRemoteAddress()).getAddress().getHostAddress();
        System.out.println("Processing for " + sourceIP);
        if (rateLimitingCache.containsKey(sourceIP)) {
            System.out.println("Available IP tokens left: " + rateLimitingCache.get(sourceIP).getAvailableTokens());
            if (rateLimitingCache.get(sourceIP) != null && !rateLimitingCache.get(sourceIP).tryConsume(1)) {
                serverWebExchange.getResponse().setStatusCode(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
                return Mono.empty();
            }
        } else {
            System.out.println("Creating new IP bucket...");
            rateLimitingCache.put(sourceIP, createIpRateLimitBucket());
        }

        return serverWebExchange.getSession().flatMap(webSession -> {
           if(webSession.getAttribute("bucket") != null) {
               // if it does - extract the bucket from the session
               var bucket = (Bucket) webSession.getAttribute("bucket");
               // consume a token
               if (bucket.tryConsume(1)) {
                   // if allowed - i.e. not over the allocated rate,
                   // then pass request on to the next filter in the chain
                   System.out.println("Available session tokens left : "+ bucket.getAvailableTokens());
                   return webFilterChain.filter(serverWebExchange);
               } else {
                   // if not allowed then modify response code and immediately return to client
                   serverWebExchange.getResponse().setStatusCode(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
                   return Mono.empty();
               }
           } else {
               var bucket = createSessionRateLimitBucket();
               System.out.println("Creating new session bucket...");
               // save bucket to session
               webSession.getAttributes().put("bucket", bucket);
               bucket.tryConsume(1);
               // pass on the request to the next filter in the chain
               return webFilterChain.filter(serverWebExchange);
           }
        });
    }
}
