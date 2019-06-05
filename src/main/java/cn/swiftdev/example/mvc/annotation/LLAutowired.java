package cn.swiftdev.example.mvc.annotation;


import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface LLAutowired {
    String value() default "";
}
