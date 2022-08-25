package com.shimizukenta.secs.ext.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;

import com.shimizukenta.secs.ext.config.SecsConfigurationRegistrar;

/** secs handler 处理类引入
 * @author dsy
 *
 */
@EnableAspectJAutoProxy(exposeProxy = true ,proxyTargetClass = true )
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(SecsConfigurationRegistrar.class)
public @interface EnableSecs {

}
