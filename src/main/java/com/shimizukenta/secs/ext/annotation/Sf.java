package com.shimizukenta.secs.ext.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** stream function 处理方法
 * @author dsy
 *
 */
@Target(ElementType.METHOD )
@Retention(RetentionPolicy.RUNTIME)
public @interface Sf {

	/**
	 * stream numbrer
	 * @return
	 */
	int s() ;
	
	/** function number 
	 * @return
	 */
	int f() ;
}
