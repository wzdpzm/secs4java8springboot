package com.shimizukenta.secs.ext.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import com.shimizukenta.secs.ext.annotation.SecsMsgListener;
import com.shimizukenta.secs.hsms.HsmsConnectionMode;
import com.shimizukenta.secs.hsms.HsmsMessage;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicator;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.shimizukenta.secs.utils.ConfigConstants;
import com.shimizukenta.secs.utils.SecsUtils;


@ConditionalOnProperty(prefix = ConfigConstants.ADDITIONS_DEVICES_HSMS , name = "enabled", havingValue = "true", matchIfMissing = false)
@Configuration(proxyBeanMethods = true )
@Import(SecsConfigurationRegistrar.class)
@EnableConfigurationProperties(HsmsCplexProps.class)
public class HsmsAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(HsmsAutoConfiguration.class);

	@Autowired
	private HsmsCplexProps hsmsCplexProps;

	@Lazy
	@Autowired(required = false )
	private List<AbstractSecsMsgListener> list;

//	@ConditionalOnProperty(prefix = "additions.devices.hsms", name = "mutiple", havingValue = "false", matchIfMissing = false)
	@ConditionalOnExpression("${!hsmsCplexProps.mutiple:true}")
	@Bean( ConfigConstants.HSMS_SS_COMMUNICATOR)
	public HsmsSsCommunicator hsmsSsCommunicator() {
		HsmsProps hsmsProps = hsmsCplexProps.getProps();
		HsmsSsCommunicator comm = getCommunicator(hsmsProps , null );
		Runnable run = () -> {
			try {
				comm.open();
			} catch (Exception e) {
				LOGGER.debug("open_error==>:") ;
			}
		};
		/**
		 * 单独起一个线程，避免hsms 打开失败导致应用无法启动
		 */
		Thread thread = new Thread(run, "hsms-commonunite");
		thread.setDaemon(true);
		thread.start();
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
		if(HsmsConnectionMode.PASSIVE.name().equals(hsmsProps.getProtocol()) ) {
			config.rebindIfPassive(hsmsProps.getRebindIfPassive());
		}
		
		HsmsSsCommunicator comm = HsmsSsCommunicator.newInstance(config);
		/**
		 * 打印详细的日志
		 */
		if(hsmsProps.getLogDetail()) {
			comm.addSecsLogListener(detail ->{
				
				if( detail instanceof HsmsMessage && SecsUtils.dataMessage( (HsmsMessage)detail )) {
					LOGGER.debug("secs2_logs_detail==>:") ;
					LOGGER.debug(Objects.toString(detail)) ;
				}
				
			}) ;
		}

		
		
		if (CollectionUtils.isNotEmpty(list)) {
			list.parallelStream().forEach( lsn -> {
				
				SecsMsgListener annotation = lsn.getClass().getAnnotation(SecsMsgListener.class);
				if(Objects.nonNull(annotation) && StringUtils.isEmpty(annotation.id() ) && annotation.global() ) {
					comm.addSecsLogListener(lsn);
				}else if(Objects.nonNull(annotation) && !StringUtils.isEmpty(key) && key.equalsIgnoreCase(annotation.id())) {
					comm.addSecsLogListener( lsn);
				}
				
				
				
			});

		}

		/**
		 * 添加状态记录
		 */
		comm.addSecsCommunicatableStateChangeListener(state -> {
			SecsUtils.getConnectionStatusFactory().put(comm, state);
		});

		
	
		return comm;
	}
	
	
}
