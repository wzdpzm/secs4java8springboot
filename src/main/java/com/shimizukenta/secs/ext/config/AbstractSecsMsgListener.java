package com.shimizukenta.secs.ext.config;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import com.shimizukenta.secs.SecsException;
import com.shimizukenta.secs.SecsMessage;
import com.shimizukenta.secs.SecsMessageReceiveListener;
import com.shimizukenta.secs.SecsSendMessageException;
import com.shimizukenta.secs.SecsWaitReplyMessageException;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicator;
import com.shimizukenta.secs.secs2.Secs2;

import lombok.extern.slf4j.Slf4j;

/**
 * 抽象消息接收者
 * 
 * @author dsy
 *
 */
@Slf4j
public abstract class AbstractSecsMsgListener implements SecsMessageReceiveListener {

	public  static final String HANDLERS_STR = "HANDLERS";
	
	@Lazy
	@Autowired(required = false )
	protected HsmsSsCommunicator hsmsSsCommunicator; 
	
	private final MultiKeyMap<Integer, Consumer<SecsMessage>> HANDLERS = new MultiKeyMap<>();

	@Override
	public void received(SecsMessage event) {
		
		
		log.debug("get_sf_data:" + event);

		Consumer<SecsMessage> consumer = HANDLERS.get(event.getStream(), event.getFunction());
		if (Objects.nonNull(consumer)) {
			consumer.accept(event);
		} else {
			/**
			 * 没有对应的处理类
			 */
			log.warn("received_ignore_msg:{}", event.toJson());
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
	public static Optional<SecsMessage>  replay(HsmsSsCommunicator hsmsSsCommunicator , SecsMessage primary,  boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
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
	public  Optional<SecsMessage>  replay(SecsMessage primary,  boolean wbit, Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return replay(hsmsSsCommunicator, primary, wbit, secs2);
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
    public  Optional<SecsMessage>  replay(SecsMessage primary,  Secs2 secs2) throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException, InterruptedException {
		
		return replay(hsmsSsCommunicator, primary, false, secs2);
	}

}
