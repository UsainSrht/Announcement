package net.neoforged.fml.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compile-time stand-in for the NeoForge @Mod annotation. Never shipped; NeoForge provides the real one. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();
}
