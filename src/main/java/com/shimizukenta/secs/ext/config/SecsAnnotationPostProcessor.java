package com.shimizukenta.secs.ext.config;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import com.shimizukenta.secs.SecsMessage;
import com.shimizukenta.secs.ext.annotation.SecsMsgListener;
import com.shimizukenta.secs.ext.annotation.Sf;

import lombok.extern.slf4j.Slf4j;



/** 自动向注解中填充stream function handler 处理器
 * @author dsy
 *
 */
@Slf4j
public class SecsAnnotationPostProcessor implements BeanPostProcessor {

  

	@Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        final Class<?> ultimateTargetClass = AopProxyUtils.ultimateTargetClass(bean);
        if (ultimateTargetClass.isAnnotationPresent(SecsMsgListener.class)) {
            SecsMsgListener listenerAnnotation = bean.getClass().getAnnotation(SecsMsgListener.class);
             if(Objects.nonNull(listenerAnnotation) && AbstractSecsMsgListener.class.isAssignableFrom(ultimateTargetClass)) {
            	 
            	 Field handlers = ReflectionUtils.findField(bean.getClass(), AbstractSecsMsgListener.HANDLERS_STR );
            	 if(Objects.nonNull(handlers)) {
            		 
            		 wrapperHandler(bean, handlers);
                
            	 }
            	
            
            	
             }
          
        }
        return bean;
    }

	/** 向bean 的handler 中填充值
	 * @param bean
	 * @param handlers
	 */
	private void wrapperHandler(Object bean, Field handlers) {
		handlers.setAccessible(true);
		 @SuppressWarnings("unchecked")
		MultiKeyMap<Integer, Consumer<SecsMessage>> value = ( MultiKeyMap<Integer, Consumer<SecsMessage>>)ReflectionUtils.getField(handlers, bean);
            
		 Set<Method> methods  = findMethod(bean.getClass(), SecsMessage.class);
		 if(CollectionUtils.isNotEmpty(methods)) {
			 
			 methods.forEach(method  ->{
				 
				 Sf sf = method.getAnnotation(Sf.class);
				 
		    	 if(Objects.nonNull(method) && Objects.nonNull(sf)) {
		    		 Consumer<SecsMessage> consumer =  i -> {
						try {
							method.setAccessible(true) ;
							method.invoke(bean, i);
						} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
							log.error("invocke_error:{}" , e);
						}
					};
					
					value.put(sf.s(), sf.f() , consumer);
		    	 }
		    	 
			 });
		 }
	}

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
    
    /** 通过变量类型来查找一个类拥有该变量类型的所有方法
     * @param clazz
     * @param paramTypes
     * @return
     */
    @Nullable
	public static Set<Method>  findMethod(Class<?> clazz,@Nullable Class<?>... paramTypes) {
		Assert.notNull(clazz, "Class must not be null");
		Class<?> searchType = clazz;
		Set<Method> methods = new HashSet<>();
		while (searchType != null) {
			List<Method> preMethods = Arrays.asList( clazz.getDeclaredMethods())   ;
			List<Method> list = preMethods.parallelStream().filter(method ->{
				
				boolean b = !method.getName().equals("received");
				boolean hasSameParams = hasSameParams(method, paramTypes);
				boolean contains = methods.contains(method);
				return b&& hasSameParams && !contains;
			}).collect(Collectors.toList());
			
			methods.addAll(list);
			searchType = searchType.getSuperclass();
		}
		return methods;
	}
    
	/** 通过变量类型来匹配方法
	 * @param method
	 * @param paramTypes
	 * @return
	 */
	private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
		return (paramTypes.length == method.getParameterCount() &&
				Arrays.equals(paramTypes, method.getParameterTypes()));
	}
}
