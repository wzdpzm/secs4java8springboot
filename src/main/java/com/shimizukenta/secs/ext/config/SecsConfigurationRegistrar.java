package com.shimizukenta.secs.ext.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * @author dsy
 * 引入 bean 注册器
 */
public class SecsConfigurationRegistrar  implements ImportBeanDefinitionRegistrar {

	@Override
	public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
		
		    String name = SecsAnnotationPostProcessor.class.getName();
		  
		    boolean containsBeanDefinition = registry.containsBeanDefinition(name);
		    if(!containsBeanDefinition) {
		    	 //使用beanDefinitionRegistry对象将EchoBeanPostProcessor注入至Spring容器中
		        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.rootBeanDefinition(SecsAnnotationPostProcessor.class);
		     
		      
		        
				registry.registerBeanDefinition(name, beanDefinitionBuilder.getBeanDefinition());
		    }
			

		
		
	}





}
