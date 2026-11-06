package net.minecraftforge.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stand-in for the Forge @Mod annotation. Never shipped; Forge provides the real one. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
