package com.shimizukenta.secs.ext.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import com.shimizukenta.secs.SecsException;
import com.shimizukenta.secs.SecsLog;
import com.shimizukenta.secs.SecsLogListener;
import com.shimizukenta.secs.SecsMessage;
import com.shimizukenta.secs.SecsSendMessageException;
import com.shimizukenta.secs.SecsWaitReplyMessageException;
import com.shimizukenta.secs.hsms.HsmsMessage;
import com.shimizukenta.secs.hsms.HsmsReceiveMessageLog;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicator;
import com.shimizukenta.secs.secs2.Secs2;
import com.shimizukenta.secs.utils.ConfigConstants;
import com.shimizukenta.secs.utils.SecsUtils ;
import lombok.extern.slf4j.Slf4j;

/**
 * 抽象消息接收者
 *  SecsMessageReceiveListener 出现异常无法接收消息 , 具体原因后续排查
 * @author dsy
 *
 */
@Slf4j
public abstract class AbstractSecsMsgListener implements SecsLogListener ,ApplicationContextAware {


	public  static final String HANDLERS_STR = "HANDLERS";
	
	/**
	 * 单会话通讯器
	 */
	
	protected static HsmsSsCommunicator hsmsSsCommunicator; 
	
	/**
	 * 多会话预留
	 */
	
	protected static Map<String ,HsmsSsCommunicator> hsmsSsCommunicators; 
	
	
	/**
	 *  本类处理类暂存
	 */
	private final MultiKeyMap<Integer, Consumer<HsmsMessage>> HANDLERS = new MultiKeyMap<>();
	
	
	



	@Override
	public void received(SecsLog event) {
		if (event instanceof HsmsReceiveMessageLog) {
			HsmsReceiveMessageLog logInfo = (HsmsReceiveMessageLog) event;

			Optional<Object> value = logInfo.value();
			if(value.isPresent()) {
				Object object = value.get();
				HsmsMessage msg = (HsmsMessage) object;
				if(SecsUtils.dataMessage(msg)) {
					

					Consumer<HsmsMessage> consumer = HANDLERS.get(msg.getStream(), msg.getFunction());
					if (Objects.nonNull(consumer)) {
						new  Thread( () -> consumer.accept( msg )).start();
					} else {
						/**
						 * 没有对应的处理类
						 */
						log.error("received_ignore_data_msg:{}",  msg );
//						try {
//							hsmsSsCommunicator.send(9, 9 , false) ;
//						} catch (SecsException | InterruptedException e) {
//							log.error("error:{}" , e) ;
//						}
					}
				}

			}

		}
		
	
		
	}


	/** 通过传入的通讯器回复消息
	 * @param hsmsSsCommunicator
	 * @param primary
	 * @param wbit
	 * @param secs2
	 * @return
	 * @throws SecsSendMessageException
	 * @throws SecsWaitReplyMessageException
	 * @throws SecsException
	 * @throws InterruptedException
	 */
	public static Optional<SecsMessage>  reply(HsmsSsCommunicator hsmsSsCommunicator , SecsMessage primary,  boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return hsmsSsCommunicator.send(primary, primary.getStream(), primary.getFunction() + 1 , wbit, secs2);
	}
	
	
	/** 通过容器中的通讯器回复消息
	 * @param primary
	 * @param wbit
	 * @param secs2
	 * @return
	 * @throws SecsSendMessageException
	 * @throws SecsWaitReplyMessageException
	 * @throws SecsException
	 * @throws InterruptedException
	 */
	public static Optional<SecsMessage>  reply(SecsMessage primary,  boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return reply(hsmsSsCommunicator, primary, wbit, secs2);
	}
	
	
    /** 不等待消息回复消息
     * @param primary
     * @param secs2
     * @return
     * @throws SecsSendMessageException
     * @throws SecsWaitReplyMessageException
     * @throws SecsException
     * @throws InterruptedException
     */
    public static Optional<SecsMessage>  reply(SecsMessage primary,  Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return reply(hsmsSsCommunicator, primary, false, secs2);
	}

	
	/**
	  * @Title: send
	  * @Description: 限定stream function 请求
	  * @param @param strm
	  * @param @param func
	  * @param @param wbit
	  * @param @param secs2
	  * @param @return
	  * @param @throws SecsSendMessageException
	  * @param @throws SecsWaitReplyMessageException
	  * @param @throws SecsException
	  * @param @throws InterruptedException    设定文件
	  * @return Optional<SecsMessage>    返回类型
	  * @throws
	  */
	public  static Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return hsmsSsCommunicator.send(strm, func, wbit, secs2);
	}
	
	
	public static Optional<SecsMessage> send(SecsMessage primaryMsg, int strm, int func, boolean wbit) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException{
		
		return hsmsSsCommunicator.send(primaryMsg, strm, func, wbit);
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void setApplicationContext(ApplicationContext cxt) throws BeansException {
		
		if(cxt.containsBean(ConfigConstants.HSMS_SS_COMMUNICATOR)) {
			hsmsSsCommunicator = cxt.getBean( HsmsSsCommunicator.class);
		}
		
		if(cxt.containsBean(ConfigConstants.HSMS_SS_COMMUNICATORS)) {
			hsmsSsCommunicators = (Map<String ,HsmsSsCommunicator>)cxt.getBean(ConfigConstants.HSMS_SS_COMMUNICATORS);
		}
		

	}

    
}
