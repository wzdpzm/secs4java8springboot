package com.shimizukenta.secs.hsmsgs;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.shimizukenta.secs.AbstractSecsWaitReplyMessageExceptionLog;
import com.shimizukenta.secs.ByteArrayProperty;
import com.shimizukenta.secs.Property;
import com.shimizukenta.secs.PropertyChangeListener;
import com.shimizukenta.secs.ReadOnlyTimeProperty;
import com.shimizukenta.secs.SecsException;
import com.shimizukenta.secs.SecsMessage;
import com.shimizukenta.secs.SecsSendMessageException;
import com.shimizukenta.secs.SecsWaitReplyMessageException;
import com.shimizukenta.secs.hsmsss.AbstractHsmsSsRebindPassiveCommunicator;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicateState;
import com.shimizukenta.secs.hsmsss.HsmsSsCommunicatorConfig;
import com.shimizukenta.secs.hsmsss.HsmsSsConnectionLog;
import com.shimizukenta.secs.hsmsss.HsmsSsDetectTerminateException;
import com.shimizukenta.secs.hsmsss.HsmsSsMessage;
import com.shimizukenta.secs.hsmsss.HsmsSsMessageRejectReason;
import com.shimizukenta.secs.hsmsss.HsmsSsMessageSelectStatus;
import com.shimizukenta.secs.hsmsss.HsmsSsMessageType;
import com.shimizukenta.secs.hsmsss.HsmsSsNotConnectedException;
import com.shimizukenta.secs.hsmsss.HsmsSsPassiveBindLog;
import com.shimizukenta.secs.hsmsss.HsmsSsReceiveMessageLog;
import com.shimizukenta.secs.hsmsss.HsmsSsReplyMessageManager;
import com.shimizukenta.secs.hsmsss.HsmsSsSendMessageException;
import com.shimizukenta.secs.hsmsss.HsmsSsSendedMessageLog;
import com.shimizukenta.secs.hsmsss.HsmsSsTimeoutT3Exception;
import com.shimizukenta.secs.hsmsss.HsmsSsTimeoutT6Exception;
import com.shimizukenta.secs.hsmsss.HsmsSsTimeoutT7Exception;
import com.shimizukenta.secs.hsmsss.HsmsSsTimeoutT8Exception;
import com.shimizukenta.secs.hsmsss.HsmsSsTooBigSendMessageException;
import com.shimizukenta.secs.hsmsss.HsmsSsTrySendMessageLog;
import com.shimizukenta.secs.secs2.Secs2;
import com.shimizukenta.secs.secs2.Secs2BuildException;
import com.shimizukenta.secs.secs2.Secs2BytesPack;
import com.shimizukenta.secs.secs2.Secs2BytesPackBuilder;
import com.shimizukenta.secs.secs2.Secs2BytesParser;
import com.shimizukenta.secs.secs2.Secs2Exception;
import com.shimizukenta.secs.utils.SecsUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * This abstract class is implementation of HSMS-SS-Passive-rebind Communicator(SEMI-E37.1).
 * 
 * <p>
 * This class is called from {@link HsmsSsCommunicator#newInstance(HsmsSsCommunicatorConfig)<br />
 * </p>
 * 
 * @author kenta-shimizu
 *
 */
@Slf4j
public abstract class AbstractHsmsGsRebindPassiveCommunicator extends AbstractHsmsSsRebindPassiveCommunicator {
	
	public final static  Map<Integer,AbstractInnerConnection> selectedConnections =new ConcurrentHashMap<>();
	
	public final static Map<Integer,AbstractInnerConnection> deviceIdConnections =new ConcurrentHashMap<>();
	
	private Map<Integer,Integer> hashCodeToDeviceId =new ConcurrentHashMap<>();
	
	private final static Map<Integer,ByteArrayProperty> deviceIdSessionIdBytes =new ConcurrentHashMap<>();
	
	public AbstractHsmsGsRebindPassiveCommunicator(HsmsSsCommunicatorConfig config) {
		super(config);
	
	}
	
	public void open() throws IOException {
		super.open();
	}
	
	public void close() throws IOException {
		super.close();
	}
	
	@Override
	protected void passiveOpen() {
		
		executeLoopTask(() -> {
			
			passiveBind();
			
			ReadOnlyTimeProperty tp = this.hsmsSsConfig().rebindIfPassive();
			if ( tp.gtZero() ) {
				tp.sleep();
			} else {
				return;
			}
		});
	}
	
	private void passiveBind() throws InterruptedException {
		
		try (
				AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open();
				) {
			
			final SocketAddress socketAddr = hsmsSsConfig().socketAddress().getSocketAddress();
			
			notifyLog(HsmsSsPassiveBindLog.tryBind(socketAddr));
			
			server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
			server.bind(socketAddr);
			
			notifyLog(HsmsSsPassiveBindLog.binded(socketAddr));
			
			server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {

				@Override
				public void completed(AsynchronousSocketChannel channel, Void attachment) {
					server.accept(attachment, this);
					
					
					SocketAddress local = null;
					SocketAddress remote = null;
					
					try {
						local = channel.getLocalAddress();
						remote = channel.getRemoteAddress();
						
						notifyLog(HsmsSsConnectionLog.accepted(local, remote));

						completedAction(channel);

					}
					catch ( IOException e ) {
						notifyLog(e);
					}
					catch ( InterruptedException ignore ) {
					}
					finally {
						
						try {
							channel.shutdownOutput();
						}
						catch ( IOException ignore ) {
						}
						
						try {
							channel.close();
						}
						catch ( IOException e ) {
							notifyLog(e);
						}
						notifyLog(HsmsSsConnectionLog.closed(local, remote));
					}
				}
				
				@Override
				public void failed(Throwable t, Void attachment) {
					
					if ( ! (t instanceof ClosedChannelException) ) {
						notifyLog(t);
					}
					
					synchronized ( server ) {
						server.notifyAll();
					}
				}
			});
			
			synchronized ( server ) {
				
				try {
					server.wait();
				}
				finally {
					notifyLog(HsmsSsPassiveBindLog.closed(socketAddr));
				}
			}
			
		}
		catch ( IOException e ) {
			notifyLog(e);
		}
	}
	
protected void completedAction(AsynchronousSocketChannel channel) throws InterruptedException {
		
		try {
			
			log.warn("gsSession==created ==>local:{}, remote:{}", channel.getLocalAddress(), channel.getRemoteAddress());
			log.warn("gsSession==created ==>local:{}, remote:{}", channel.getLocalAddress(), channel.getRemoteAddress());
			

			final PassiveInnerConnection conn = new PassiveInnerConnection(channel);

			final Collection<Callable<Void>> tasks = Arrays.asList(
					() -> {
						try {
							conn.reading();
						}
						catch ( InterruptedException ignore ) {
						}
						return null;
					},
					() -> {
						try {
							conn.mainTask();
						}
						catch ( InterruptedException ignore ) {
						}
						return null;
					}
					);
			
			this.executeInvokeAny(tasks);
		}
		catch ( ExecutionException | IOException e ) {

			Throwable t = e.getCause();

			if ( t instanceof RuntimeException ) {
				throw (RuntimeException)t;
			}

			notifyLog(t);
		}
	}

private ByteArrayProperty Default_sessionIdBytes = ByteArrayProperty.newInstance(new byte[] {0, 0});

 class PassiveInnerConnection extends AbstractInnerConnection {

	private ByteArrayProperty sessionIdBytes = ByteArrayProperty.newInstance(new byte[] {0, 0});
	private final Property<HsmsSsCommunicateState> hsmsSsCommStateProperty = Property.newInstance(HsmsSsCommunicateState.NOT_CONNECTED);
		

	public PassiveInnerConnection(AsynchronousSocketChannel channel) {
		super(channel);
		
	}
	
	public void mainTask() throws InterruptedException {
		
		try {
			
			{
				final Collection<Callable<Boolean>> tasks = Arrays.asList(
						() -> {
							try {
								return connectTask();
							}
							catch ( InterruptedException ignore ) {
							}
							
							return Boolean.FALSE;
						}
						);
				
				try {
					boolean f = executeInvokeAny(
							tasks,
							hsmsSsConfig().timeout().t7()
							).booleanValue();
					
					if ( f ) {
						
						/* SELECTED */
						notifyHsmsSsCommunicateStateChange(HsmsSsCommunicateState.SELECTED);
						
					} else {
						
						return;
					}
				}
				catch ( TimeoutException e ) {
					notifyLog(new HsmsSsTimeoutT7Exception(e));
					return;
				}
				catch ( ExecutionException e ) {
					
					Throwable t = e.getCause();
					
					if ( t instanceof RuntimeException ) {
						throw (RuntimeException)t;
					}
					
					notifyLog(t);
					return;
				}
			}
			
			{
				final Collection<Callable<Void>> tasks = Arrays.asList(
						() -> {
							try {
								selectedTask();
							}
							catch ( InterruptedException ignore ) {
							}
							catch ( SecsException e ) {
								notifyLog(e);
							}
							return null;
						},
						() -> {
							try {
								linktesting();
							}
							catch ( InterruptedException ignore ) {
							}
							return null;
						}
						);
				
				try {
					executeInvokeAny(tasks);
				}
				catch ( ExecutionException e ) {
					
					Throwable t = e.getCause();
					
					if ( t instanceof RuntimeException ) {
						throw (RuntimeException)t;
					}
					
					notifyLog(t);
				}
			}
		}
		finally {
			notifyHsmsSsCommunicateStateChange(HsmsSsCommunicateState.NOT_CONNECTED);
			removeSelectedConnection(this);
		}
	}
	
	protected Boolean connectTask() throws InterruptedException, SecsException {
		
		for ( ;; ) {
			
			final HsmsSsMessage msg = this.takeReceiveMessage();
			
			switch ( HsmsSsMessageType.get(msg) ) {
			case DATA: {
				
				send(createRejectRequest(msg, HsmsSsMessageRejectReason.NOT_SELECTED));
				break;
			}
			case SELECT_REQ: {
				
				boolean f = addSelectedConnection(this);
				
				if ( f /* success */) {
					send(createSelectResponse(msg, HsmsSsMessageSelectStatus.SUCCESS));
					
					return Boolean.TRUE;
					
				} else {
					
					send(createSelectResponse(msg, HsmsSsMessageSelectStatus.ALREADY_USED));
				}
				
				break;
			}
			case LINKTEST_REQ: {
				
				send(createLinktestResponse(msg));
				break;
			}
			case SEPARATE_REQ: {

				return Boolean.FALSE;
				/* break; */
			}
			case SELECT_RSP:
			case DESELECT_RSP:
			case LINKTEST_RSP:
			case REJECT_REQ: {
				
				send(createRejectRequest(msg, HsmsSsMessageRejectReason.TRANSACTION_NOT_OPEN));
				break;
			}
			case DESELECT_REQ:
			default: {
				
				if ( HsmsSsMessageType.supportSType(msg) ) {
					
					if ( ! HsmsSsMessageType.supportPType(msg) ) {
						
						send(createRejectRequest(msg, HsmsSsMessageRejectReason.NOT_SUPPORT_TYPE_P));
					}
					
				} else {
					
					send(createRejectRequest(msg, HsmsSsMessageRejectReason.NOT_SUPPORT_TYPE_S));
				}
			}
			}
		}
	}
	
	protected void selectedTask() throws InterruptedException, SecsException {
		
		
		for ( ;; ) {
			
			final HsmsSsMessage msg = this.takeReceiveMessage();
			
			switch ( HsmsSsMessageType.get(msg) ) {
			case DATA: {
				zxgjHandler(msg ,this);
				notifyReceiveMessage(msg);
				break;
			}
			case SELECT_REQ: {
				
				send(createSelectResponse(msg, HsmsSsMessageSelectStatus.ACTIVED));
				break;
			}
			case LINKTEST_REQ: {
				
				send(createLinktestResponse(msg));
				break;
			}
			case SEPARATE_REQ: {
				return;
				/* break; */
			}
			case SELECT_RSP:
			case DESELECT_RSP:
			case LINKTEST_RSP:
			case REJECT_REQ: {
				
				send(createRejectRequest(msg, HsmsSsMessageRejectReason.TRANSACTION_NOT_OPEN));
				break;
			}
			case DESELECT_REQ:
			default: {
				
				if ( HsmsSsMessageType.supportSType(msg) ) {
					
					if ( ! HsmsSsMessageType.supportPType(msg) ) {
						
						send(createRejectRequest(msg, HsmsSsMessageRejectReason.NOT_SUPPORT_TYPE_P));
					}
					
				} else {
					
					send(createRejectRequest(msg, HsmsSsMessageRejectReason.NOT_SUPPORT_TYPE_S));
				}
			}
			}
		}
	}

	private void zxgjHandler(final HsmsSsMessage msg, PassiveInnerConnection passiveInnerConnection) {
		if(Objects.isNull(passiveInnerConnection.deviceId)) {
			int deviceId = msg.deviceId();
			deviceId = specialHandler(deviceId, msg, passiveInnerConnection) ;
			sessionIdBytes = SecsUtils.changeDeviceid( deviceId);
			deviceIdSessionIdBytes.put( deviceId, sessionIdBytes) ;
			deviceIdConnections.put( deviceId,  this);
			hashCodeToDeviceId.put(passiveInnerConnection.hashCode(), deviceId);
		}
		
		
	}
	
	


	/** 中芯国际项目特殊处理 临时解决方案
	 * @param deviceId
	 * @param msg
	 * @param passiveInnerConnection 
	 */
	private int specialHandler(int deviceId, HsmsSsMessage msg, PassiveInnerConnection passiveInnerConnection) {
		
		if(isSpecial(msg)) {
			Secs2 secs2 = msg.secs2();

			try {
				String deviceIdStr = secs2.getAscii(0);
				deviceId = SecsUtils.deviceIdStrToInt(deviceIdStr);
				passiveInnerConnection.setDeviceId(deviceId);
				passiveInnerConnection.setDeviceIdStr(deviceIdStr);
			} catch (Secs2Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
		return deviceId;
	}



	protected boolean addSelectedConnection(AbstractInnerConnection c) {
		
		selectedConnections.putIfAbsent (c.hashCode() , c);
		return true ;
		}
	}
	
	

	private final static boolean isSpecial(SecsMessage primary) {
		return (primary.getStream() == 1 && primary.getFunction() == 13) ||  (primary.getStream() == 1 && primary.getFunction() == 14);
	}

	
	private final Property<HsmsSsCommunicateState> hsmsSsCommStateProperty = Property.newInstance(HsmsSsCommunicateState.NOT_CONNECTED);
	
	
	


	

	protected boolean removeSelectedConnection(AbstractInnerConnection c) {
		Integer deviceId = hashCodeToDeviceId.get(c.hashCode());
		if(Objects.nonNull(deviceId)) {
			deviceIdConnections.remove(deviceId);
			hashCodeToDeviceId.remove(deviceId);
		}
		return Objects.nonNull( this.selectedConnections.remove( c.hashCode())) ;
	
	}

	
	protected boolean removeSelectedConnection(int sessionId) {
		return Objects.nonNull( this.deviceIdConnections.remove( sessionId )) ;
	
	}
	
	protected AbstractInnerConnection getSelectedConnection( Integer sessionId) {
		Collection<AbstractInnerConnection> collection = selectedConnections.values();
		for (AbstractInnerConnection col : collection) {
			if( Objects.nonNull(col.deviceId) && col.deviceId.intValue() == sessionId) {
				return col;
			}
		}
		return deviceIdConnections.get( sessionId);
	}
	
	/* HSMS Communicate State */
	protected HsmsSsCommunicateState hsmsSsCommunicateState() {
		return hsmsSsCommStateProperty.get();
	}
	
	protected void notifyHsmsSsCommunicateStateChange(HsmsSsCommunicateState state) {
		hsmsSsCommStateProperty.set(state);
	}
	
	
	
	@Override
	public boolean linktest() throws InterruptedException {
		try {
			return send(createLinktestRequest()).isPresent();
		}
		catch ( SecsException e ) {
			return false;
		}
	}
	
	
	private final AtomicInteger autoNumber = new AtomicInteger();
	
	private int autoNumber() {
		return autoNumber.incrementAndGet();
	}
	
	protected byte[] systemBytes() {
		
		byte[] bs = new byte[4];
		
		bs[0] = (byte)0;
		bs[1] = (byte)0;
		
		int n = autoNumber();
		
		bs[2] = (byte)(n >> 8);
		bs[3] = (byte)n;
		
		return bs;
	}
	
	@Override
	public Optional<HsmsSsMessage> send(HsmsSsMessage msg)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException,
			InterruptedException {
	
		final AbstractInnerConnection c = getSelectedConnection(msg.deviceId());
		
		if ( c == null ) {
			
			throw new HsmsSsNotConnectedException(msg);
			
		} else {
			
			try {
				return c.send(msg);
			}
			catch ( SecsWaitReplyMessageException e ) {
				
				notifyLog(new AbstractSecsWaitReplyMessageExceptionLog(e) {
					
					private static final long serialVersionUID = -1896655030432810962L;
				});
				
				throw e;
			}
			catch ( SecsException e ) {
				notifyLog(e);
				throw e;
			}
		}
	}
	

	/** 通过设备id 来发送消息
	 * @param msg
	 * @param deviceId
	 * @return
	 * @throws SecsSendMessageException
	 * @throws SecsWaitReplyMessageException
	 * @throws SecsException
	 * @throws InterruptedException
	 */
	public Optional<HsmsSsMessage> send(HsmsSsMessage msg, String deviceId)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException,
			InterruptedException {
	
		final AbstractInnerConnection c = getSelectedConnection(SecsUtils.deviceIdStrToInt(deviceId));
		
		if ( c == null ) {
			
			throw new HsmsSsNotConnectedException(msg);
			
		} else {
			
			try {
				return c.send(msg);
			}
			catch ( SecsWaitReplyMessageException e ) {
				
				notifyLog(new AbstractSecsWaitReplyMessageExceptionLog(e) {
					
					private static final long serialVersionUID = -1896655030432810962L;
				});
				
				throw e;
			}
			catch ( SecsException e ) {
				notifyLog(e);
				throw e;
			}
		}
	}
	
	@Override
	public Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException
			, InterruptedException {
		byte[] bs = Default_sessionIdBytes.get();
		byte byte0 = bs[0];
		byte byte1 = bs[1];
	
		HsmsSsMessageType mt = HsmsSsMessageType.DATA;
		
		byte[] sysbytes = systemBytes();
		
		byte[] head = new byte[] {
				byte0,
				byte1,
				(byte)strm,
				(byte)func,
				mt.pType(),
				mt.sType(),
				sysbytes[0],
				sysbytes[1],
				sysbytes[2],
				sysbytes[3]
		};
		
		if ( wbit ) {
			head[2] |= 0x80;
		}
		
		return send(new HsmsSsMessage(head, secs2)).map(msg -> (SecsMessage)msg);
	}
	private final ByteArrayProperty sessionIdBytes = ByteArrayProperty.newInstance(new byte[] {0, 0});

	public Optional<SecsMessage> send(int strm, int func, boolean wbit, Secs2 secs2 , int deviceId)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException
			, InterruptedException {
		
		 byte[] bs = sessionIdBytes.get();
	
		HsmsSsMessageType mt = HsmsSsMessageType.DATA;
		
		byte[] sysbytes = systemBytes();
		
		byte[] head = new byte[] {
				bs[0],
				bs[1],
				(byte)strm,
				(byte)func,
				mt.pType(),
				mt.sType(),
				sysbytes[0],
				sysbytes[1],
				sysbytes[2],
				sysbytes[3]
		};
		
		if ( wbit ) {
			head[2] |= 0x80;
		}
		
		return send(new HsmsSsMessage(head, secs2) , deviceId).map(msg -> (SecsMessage)msg);
	}
	
	@Override
	public Optional<SecsMessage> send(SecsMessage primary, int strm, int func, boolean wbit, Secs2 secs2)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException
			, InterruptedException {
		
		byte[] pri = primary.header10Bytes();
		
		
		HsmsSsMessageType mt = HsmsSsMessageType.DATA;
	
		byte[] head = new byte[] {
				pri[0],
				pri[1],
				(byte)strm,
				(byte)func,
				mt.pType(),
				mt.sType(),
				pri[6],
				pri[7],
				pri[8],
				pri[9]
		};
		
		if ( wbit ) {
			head[2] |= 0x80;
		}
		
		return send(createHsmsSsMessage(head, secs2)).map(msg -> (SecsMessage)msg);
	}
	
	
	
	public Optional<SecsMessage> send(SecsMessage primary, int strm, int func, boolean wbit, Secs2 secs2 , int deviceId)
			throws SecsSendMessageException, SecsWaitReplyMessageException, SecsException
			, InterruptedException {
		
		byte[] pri = primary.header10Bytes();
		
		
		HsmsSsMessageType mt = HsmsSsMessageType.DATA;
	
		byte[] head = new byte[] {
				pri[0],
				pri[1],
				(byte)strm,
				(byte)func,
				mt.pType(),
				mt.sType(),
				pri[6],
				pri[7],
				pri[8],
				pri[9]
		};
		
		if ( wbit ) {
			head[2] |= 0x80;
		}
		
		return send(createHsmsSsMessage(head, secs2) , deviceId).map(msg -> (SecsMessage)msg);
	}
	
	
	
	
	protected  Optional<HsmsSsMessage> send(HsmsSsMessage createHsmsSsMessage, int deviceId) throws InterruptedException, SecsException   {

		
		final AbstractInnerConnection c = getSelectedConnection(deviceId);
		
		if ( c == null ) {
			
			throw new HsmsSsNotConnectedException(createHsmsSsMessage);
			
		} else {
			
			try {
				return c.send(createHsmsSsMessage);
			}
			catch ( SecsWaitReplyMessageException e ) {
				
				notifyLog(new AbstractSecsWaitReplyMessageExceptionLog(e) {
					
					private static final long serialVersionUID = -1896655030432810962L;
				});
				
				throw e;
			}
			catch ( SecsException e ) {
				notifyLog(e);
				throw e;
			}
		}
	
	};

	@Override
	public HsmsSsMessage createHsmsSsMessage(byte[] header) {
		return createHsmsSsMessage(header, Secs2.empty());
	}
	
	@Override
	public HsmsSsMessage createHsmsSsMessage(byte[] header, Secs2 body) {
		return new HsmsSsMessage(header, body);
	}
	
	@Override
	public HsmsSsMessage createSelectRequest() {
		return createHsmsSsControlPrimaryMessage(HsmsSsMessageType.SELECT_REQ);
	}
	
	@Override
	public HsmsSsMessage createSelectResponse(HsmsSsMessage primary, HsmsSsMessageSelectStatus status) {
		
		HsmsSsMessageType mt = HsmsSsMessageType.SELECT_RSP;
		byte[] pri = primary.header10Bytes();
		
		return createHsmsSsMessage(new byte[] {
				pri[0],
				pri[1],
				(byte)0,
				status.statusCode(),
				mt.pType(),
				mt.sType(),
				pri[6],
				pri[7],
				pri[8],
				pri[9]
		});
	}
	
	@Override
	public HsmsSsMessage createDeselectRequest() {
		return createHsmsSsControlPrimaryMessage(HsmsSsMessageType.DESELECT_REQ);
	}
	
	@Override
	public HsmsSsMessage createDeselectResponse(HsmsSsMessage primary) {
		
		HsmsSsMessageType mt = HsmsSsMessageType.DESELECT_RSP;
		byte[] pri = primary.header10Bytes();
		
		return createHsmsSsMessage(new byte[] {
				pri[0],
				pri[1],
				(byte)0,
				(byte)0,
				mt.pType(),
				mt.sType(),
				pri[6],
				pri[7],
				pri[8],
				pri[9]
		});
	}
	
	@Override
	public HsmsSsMessage createLinktestRequest() {
		return createHsmsSsControlPrimaryMessage(HsmsSsMessageType.LINKTEST_REQ);
	}
	
	@Override
	public HsmsSsMessage createLinktestResponse(HsmsSsMessage primary) {
		
		HsmsSsMessageType mt = HsmsSsMessageType.LINKTEST_RSP;
		byte[] pri = primary.header10Bytes();
		
		return createHsmsSsMessage(new byte[] {
				pri[0],
				pri[1],
				(byte)0,
				(byte)0,
				mt.pType(),
				mt.sType(),
				pri[6],
				pri[7],
				pri[8],
				pri[9]
		});
	}
	
	@Override
	public HsmsSsMessage createRejectRequest(HsmsSsMessage ref, HsmsSsMessageRejectReason reason) {
		
		HsmsSsMessageType mt = HsmsSsMessageType.REJECT_REQ;
		byte[] bs = ref.header10Bytes();
		byte b = reason == HsmsSsMessageRejectReason.NOT_SUPPORT_TYPE_P ? bs[4] : bs[5];
		
		return createHsmsSsMessage(new byte[] {
				bs[0],
				bs[1],
				b,
				reason.reasonCode(),
				mt.pType(),
				mt.sType(),
				bs[6],
				bs[7],
				bs[8],
				bs[9]
		});
	}
	
	@Override
	public HsmsSsMessage createSeparateRequest() {
		return createHsmsSsControlPrimaryMessage(HsmsSsMessageType.SEPARATE_REQ);
	}
	
	private HsmsSsMessage createHsmsSsControlPrimaryMessage(HsmsSsMessageType mt) {
		
		byte[] sysbytes = systemBytes();
		
		return createHsmsSsMessage(new byte[] {
				(byte)0xFF,
				(byte)0xFF,
				(byte)0,
				(byte)0,
				mt.pType(),
				mt.sType(),
				sysbytes[0],
				sysbytes[1],
				sysbytes[2],
				sysbytes[3]
		});
	}
	
	
	private static final long MAX_BUFFER_SIZE = 256L * 256L;
	private static final byte[] emptyBytes = new byte[] {0x0, 0x0, 0x0, 0x0};
	private static final long bodyBufferSize = 1024L;
	
	protected abstract class AbstractInnerConnection {
		
		private final AsynchronousSocketChannel channel;
		private boolean linktestResetted;
		
		protected String deviceIdStr;
		
		protected Integer deviceId;
		
		protected AbstractInnerConnection(AsynchronousSocketChannel channel) {
			this.channel = channel;
			this.linktestResetted = false;
		}
		
		




		public String getDeviceIdStr() {
			return deviceIdStr;
		}

		public void setDeviceIdStr(String deviceIdStr) {
			this.deviceIdStr = deviceIdStr;
		}

		public Integer getDeviceId() {
			return deviceId;
		}

		public void setDeviceId(Integer deviceId) {
			this.deviceId = deviceId;
		}






		private final BlockingQueue<HsmsSsMessage> recvMsgQueue = new LinkedBlockingQueue<>();
		
		protected HsmsSsMessage takeReceiveMessage() throws InterruptedException {
			return this.recvMsgQueue.take();
		}
		
		protected HsmsSsMessage pollReceiveMessage(ReadOnlyTimeProperty timeout) throws InterruptedException {
			return timeout.poll(this.recvMsgQueue);
		}
		
		private final HsmsSsReplyMessageManager replyMgr = new HsmsSsReplyMessageManager();
		
		protected Optional<HsmsSsMessage> send(HsmsSsMessage msg)
				throws SecsSendMessageException, SecsWaitReplyMessageException,
				SecsException, InterruptedException {
			
			switch ( HsmsSsMessageType.get(msg) ) {
			case SELECT_REQ:
			case LINKTEST_REQ: {
				
				try {
					this.replyMgr.entry(msg);
					
					System.out.println("channelHashCode:{}" + this.channel.hashCode()  );
					this.innerSend(msg);
					
					final HsmsSsMessage r = replyMgr.reply(
							msg,
							hsmsSsConfig().timeout().t6()
							).orElse(null);
					
					if ( r == null ) {
						throw new HsmsSsTimeoutT6Exception(msg);
					} else {
						return Optional.of(r);
					}
				}
				finally {
					this.replyMgr.exit(msg);
				}
				
				/* break; */
			}
			case DATA: {
				if ( msg.wbit() ) {
					
					try {
						replyMgr.entry(msg);
						
						this.innerSend(msg);
						
						final HsmsSsMessage r = replyMgr.reply(
								msg,
								hsmsSsConfig().timeout().t3()
								).orElse(null);
						
						if ( r == null ) {
							throw new HsmsSsTimeoutT3Exception(msg);
						} else {
							return Optional.of(r);
						}
					}
					finally {
						replyMgr.exit(msg);
					}
					
				} else {
					
					this.innerSend(msg);
					return Optional.empty();
				}
				
				/* break */
			}
			default: {
				
				this.innerSend(msg);
				return Optional.empty();
			}
			}
		}
		
		protected long prototypeMaxBufferSize() {
			return MAX_BUFFER_SIZE;
		}
		
		private void innerSend(HsmsSsMessage msg)
				throws SecsSendMessageException, SecsException,InterruptedException {
			
			try {
				notifyLog(new HsmsSsTrySendMessageLog(msg));
				
				final Secs2BytesPack pack = Secs2BytesPackBuilder.build(1024, msg.secs2());
				
				long len = pack.size() + 10L;
				
				if ((len > 0x00000000FFFFFFFFL) || (len < 10L)) {
					throw new HsmsSsTooBigSendMessageException(msg);
				}
				
				notifyTrySendMessagePassThrough(msg);
				
				long bufferSize = len + 4L;
				
				if ( bufferSize > this.prototypeMaxBufferSize() ) {
					
					final List<ByteBuffer> buffers = new ArrayList<>();
					{
						ByteBuffer buffer = ByteBuffer.allocate(14);
						
						buffer.put((byte)(len >> 24));
						buffer.put((byte)(len >> 16));
						buffer.put((byte)(len >>  8));
						buffer.put((byte)(len      ));
						buffer.put(msg.header10Bytes());
						
						((Buffer)buffer).flip();
						buffers.add(buffer);
					}
					
					for (byte[] bs : pack.getBytes()) {
						ByteBuffer buffer = ByteBuffer.allocate(bs.length);
						buffer.put(bs);
						
						((Buffer)buffer).flip();
						buffers.add(buffer);
					}
					
					synchronized ( this.channel ) {
						for ( ByteBuffer buffer : buffers ) {
							innerSend(buffer);
						}
					}
					
				} else {
					
					ByteBuffer buffer = ByteBuffer.allocate((int)bufferSize);
					
					buffer.put((byte)(len >> 24));
					buffer.put((byte)(len >> 16));
					buffer.put((byte)(len >>  8));
					buffer.put((byte)(len      ));
					buffer.put(msg.header10Bytes());
					
					for (byte[] bs : pack.getBytes()) {
						buffer.put(bs);
					}
					
					((Buffer)buffer).flip();
					
					synchronized ( this.channel ) {
						innerSend(buffer);
					}
				}
				
				notifySendedMessagePassThrough(msg);
				
				notifyLog(new HsmsSsSendedMessageLog(msg));
			}
			catch ( ExecutionException e ) {
				
				Throwable t = e.getCause();
				
				if ( t instanceof RuntimeException ) {
					throw (RuntimeException)t;
				}
				
				throw new HsmsSsSendMessageException(msg, t);
			}
			catch ( Secs2BuildException | HsmsSsDetectTerminateException e ) {
				throw new HsmsSsSendMessageException(msg, e);
			}
		}
		
		private void innerSend(ByteBuffer buffer)
				throws ExecutionException, HsmsSsDetectTerminateException, InterruptedException {
			
			while ( buffer.hasRemaining() ) {
				
				final Future<Integer> f = this.channel.write(buffer);
				
				try {
					int w = f.get().intValue();
					
					if ( w <= 0 ) {
						throw new HsmsSsDetectTerminateException();
					}
				}
				catch ( InterruptedException e ) {
					f.cancel(true);
					throw e;
				}
			}
		}
		
		public void reading() throws InterruptedException {
			
			final ByteBuffer lenBf = ByteBuffer.allocate(8);
			final ByteBuffer headBf = ByteBuffer.allocate(10);
			final byte[] headbs = new byte[10];
			final List<byte[]> bodybss = new ArrayList<>();

			try {
				
				for ( ;; ) {
					
					((Buffer)lenBf).clear();
					((Buffer)headBf).clear();
					bodybss.clear();
					
					lenBf.put(emptyBytes);
					
					readToBuffer(channel, lenBf, false);
					
					while ( lenBf.hasRemaining() ) {
						readToBuffer(channel, lenBf);
					}
					
					while ( headBf.hasRemaining() ) {
						readToBuffer(channel, headBf);
					}
					
					((Buffer)lenBf).flip();
					long len = lenBf.getLong() - 10L;
					
					if ( len < 0L ) {
						continue;
					}
					
					this.resetLinktesting();
					
					while ( len > 0L ) {
						
						final ByteBuffer buffer = ByteBuffer.allocate(
								(int)((len > bodyBufferSize) ? bodyBufferSize : len)); 
						
						len -= bodyBufferSize;
						
						while ( buffer.hasRemaining() ) {
							readToBuffer(channel, buffer);
						}
						
						this.resetLinktesting();
						
						((Buffer)buffer).flip();
						byte[] bs = new byte[buffer.remaining()];
						buffer.get(bs);
						
						bodybss.add(bs);
					}
					
					((Buffer)headBf).flip();
					headBf.get(headbs);
					
					try {
						HsmsSsMessage msg = new HsmsSsMessage(
								headbs,
								Secs2BytesParser.getInstance().parse(bodybss));
						
						notifyReceiveMessagePassThrough(msg);
						notifyLog(new HsmsSsReceiveMessageLog(msg));
						
						HsmsSsMessage r = replyMgr.put(msg).orElse(null);
						if ( r != null ) {
							this.recvMsgQueue.put(r);
						}
					}
					catch ( Secs2Exception e ) {
						notifyLog(e);
					}
				}
			}
			catch ( HsmsSsDetectTerminateException | HsmsSsTimeoutT8Exception e ) {
				notifyLog(e);
			}
			catch ( ExecutionException e ) {
				
				Throwable t = e.getCause();
				
				if ( t instanceof RuntimeException ) {
					throw (RuntimeException)t;
				}
				
				notifyLog(t);
			}
		}
		
		private int readToBuffer(AsynchronousSocketChannel channel, ByteBuffer buffer)
				throws HsmsSsDetectTerminateException, HsmsSsTimeoutT8Exception, ExecutionException, InterruptedException {
			
			return readToBuffer(channel, buffer, true);
		}

		private int readToBuffer(AsynchronousSocketChannel channel, ByteBuffer buffer, boolean detectT8Timeout)
				throws HsmsSsDetectTerminateException, HsmsSsTimeoutT8Exception, ExecutionException, InterruptedException {
			
			final Future<Integer> f = channel.read(buffer);
			
			try {
				
				if ( detectT8Timeout ) {
					
					try {
						int r = hsmsSsConfig().timeout().t8().future(f).intValue();
						
						if ( r < 0 ) {
							throw new HsmsSsDetectTerminateException();
						}
						
						return r;
					}
					catch ( TimeoutException e ) {
						throw new HsmsSsTimeoutT8Exception(e);
					}
						
				} else {
					
					int r = f.get().intValue();
					if ( r < 0 ) {
						throw new HsmsSsDetectTerminateException();
					}
					
					return r;
				}
			}
			catch ( InterruptedException e ) {
				f.cancel(true);
				throw e;
			}
		}
		
		private final Object syncLinktesting = new Object();
		
		protected void linktesting() throws InterruptedException {
			
			final ReadOnlyTimeProperty tpLinktest = hsmsSsConfig().linktest();
			
			final PropertyChangeListener<Number> lstnr = (Number n) -> {
				synchronized ( this.syncLinktesting ) {
					this.linktestResetted = true;
					this.syncLinktesting.notifyAll();
				}
			};
			
			try {
				
				tpLinktest.addChangeListener(lstnr);
				
				for ( ;; ) {
					
					synchronized ( this.syncLinktesting ) {
						
						if ( tpLinktest.geZero() ) {
							tpLinktest.wait(this.syncLinktesting);
						} else {
							this.syncLinktesting.wait();
						}
						
						if ( this.linktestResetted ) {
							this.linktestResetted = false;
							continue;
						}
					}
					
					if ( this.send(createLinktestRequest())
							.map(HsmsSsMessageType::get)
							.filter(t -> t == HsmsSsMessageType.LINKTEST_RSP)
							.isPresent()) {
						
						continue;
						
					} else {
						
						return;
					}
				}
			}
			catch ( SecsException e ) {
				notifyLog(e);
				return;
			}
			finally {
				tpLinktest.removeChangeListener(lstnr);
			}
		}
		
		private void resetLinktesting() {
			synchronized ( this.syncLinktesting ) {
				this.linktestResetted = true;
			}
		}
		
	}







	
	

	
	
}
 
 

	

