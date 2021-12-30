package com.shimizukenta.secs.ext.config;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.util.StringUtils;

import com.shimizukenta.secs.ext.annotation.SecsMsgListener;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicator;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.shimizukenta.secs.utils.ConfigConstants;
import com.shimizukenta.secs.utils.SecsUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ConditionalOnProperty(prefix = ConfigConstants.ADDITIONS_DEVICES_HSMS , name = "enabled", havingValue = "true", matchIfMissing = false)
@Configuration(proxyBeanMethods = false)
@Import(SecsConfigurationRegistrar.class)
@EnableConfigurationProperties(HsmsCplexProps.class)
public class HsmsAutoConfiguration {


	@Autowired
	private HsmsCplexProps hsmsCplexProps;

	@Autowired(required = false )
	private List<AbstractSecsMsgListener> list;

//	@ConditionalOnProperty(prefix = "additions.devices.hsms", name = "mutiple", havingValue = "false", matchIfMissing = false)
	@ConditionalOnExpression("${!hsmsCplexProps.mutiple:true}")
	@Bean( ConfigConstants.HSMS_SS_COMMUNICATOR)
	public HsmsSsCommunicator hsmsSsCommunicator() {
		HsmsProps hsmsProps = hsmsCplexProps.getProps();
		HsmsSsCommunicator comm = getCommunicator(hsmsProps , null );

		return comm;

	}



	@ConditionalOnProperty(prefix = ConfigConstants.ADDITIONS_DEVICES_HSMS ,name = "mutiple" , havingValue = "true" ,matchIfMissing = false)
	@Bean( ConfigConstants.HSMS_SS_COMMUNICATORS )
	public   Map<String ,HsmsSsCommunicator> hsmsSsCommunicators(){
		
		Map<String, HsmsProps> map = hsmsCplexProps.getSessions();
		Map<String ,HsmsSsCommunicator> res = new ConcurrentHashMap<>();
		if(MapUtils.isNotEmpty(map)) {
			map.entrySet().parallelStream().forEach(item ->{
				HsmsSsCommunicator comm = getCommunicator(item.getValue() , item.getKey() );
				res.put(item.getKey(), comm);
			});
		}
		return res;
	}

	
	
	
	/** 从配置项获取通讯器
	 * @param hsmsProps
	 * @return
	 */
	private HsmsSsCommunicator getCommunicator(HsmsProps hsmsProps , String key) {
		HsmsSsCommunicatorConfig config = SecsUtils.getConfig(hsmsProps);
		config.rebindIfPassive(1f);
		HsmsSsCommunicator comm = HsmsSsCommunicator.newInstance(config);
		/**
		 * 打印详细的日志
		 */
		if(hsmsProps.getLogDetail()) {
			 comm.addSecsLogListener( detail -> log.info(Objects.toString(detail)));
		}
	 
		if (CollectionUtils.isNotEmpty(list)) {
			list.parallelStream().forEach( lsn -> {
				
				SecsMsgListener annotation = lsn.getClass().getAnnotation(SecsMsgListener.class);
				if(Objects.nonNull(annotation) && StringUtils.isEmpty(annotation.id() ) && annotation.global() ) {
					comm.addSecsMessageReceiveListener(lsn);
				}else if(Objects.nonNull(annotation) && !StringUtils.isEmpty(key) && key.equalsIgnoreCase(annotation.id())) {
					comm.addSecsMessageReceiveListener( lsn);
				}
				
				
				
			});

		}

		/**
		 * 添加状态记录
		 */
		comm.addSecsCommunicatableStateChangeListener(state -> {
			SecsUtils.getConnectionStatusFactory().put(comm, state);
		});

		new Thread( () ->
		{

			try {
				comm.open();
				LockSupport.park();
			} catch (IOException e) {
				log.error("open error:{}", e);
			}

		
		}
				
				).start();
		CompletableFuture.runAsync(() -> {});
		return comm;
	}
	
	
}
