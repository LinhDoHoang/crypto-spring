package com.crypto.crypto.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class ControllerLogging {

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerBean() {
    }

    @Pointcut("within(com.crypto.crypto..*Service*)")
    public void serviceBean() {
    }

    @Around("controllerBean() || serviceBean()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String className = pjp.getSignature().getDeclaringTypeName();
        String methodName = pjp.getSignature().getName();

        log.info("==> [START] {}.{}()", className, methodName);
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("<== [END] {}.{}() elapsed: {}ms", className, methodName, elapsed);
            return result;
        } catch (Throwable exception) {
            log.error("=== [ERROR] {}.{}()", className, methodName, exception);
            throw exception;
        }
    }
}
