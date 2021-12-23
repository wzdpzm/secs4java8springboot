package com.shimizukenta.secs.ext.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

@Component
@Target(value = ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecsMsgListener {

	
}
