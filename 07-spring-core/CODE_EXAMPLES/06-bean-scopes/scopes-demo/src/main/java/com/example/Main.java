package com.example;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Main {

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext(AppConfig.class)) {
            System.out.println("-- Counter is a singleton --");
            System.out.println("first  next() = " + context.getBean(Counter.class).next());
            System.out.println("second next() = " + context.getBean(Counter.class).next());
            System.out.println("third  next() = " + context.getBean(Counter.class).next());
            System.out.println("(values keep climbing -- same instance every time)");

            System.out.println();
            System.out.println("-- WorkTicket is prototype --");
            System.out.println("first  id = " + context.getBean(WorkTicket.class).id());
            System.out.println("second id = " + context.getBean(WorkTicket.class).id());
            System.out.println("(different ids -- fresh instance per getBean)");

            System.out.println();
            System.out.println("-- BrokenCoordinator reuses one ticket --");
            BrokenCoordinator broken = context.getBean(BrokenCoordinator.class);
            System.out.println(broken.startWork());
            System.out.println(broken.startWork());

            System.out.println();
            System.out.println("-- FixedCoordinator gets a fresh ticket each call --");
            FixedCoordinator fixed = context.getBean(FixedCoordinator.class);
            System.out.println(fixed.startWork());
            System.out.println(fixed.startWork());
        }
    }
}
