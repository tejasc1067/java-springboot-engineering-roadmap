package com.example;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// @Aspect declares the class as advice-bearing. @Component makes it a Spring bean
// (without that, @EnableAspectJAutoProxy has nothing to find).
@Aspect
@Component
public class TimingAspect {

    private final List<String> events = new ArrayList<>();

    // Pointcut: any method, any return type, on any class whose name ends in
    // "Service" anywhere under com.example, with any arguments.
    @Around("execution(* com.example..*Service.*(..))")
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long nanos = System.nanoTime() - start;
            String entry = pjp.getSignature().toShortString() + " took " + nanos + " ns";
            events.add(entry);
            System.out.println(entry);
        }
    }

    public List<String> events() {
        return events;
    }
}
