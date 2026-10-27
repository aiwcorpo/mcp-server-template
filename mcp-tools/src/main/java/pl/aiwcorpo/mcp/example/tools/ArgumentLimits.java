package pl.aiwcorpo.mcp.example.tools;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limits of a string argument, published in the tool's {@code inputSchema} ({@code maxLength}, {@code pattern})
 * by {@link SchemaLimitedToolCallbacks}. The schema goes into the manifest (C6), so the gateway and the client see
 * the limits; the tool still checks them itself, the schema is not a substitute for that check.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ArgumentLimits {

    /** JSON Schema {@code maxLength}; 0 = none. */
    int maxLength() default 0;

    /** JSON Schema {@code pattern} (ECMA 262, unanchored unless written with ^ and $); empty = none. */
    String pattern() default "";
}
